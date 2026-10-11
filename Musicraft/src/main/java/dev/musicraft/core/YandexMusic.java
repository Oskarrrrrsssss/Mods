package dev.musicraft.core;

import com.google.gson.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Unofficial adapter. Authorization is restricted to the fixed API origin.
 * Protocol reference: MarshalX/yandex-music-api, _client/tracks.py, _client/rotor_sessions.py,
 * and utils/sign_request.py (upstream main, consulted 2026-10-12).
 */
public final class YandexMusic {
    private static final String API = "https://api.music.yandex.net";
    private static final String ANDROID_CLIENT = "YandexMusicAndroid/24023621";
    private static final String DESKTOP_CLIENT = "YandexMusicDesktopAppWindows/5.95.0";
    private static final String DESKTOP_SIGN_KEY = "kzqU4XhfCaY6B6JTHODeq5";
    private static final String LYRICS_SIGN_KEY = "p93jhgh689SBReK6ghtw62";
    private static final int API_LIMIT = 8_000_000, AUDIO_LIMIT = 64 * 1024 * 1024;
    private static final DateTimeFormatter ISO_MILLIS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    public static final String STATION = "user:onyourwave";
    private final Supplier<String> token;
    private final Transport transport;
    private final Clock clock;
    private final AtomicLong waveGeneration = new AtomicLong();
    private final Object waveLock = new Object();
    private WaveSession activeWave;
    private final LinkedHashMap<String, String> batchSessions = new LinkedHashMap<>();

    public YandexMusic(Supplier<String> token) {
        this(token, new HttpTransport(), Clock.systemUTC());
    }
    // Injectable transport keeps protocol tests offline without weakening the production URL policy.
    YandexMusic(Supplier<String> token, Transport transport, Clock clock) {
        this.token = Objects.requireNonNull(token);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
    }
    public void checkAccount() throws Exception {
        JsonObject status = object(api("/account/status", null), "аккаунта");
        if (!status.has("account") || !status.get("account").isJsonObject()
            || number(status.getAsJsonObject("account"), "uid", 0) <= 0)
            throw new IOException("Яндекс Музыка не подтвердила аккаунт. Подключи аккаунт снова.");
    }
    public List<Track> search(String query) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        JsonObject result = object(api("/search?type=track&page=0&text=" + enc(query.strip()), null), "поиска");
        if (!result.has("tracks") || !result.get("tracks").isJsonObject()) return List.of();
        List<Track> tracks = new ArrayList<>();
        for (JsonElement item : array(result.getAsJsonObject("tracks"), "results")) {
            if (!item.isJsonObject()) continue;
            Track t = track(item.getAsJsonObject(), ""); if (t != null) tracks.add(t);
        }
        return List.copyOf(tracks);
    }
    /** Starts My Wave on null, otherwise requests the next batch from the current session. */
    public List<Track> wave(String previousId) throws Exception {
        long generation;
        WaveSession session;
        synchronized (waveLock) {
            generation = previousId == null ? waveGeneration.incrementAndGet() : waveGeneration.get();
            if (previousId == null) activeWave = null;
            session = previousId == null ? null : activeWave;
        }
        if (session == null) return newWave(generation, previousId == null ? List.of() : List.of(previousId));
        JsonObject body = new JsonObject(); JsonArray queue = new JsonArray();
        for (String id : session.issued()) queue.add(id);
        if (previousId != null && !session.issued().contains(previousId)) queue.add(previousId);
        body.add("queue", queue);
        JsonObject result = object(api("/rotor/session/" + enc(session.id()) + "/tracks", body), "Моей волны");
        ensureWaveCurrent(generation);
        if (bool(result, "unknownSession", false)) return newWave(generation, session.issued());
        if (bool(result, "terminated", false)) return List.of();
        List<Track> found = sequence(result, previousId);
        commitWave(generation, session.id(), str(result, "batchId", ""), session.issued(), found);
        return found;
    }
    private List<Track> newWave(long generation, List<String> queueIds) throws Exception {
        JsonObject body = new JsonObject(); JsonArray seeds = new JsonArray(); seeds.add(STATION);
        body.add("seeds", seeds); body.addProperty("includeTracksInResponse", true);
        body.addProperty("interactive", true);
        if (!queueIds.isEmpty()) { JsonArray queue = new JsonArray(); queueIds.forEach(queue::add); body.add("queue", queue); }
        JsonObject result = object(api("/rotor/session/new", body), "Моей волны");
        ensureWaveCurrent(generation);
        String id = str(result, "radioSessionId", "");
        if (id.isBlank() || bool(result, "terminated", false))
            throw new IOException("Не удалось создать сессию Моей волны: проверь доступность сервиса и подписку.");
        List<Track> found = sequence(result, null);
        String batch = str(result, "batchId", "");
        commitWave(generation, id, batch, queueIds, found);
        ensureWaveCurrent(generation);
        return found;
    }
    private void commitWave(long generation, String id, String batch, List<String> prior, List<Track> found) throws IOException {
        synchronized (waveLock) {
            ensureWaveCurrent(generation);
            LinkedHashSet<String> issued = new LinkedHashSet<>(prior); found.forEach(t -> issued.add(t.id()));
            while (issued.size() > 100) issued.remove(issued.iterator().next());
            activeWave = new WaveSession(id, batch, List.copyOf(issued));
            if (!batch.isBlank()) batchSessions.put(batch, id);
            while (batchSessions.size() > 128) batchSessions.remove(batchSessions.keySet().iterator().next());
        }
    }
    private void ensureWaveCurrent(long generation) throws IOException {
        if (Thread.currentThread().isInterrupted() || generation != waveGeneration.get())
            throw new InterruptedIOException("Запрос Моей волны отменён.");
    }
    public void resetWave() {
        synchronized (waveLock) { waveGeneration.incrementAndGet(); activeWave = null; batchSessions.clear(); }
    }
    public void feedback(String type, Track track, long playedMillis) throws Exception {
        if (!Set.of("radioStarted", "trackStarted", "trackFinished", "skip").contains(type))
            throw new IOException("Неизвестный тип события Моей волны.");
        String sessionId, batch;
        synchronized (waveLock) {
            if (track != null) {
                batch = track.batchId(); sessionId = batchSessions.get(batch);
            } else {
                sessionId = activeWave == null ? null : activeWave.id(); batch = activeWave == null ? "" : activeWave.batch();
            }
        }
        if (sessionId == null) return; // An old/cancelled queue must not affect a new session.
        sendFeedback(sessionId, batch, type, track, playedMillis);
    }
    private void sendFeedback(String sessionId, String batch, String type, Track track, long playedMillis) throws Exception {
        JsonObject event = new JsonObject(); event.addProperty("type", type);
        event.addProperty("timestamp", ISO_MILLIS.format(clock.instant()));
        if (!type.equals("radioStarted")) {
            if (track == null) throw new IOException("Для события Моей волны нужен трек.");
            event.addProperty("trackId", track.id());
        }
        if (type.equals("trackFinished") || type.equals("skip")) event.addProperty("totalPlayedSeconds", Math.max(0, playedMillis) / 1000.0);
        JsonObject body = new JsonObject(); body.add("event", event);
        if (!batch.isBlank()) body.addProperty("batchId", batch);
        if (type.equals("radioStarted")) body.addProperty("from", "radio-musicraft-user-onyourwave");
        api("/rotor/session/" + enc(sessionId) + "/feedback", body);
    }
    public byte[] audio(Track track) throws Exception {
        String id = track.id(); String ts = Long.toString(clock.instant().getEpochSecond());
        String sign = hmac(DESKTOP_SIGN_KEY, ts + id + "hqmp3raw").replaceFirst("=+$", "");
        JsonObject result;
        try {
            result = object(api("/get-file-info?ts=" + ts + "&trackId=" + enc(id)
                + "&quality=hq&codecs=mp3&transports=raw&sign=" + enc(sign), null, DESKTOP_CLIENT), "аудио");
        } catch (ApiException e) {
            if (!Set.of(404, 405, 501).contains(e.status)) throw e;
            return legacyAudio(track);
        }
        if (!result.has("downloadInfo") || result.get("downloadInfo").isJsonNull()) return legacyAudio(track);
        JsonObject info = object(result.get("downloadInfo"), "аудио");
        if (bool(info, "preview", false)) throw noFullTrack();
        if (!str(info, "codec", "").equals("mp3") || !str(info, "transport", "").equals("raw"))
            throw new IOException("Яндекс Музыка не вернула обычный MP3 для воспроизведения.");
        LinkedHashSet<String> links = new LinkedHashSet<>(); String first = str(info, "url", "");
        if (!first.isBlank()) links.add(first);
        for (JsonElement item : array(info, "urls")) if (item.isJsonPrimitive()) links.add(item.getAsString());
        if (links.isEmpty()) throw noFullTrack();
        IOException last = null;
        for (String link : links.stream().limit(3).toList()) {
            try { return asset(resourceUri(link), AUDIO_LIMIT); }
            catch (InterruptedIOException e) { throw e; }
            catch (IOException e) { last = e; }
        }
        throw last == null ? noFullTrack() : last;
    }
    private byte[] legacyAudio(Track track) throws Exception {
        JsonElement result = api("/tracks/" + enc(track.id()) + "/download-info", null);
        if (!result.isJsonArray()) throw noFullTrack();
        JsonObject best = null; long bitrate = -1;
        for (JsonElement choice : result.getAsJsonArray()) {
            if (!choice.isJsonObject()) continue;
            JsonObject item = choice.getAsJsonObject();
            if (!str(item, "codec", "").equals("mp3") || bool(item, "preview", false)) continue;
            long rate = number(item, "bitrateInKbps", 0);
            if (rate > bitrate) { bitrate = rate; best = item; }
        }
        if (best == null) throw noFullTrack();
        URI direct = resourceUri(str(best, "downloadInfoUrl", ""));
        if (!bool(best, "direct", false)) {
            byte[] xml = asset(direct, 256_000);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException e) throws SAXParseException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
            });
            Document doc;
            try { doc = builder.parse(new ByteArrayInputStream(xml)); }
            catch (org.xml.sax.SAXException e) { throw new IOException("Некорректные данные сервера аудио."); }
            String host = xml(doc, "host"), path = xml(doc, "path"), timestamp = xml(doc, "ts"), salt = xml(doc, "s");
            if (!path.startsWith("/") || !timestamp.matches("[0-9]+")) throw new IOException("Некорректный адрес аудио.");
            String signature = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                .digest(("XGRlBW9FXlekgbPrRHuSiA" + path.substring(1) + salt).getBytes(StandardCharsets.UTF_8)));
            direct = resourceUri("https://" + host + "/get-mp3/" + signature + "/" + timestamp + path);
        }
        return asset(direct, AUDIO_LIMIT);
    }
    private static IOException noFullTrack() { return new IOException("Нет доступного полного MP3: проверь подписку и доступность трека."); }
    public Lyrics lyrics(Track track) throws Exception {
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String id = track.id().split(":")[0];
        String sign = hmac(LYRICS_SIGN_KEY, id + timestamp);
        JsonObject result = object(api("/tracks/" + enc(track.id()) + "/lyrics?format=LRC&timeStamp=" + timestamp + "&sign=" + enc(sign), null), "текста песни");
        String link = str(result, "downloadUrl", "");
        if (link.isBlank()) return Lyrics.EMPTY;
        return Lyrics.parse(new String(asset(resourceUri(link), 2_000_000), StandardCharsets.UTF_8));
    }
    public byte[] cover(Track track) throws Exception {
        if (track.coverUrl().isBlank()) return new byte[0];
        return asset(resourceUri(track.coverUrl()), 8_000_000);
    }
    private JsonElement api(String path, JsonObject body) throws Exception { return api(path, body, ANDROID_CLIENT); }
    private JsonElement api(String path, JsonObject body, String clientName) throws Exception {
        String credential = token.get();
        if (credential == null || credential.isBlank()) throw new IOException("Подключи аккаунт Яндекс Музыки в плеере.");
        if (credential.length() > 4096 || credential.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IOException("Некорректный токен авторизации.");
        URI uri = URI.create(API + path);
        if (!uri.getHost().equals("api.music.yandex.net") || !"https".equals(uri.getScheme()))
            throw new IOException("Недопустимый адрес API.");
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
            .header("Authorization", "OAuth " + credential).header("X-Yandex-Music-Client", clientName)
            .header("Accept", "application/json").header("Accept-Language", "ru").header("User-Agent", "Musicraft/0.1");
        if (body != null) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        else request.GET();
        Response response = exchange(request.build(), API_LIMIT);
        if (response.status() < 200 || response.status() >= 300) throw apiError(response.status());
        JsonObject json;
        try { json = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Некорректный ответ Яндекс Музыки."); }
        if (json.has("error") && !json.get("error").isJsonNull()) throw apiError(errorStatus(json.get("error")));
        // Some modern endpoints return their fields directly rather than in a result envelope.
        if (json.has("result") && !json.get("result").isJsonNull()) return json.get("result");
        return json;
    }
    private static int errorStatus(JsonElement error) {
        String code = error.isJsonPrimitive() ? error.getAsString() : error.isJsonObject() ? str(error.getAsJsonObject(), "name", "") : "";
        String lower = code.toLowerCase(Locale.ROOT);
        if (lower.contains("unauthor") || lower.contains("invalid-token") || lower.contains("expired-token")) return 401;
        if (lower.contains("subscription") || lower.contains("premium") || lower.contains("not-paid")) return 402;
        if (lower.contains("forbidden")) return 403;
        if (lower.contains("not-found")) return 404;
        return 400;
    }
    private static ApiException apiError(int code) {
        String message = switch (code) {
            case 401 -> "Авторизация истекла или токен не принят. Подключи аккаунт снова.";
            case 402 -> "Для полного воспроизведения нужна действующая подписка Яндекс Музыки.";
            case 403 -> "Яндекс Музыка отказала в доступе: проверь аккаунт, подписку и доступность трека в регионе.";
            case 429 -> "Слишком много запросов. Подожди и повтори.";
            case 404 -> "Запрошенный трек или функция Яндекс Музыки недоступны.";
            default -> "Яндекс Музыка: HTTP " + code;
        };
        return new ApiException(code, message);
    }
    public byte[] asset(URI uri, int limit) throws Exception {
        for (int redirect = 0; redirect <= 4; redirect++) {
            validateAssetUri(uri);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).GET().build();
            Response response = exchange(request, limit);
            if (Set.of(301, 302, 303, 307, 308).contains(response.status())) {
                String location = response.headers().firstValue("Location").orElseThrow(() -> new IOException("Нет адреса перенаправления ресурса."));
                try { uri = uri.resolve(location); } catch (IllegalArgumentException e) { throw new IOException("Недопустимый адрес ресурса."); }
                continue;
            }
            if (response.status() != 200) throw new IOException("Не удалось загрузить аудио/обложку/текст: HTTP " + response.status());
            return response.body();
        }
        throw new IOException("Слишком много перенаправлений.");
    }
    private Response exchange(HttpRequest request, int limit) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Response response = transport.exchange(request, limit);
        if (response.body().length > limit) throw new IOException("Ресурс слишком большой.");
        return response;
    }
    static void validateAssetUri(URI uri) throws IOException {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
            || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IOException("Недопустимый адрес ресурса.");
        String h = host.toLowerCase(Locale.ROOT);
        for (String domain : List.of("yandex.net", "yandex.ru", "yandex.com", "yandexmusic.ru"))
            if (h.equals(domain) || h.endsWith("." + domain)) return;
        throw new IOException("Неизвестный сервер ресурса Яндекс Музыки.");
    }
    private static URI resourceUri(String link) throws IOException {
        try {
            if (link.startsWith("//")) link = "https:" + link;
            if (link.startsWith("http://")) link = "https://" + link.substring(7);
            URI uri = URI.create(link); validateAssetUri(uri); return uri;
        } catch (IllegalArgumentException e) { throw new IOException("Недопустимый адрес ресурса."); }
    }
    static byte[] bounded(InputStream stream, int max) throws IOException {
        if (max < 0 || max == Integer.MAX_VALUE) throw new IOException("Недопустимый размер ресурса.");
        byte[] bytes = stream.readNBytes(max + 1);
        if (bytes.length > max) throw new IOException("Ресурс слишком большой.");
        return bytes;
    }
    private static List<Track> sequence(JsonObject result, String previousId) throws IOException {
        String batch = str(result, "batchId", ""); List<Track> tracks = new ArrayList<>(); Set<String> unique = new HashSet<>();
        for (JsonElement item : array(result, "sequence")) {
            if (!item.isJsonObject()) continue;
            JsonElement data = item.getAsJsonObject().get("track");
            if (data == null || !data.isJsonObject()) continue;
            Track t = track(data.getAsJsonObject(), batch);
            if (t != null && !t.id().equals(previousId) && unique.add(t.id())) tracks.add(t);
        }
        if (!tracks.isEmpty() && batch.isBlank()) throw new IOException("Моя волна не вернула идентификатор партии треков.");
        return List.copyOf(tracks);
    }
    private static Track track(JsonObject data, String batch) {
        if (!bool(data, "available", true) && !bool(data, "availableForPremiumUsers", false)) return null;
        String id = str(data, "id", ""); if (id.isBlank()) return null;
        List<String> artists = new ArrayList<>();
        for (JsonElement a : array(data, "artists")) if (a.isJsonObject()) artists.add(str(a.getAsJsonObject(), "name", ""));
        String cover = str(data, "coverUri", "").replace("%%", "256x256");
        if (!cover.isBlank() && !cover.startsWith("https://") && !cover.startsWith("http://")) cover = "https://" + cover;
        return new Track(id, str(data, "title", "Без названия"), String.join(", ", artists),
            Math.max(0, number(data, "durationMs", 0)), null, null, cover, batch);
    }
    private static JsonArray array(JsonObject object, String key) { JsonElement value = object.get(key); return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray(); }
    private static JsonObject object(JsonElement element, String context) throws IOException { if (element == null || !element.isJsonObject()) throw new IOException("Неожиданный ответ Яндекс Музыки: " + context + "."); return element.getAsJsonObject(); }
    private static long number(JsonObject o, String key, long fallback) { try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : fallback; } catch (RuntimeException e) { return fallback; } }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String str(JsonObject o, String key, String fallback) { try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback; } catch (RuntimeException e) { return fallback; } }
    private static boolean bool(JsonObject o, String key, boolean fallback) { try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : fallback; } catch (RuntimeException e) { return fallback; } }
    private static String hmac(String key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
    private static String xml(Document doc, String tag) throws IOException {
        var nodes = doc.getElementsByTagName(tag); if (nodes.getLength() != 1) throw new IOException("Неожиданный ответ сервера аудио.");
        return nodes.item(0).getTextContent();
    }
    private record WaveSession(String id, String batch, List<String> issued) { }
    static final class ApiException extends IOException {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }
    @FunctionalInterface interface Transport { Response exchange(HttpRequest request, int maximumBytes) throws IOException, InterruptedException; }
    record Response(int status, HttpHeaders headers, byte[] body) { }
    private static final class HttpTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        @Override public Response exchange(HttpRequest request, int maximumBytes) throws IOException, InterruptedException {
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, info -> new LimitedBody(maximumBytes));
            try {
                HttpResponse<byte[]> response = future.get(request.timeout().orElse(Duration.ofSeconds(45)).toMillis(), TimeUnit.MILLISECONDS);
                return new Response(response.statusCode(), response.headers(), response.body());
            } catch (InterruptedException e) { future.cancel(true); throw e; }
            catch (TimeoutException e) { future.cancel(true); throw new IOException("Яндекс Музыка не ответила вовремя. Повтори попытку."); }
            catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io && "Ресурс слишком большой.".equals(io.getMessage())) throw io;
                throw new IOException("Не удалось соединиться с Яндекс Музыкой. Проверь подключение к Интернету.");
            }
        }
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int max) { this.max = max; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > max - output.size()) {
                    subscription.cancel(); body.completeExceptionally(new IOException("Ресурс слишком большой.")); return;
                }
                byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); output.writeBytes(bytes);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(output.toByteArray()); }
    }
}
