package dev.invumusicplayer.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;

/** Offline HTTP protocol fixtures: no live credentials, music, or account are required. */
final class YandexMusicProtocolTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1668687184), ZoneOffset.UTC);
    private static final String TOKEN = "test-credential-not-a-real-account";
    private static Track remote(String id) { return new Track(id, "Title", "Artist", 100_000, null, null, "", ""); }
    private static YandexMusic.Response response(int status, String body) {
        return new YandexMusic.Response(status, HttpHeaders.of(Map.of(), (a, b) -> true), body.getBytes(StandardCharsets.UTF_8));
    }
    private static YandexMusic.Response json(String result) { return response(200, "{\"result\":" + result + "}"); }
    private static String body(HttpRequest request) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>(); ByteArrayOutputStream output = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] b = new byte[buffer.remaining()]; buffer.get(b); output.writeBytes(b); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(output.toString(StandardCharsets.UTF_8)); }
        });
        return result.get(1, TimeUnit.SECONDS);
    }
    private static JsonObject posted(HttpRequest request) throws Exception { return JsonParser.parseString(body(request)).getAsJsonObject(); }
    private static Map<String, String> query(HttpRequest request) {
        Map<String, String> values = new HashMap<>();
        for (String pair : request.uri().getRawQuery().split("&")) {
            String[] entry = pair.split("=", 2);
            values.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8), URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
        }
        return values;
    }
    private static String fileInfo(String transport, String codec, boolean preview, String url) {
        return "{\"downloadInfo\":{\"trackId\":\"4784420:100500\",\"quality\":\"hq\",\"codec\":\"" + codec
            + "\",\"transport\":\"" + transport + "\",\"preview\":" + preview + ",\"url\":\"" + url + "\"}}";
    }
    private static String sequence(String session, String batch, String... ids) {
        JsonObject result = new JsonObject(); if (session != null) result.addProperty("radioSessionId", session);
        result.addProperty("batchId", batch); JsonArray sequence = new JsonArray();
        for (String id : ids) { JsonObject data = new JsonObject(); data.addProperty("id", id); data.addProperty("title", "Track " + id);
            data.addProperty("durationMs", 100_000); JsonObject item = new JsonObject(); item.add("track", data); sequence.add(item); }
        result.add("sequence", sequence); return result.toString();
    }
    private static final class Script implements YandexMusic.Transport {
        final Deque<YandexMusic.Response> responses = new ArrayDeque<>(); final List<HttpRequest> requests = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        Script(YandexMusic.Response... responses) { this.responses.addAll(Arrays.asList(responses)); }
        @Override public YandexMusic.Response exchange(HttpRequest request, int max) {
            requests.add(request); limits.add(max); assertFalse(responses.isEmpty(), "Unexpected HTTP request: " + request.uri().getPath());
            return responses.removeFirst();
        }
        YandexMusic client() { return new YandexMusic(() -> TOKEN, this, CLOCK); }
    }
    @Test void desktopFileInfoSignatureClientAndFullAudioMatchUpstream() throws Exception {
        Script script = new Script(json(fileInfo("raw", "mp3", false, "https://strm.yandex.net/song")), response(200, "audio"));
        assertArrayEquals("audio".getBytes(StandardCharsets.UTF_8), script.client().audio(remote("4784420:100500")));
        HttpRequest api = script.requests.getFirst(); assertEquals("/get-file-info", api.uri().getPath());
        assertEquals(Map.of("ts", "1668687184", "trackId", "4784420:100500", "quality", "hq", "codecs", "mp3",
            "transports", "raw", "sign", "a746V+fc/+shkNujDVgTfQ1QHHSWzMdiAOtQxEs3Q+M"), query(api));
        assertEquals("YandexMusicDesktopAppWindows/5.95.0", api.headers().firstValue("X-Yandex-Music-Client").orElseThrow());
        assertEquals("OAuth " + TOKEN, api.headers().firstValue("Authorization").orElseThrow());
        assertTrue(script.requests.get(1).headers().firstValue("Authorization").isEmpty());
        assertEquals(64 * 1024 * 1024, script.limits.get(1));
    }
    @Test void modernFileInfoCanBeReturnedWithoutResultEnvelope() throws Exception {
        Script script = new Script(response(200, fileInfo("raw", "mp3", false, "https://strm.yandex.net/song")), response(200, "audio"));
        assertEquals(5, script.client().audio(remote("4784420")).length);
    }
    @Test void audioTriesAlternateStorageHostWithoutAuthorization() throws Exception {
        Script script = new Script(json("{\"downloadInfo\":{\"codec\":\"mp3\",\"transport\":\"raw\",\"url\":\"https://strm.yandex.net/a\",\"urls\":[\"https://strm2.yandex.net/a\"]}}"), response(503, ""), response(200, "ok"));
        assertEquals(2, script.client().audio(remote("1")).length);
        for (HttpRequest request : script.requests.subList(1, 3)) assertTrue(request.headers().firstValue("Authorization").isEmpty());
    }
    @Test void entitlementAndAuthenticationErrorsNeverFallBackOrExposeServerText() {
        for (int code : List.of(401, 402, 403, 429)) {
            Script script = new Script(response(code, "{\"errorDescription\":\"" + TOKEN + " https://secret.test\"}"));
            IOException error = assertThrows(IOException.class, () -> script.client().audio(remote("1")));
            assertFalse(error.getMessage().contains(TOKEN)); assertFalse(error.getMessage().contains("https://"));
            assertEquals(1, script.requests.size());
            if (code == 402) assertTrue(error.getMessage().contains("подписка"));
        }
    }
    @Test void subscriptionErrorInsideSuccessfulHttpResponseIsStillAnError() {
        Script script = new Script(response(200, "{\"error\":{\"name\":\"subscription-required\",\"message\":\"" + TOKEN + "\"}}"));
        IOException error = assertThrows(IOException.class, () -> script.client().audio(remote("1")));
        assertTrue(error.getMessage().contains("подписка")); assertFalse(error.getMessage().contains(TOKEN)); assertEquals(1, script.requests.size());
    }
    @Test void previewOrUnsupportedTransportIsNeverDownloaded() {
        for (String info : List.of(fileInfo("raw", "mp3", true, "https://strm.yandex.net/a"),
            fileInfo("encraw", "mp3", false, "https://strm.yandex.net/a"), fileInfo("raw", "aac", false, "https://strm.yandex.net/a"))) {
            Script script = new Script(json(info)); assertThrows(IOException.class, () -> script.client().audio(remote("1")));
            assertEquals(1, script.requests.size());
        }
    }
    @Test void legacyFallbackSelectsHighestFullMp3AndSignsSecureXml() throws Exception {
        Script script = new Script(response(404, ""), json("[{\"codec\":\"mp3\",\"preview\":true,\"bitrateInKbps\":320,\"downloadInfoUrl\":\"https://storage.yandex.net/preview\"},"
            + "{\"codec\":\"mp3\",\"preview\":false,\"bitrateInKbps\":128,\"downloadInfoUrl\":\"https://storage.yandex.net/low\"},"
            + "{\"codec\":\"mp3\",\"preview\":false,\"bitrateInKbps\":192,\"downloadInfoUrl\":\"http://storage.yandex.net/info\"}]"),
            response(200, "<download-info><host>strm.yandex.net</host><path>/sample.mp3</path><ts>42</ts><s>salt</s></download-info>"), response(200, "audio"));
        assertEquals(5, script.client().audio(remote("1")).length);
        assertEquals("https://storage.yandex.net/info", script.requests.get(2).uri().toString());
        assertEquals("/get-mp3/1fa0b7c2cf4f1ff408319807f65ca780/42/sample.mp3", script.requests.get(3).uri().getPath());
        for (HttpRequest request : script.requests.subList(2, 4)) assertTrue(request.headers().firstValue("Authorization").isEmpty());
    }
    @Test void legacyPreviewOnlyResponseReportsFullTrackUnavailable() {
        Script script = new Script(response(404, ""), json("[{\"codec\":\"mp3\",\"preview\":true,\"bitrateInKbps\":320}]"));
        IOException error = assertThrows(IOException.class, () -> script.client().audio(remote("1")));
        assertTrue(error.getMessage().contains("полного MP3")); assertEquals(2, script.requests.size());
    }
    @Test void legacyXmlCannotReadExternalEntities() {
        Script script = new Script(response(404, ""), json("[{\"codec\":\"mp3\",\"downloadInfoUrl\":\"https://storage.yandex.net/info\"}]"),
            response(200, "<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><download-info><host>&secret;</host></download-info>"));
        assertThrows(IOException.class, () -> script.client().audio(remote("1"))); assertEquals(3, script.requests.size());
    }
    @Test void myWaveCreatesSessionAndConsumesSessionBatchesWithIssuedTrackQueue() throws Exception {
        Script script = new Script(json(sequence("session-one", "batch-one", "1", "2", "2")), json(sequence(null, "batch-two", "2", "3")), json("{}"));
        YandexMusic client = script.client(); List<Track> first = client.wave(null); assertEquals(List.of("1", "2"), first.stream().map(Track::id).toList());
        assertEquals("/rotor/session/new", script.requests.getFirst().uri().getPath());
        JsonObject create = posted(script.requests.getFirst()); assertEquals("user:onyourwave", create.getAsJsonArray("seeds").get(0).getAsString());
        assertTrue(create.get("includeTracksInResponse").getAsBoolean()); assertEquals("POST", script.requests.getFirst().method());
        assertEquals(List.of("3"), client.wave("2").stream().map(Track::id).toList());
        assertEquals("/rotor/session/session-one/tracks", script.requests.get(1).uri().getPath());
        assertEquals("[\"1\",\"2\"]", posted(script.requests.get(1)).getAsJsonArray("queue").toString());
        client.feedback("skip", first.getFirst(), 2450);
        JsonObject feedback = posted(script.requests.get(2)); assertEquals("batch-one", feedback.get("batchId").getAsString());
        assertEquals("skip", feedback.getAsJsonObject("event").get("type").getAsString());
        assertEquals(2.45, feedback.getAsJsonObject("event").get("totalPlayedSeconds").getAsDouble());
        assertEquals("2022-11-17T12:13:04.000Z", feedback.getAsJsonObject("event").get("timestamp").getAsString());
    }
    @Test void oldBatchFeedbackUsesItsOriginalSessionAfterNewWave() throws Exception {
        Script script = new Script(json(sequence("one", "batch-one", "1")), json(sequence("two", "batch-two", "2")), json("{}"), json("{}"));
        YandexMusic client = script.client(); Track old = client.wave(null).getFirst(); client.wave(null);
        client.feedback("trackFinished", old, 100_000); client.feedback("radioStarted", null, 0);
        assertEquals("/rotor/session/one/feedback", script.requests.get(2).uri().getPath());
        assertEquals("batch-one", posted(script.requests.get(2)).get("batchId").getAsString());
        assertEquals("/rotor/session/two/feedback", script.requests.get(3).uri().getPath());
        JsonObject radio = posted(script.requests.get(3)); assertEquals("batch-two", radio.get("batchId").getAsString());
        assertFalse(radio.getAsJsonObject("event").has("trackId"));
        assertFalse(radio.getAsJsonObject("event").has("totalPlayedSeconds"));
    }
    @Test void unknownWaveSessionIsRenewedWithKnownQueue() throws Exception {
        Script script = new Script(json(sequence("one", "b1", "1", "2")), json("{\"unknownSession\":true}"), json(sequence("renewed", "b2", "3")));
        YandexMusic client = script.client(); client.wave(null); assertEquals("3", client.wave("2").getFirst().id());
        assertEquals("/rotor/session/new", script.requests.get(2).uri().getPath());
        assertEquals("[\"1\",\"2\"]", posted(script.requests.get(2)).getAsJsonArray("queue").toString());
    }
    @Test void canceledOldWaveCannotReplaceANewerSession() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1), release = new CountDownLatch(1); AtomicInteger calls = new AtomicInteger();
        List<HttpRequest> requests = Collections.synchronizedList(new ArrayList<>());
        YandexMusic client = new YandexMusic(() -> TOKEN, (request, max) -> {
            requests.add(request); int call = calls.incrementAndGet();
            if (call == 1) { firstEntered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return json(sequence("stale", "stale-batch", "1")); }
            if (call == 2) return json(sequence("current", "current-batch", "2"));
            return json(sequence(null, "next", "3"));
        }, CLOCK);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<List<Track>> stale = worker.submit(() -> client.wave(null)); assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
            assertEquals("2", client.wave(null).getFirst().id()); release.countDown();
            ExecutionException error = assertThrows(ExecutionException.class, () -> stale.get(2, TimeUnit.SECONDS)); assertInstanceOf(InterruptedIOException.class, error.getCause());
            client.wave("2"); assertEquals("/rotor/session/current/tracks", requests.get(2).uri().getPath());
        } finally { release.countDown(); worker.shutdownNow(); }
    }
    @Test void resetWaveClearsPendingFeedbackRouting() throws Exception {
        Script script = new Script(json(sequence("one", "b1", "1"))); YandexMusic client = script.client(); Track old = client.wave(null).getFirst();
        client.resetWave(); client.feedback("skip", old, 1); assertEquals(1, script.requests.size());
    }
    @Test void verifiedAccountNeedsPositiveUid() throws Exception {
        new Script(json("{\"account\":{\"uid\":123}}" )).client().checkAccount();
        for (String result : List.of("{}", "{\"account\":{\"uid\":0}}", "{\"account\":null}"))
            assertThrows(IOException.class, () -> new Script(json(result)).client().checkAccount());
    }
    @Test void searchRetainsPremiumTracksAndOmitsUnavailableOrMalformedEntries() throws Exception {
        Script script = new Script(json("{\"tracks\":{\"results\":[{\"id\":\"1\",\"available\":false},"
            + "{\"id\":\"2\",\"available\":false,\"availableForPremiumUsers\":true,\"title\":\"Song\",\"artists\":[{\"name\":\"Artist\"}],\"coverUri\":\"avatars.yandex.net/a/%%\"},{\"title\":\"no id\"}]}}"));
        List<Track> result = script.client().search("test / text"); assertEquals(1, result.size());
        assertEquals("Artist", result.getFirst().artist()); assertEquals("https://avatars.yandex.net/a/200x200", result.getFirst().coverUrl());
        assertEquals("test / text", query(script.requests.getFirst()).get("text"));
    }
    @Test void assetRedirectKeepsAuthorizationAbsentAndRejectsOtherOrigins() throws Exception {
        YandexMusic.Response redirect = new YandexMusic.Response(302, HttpHeaders.of(Map.of("Location", List.of("/new")), (a,b) -> true), new byte[0]);
        Script allowed = new Script(redirect, response(200, "ok")); assertEquals(2, allowed.client().asset(URI.create("https://storage.yandex.net/old"), 50).length);
        for (HttpRequest request : allowed.requests) assertTrue(request.headers().firstValue("Authorization").isEmpty());
        assertEquals("/new", allowed.requests.get(1).uri().getPath());
        Script unsafe = new Script(new YandexMusic.Response(302, HttpHeaders.of(Map.of("Location", List.of("https://attacker.test/a")), (a,b) -> true), new byte[0]));
        assertThrows(IOException.class, () -> unsafe.client().asset(URI.create("https://storage.yandex.net/old"), 50)); assertEquals(1, unsafe.requests.size());
    }
    @Test void apiRedirectIsNotFollowedWithToken() {
        Script script = new Script(new YandexMusic.Response(302, HttpHeaders.of(Map.of("Location", List.of("https://attacker.test/a")), (a,b) -> true), new byte[0]));
        assertThrows(IOException.class, () -> script.client().checkAccount()); assertEquals(1, script.requests.size());
    }
    @Test void malformedApiJsonIsReportedWithoutEchoingContents() {
        Script script = new Script(response(200, "[\"" + TOKEN + "\"]")); IOException error = assertThrows(IOException.class, () -> script.client().checkAccount());
        assertFalse(error.getMessage().contains(TOKEN));
    }
    @Test void sizeBoundAppliesToMockedAndRealBodySubscribers() throws Exception {
        Script script = new Script(response(200, "12345")); assertThrows(IOException.class, () -> script.client().asset(URI.create("https://storage.yandex.net/a"), 4));
        AtomicBoolean canceled = new AtomicBoolean(); YandexMusic.LimitedBody body = new YandexMusic.LimitedBody(4);
        body.onSubscribe(new Flow.Subscription() { public void request(long n) {} public void cancel() { canceled.set(true); } });
        body.onNext(List.of(ByteBuffer.wrap(new byte[3]), ByteBuffer.wrap(new byte[2]))); assertTrue(canceled.get());
        assertThrows(ExecutionException.class, () -> body.getBody().toCompletableFuture().get());
        YandexMusic.LimitedBody exact = new YandexMusic.LimitedBody(4);
        exact.onSubscribe(new Flow.Subscription() { public void request(long n) {} public void cancel() {} });
        exact.onNext(List.of(ByteBuffer.wrap(new byte[4]))); exact.onComplete(); assertEquals(4, exact.getBody().toCompletableFuture().get().length);
    }

    @Test void artworkFallsBackToAlbumAndFullTrackMetadata() throws Exception {
        Script script=new Script(json("[{\"id\":\"1\",\"albums\":[{\"coverUri\":\"avatars.yandex.net/get-music/%%\"}]}]"),response(200,"image"));
        assertEquals("image",new String(script.client().cover(remote("1")),StandardCharsets.UTF_8));
        assertEquals("/tracks",script.requests.getFirst().uri().getPath());
        assertEquals("https://avatars.yandex.net/get-music/200x200",script.requests.getLast().uri().toString());
        assertTrue(script.requests.getLast().headers().firstValue("Authorization").isEmpty());
    }
    @Test void likesUseAccountUidAndFormBodyWhileWaveSettingsUseJson() throws Exception {
        Script script=new Script(json("{\"account\":{\"uid\":42}}"),json("{\"revision\":2}"),json("\"ok\""));
        YandexMusic client=script.client();client.rate(remote("1"),true,false);client.waveSettings("sad","discover","any");
        assertEquals("/users/42/likes/tracks/add-multiple",script.requests.get(1).uri().getPath());
        assertTrue(Arrays.asList(body(script.requests.get(1)).split("&")).contains("track-ids=1"));
        assertEquals("sad",posted(script.requests.get(2)).get("moodEnergy").getAsString());
        assertEquals("discover",posted(script.requests.get(2)).get("diversity").getAsString());
    }
    @Test void legacy256ArtworkUsesSupported200PresetAndNoCredential() throws Exception {
        Script script=new Script(response(200,"jpeg"));
        Track old=new Track("1","t","a",100,null,null,"https://avatars.yandex.net/get-music/album/256x256","");
        assertEquals(4,script.client().cover(old).length);
        assertEquals("https://avatars.yandex.net/get-music/album/200x200",script.requests.getFirst().uri().toString());
        assertTrue(script.requests.getFirst().headers().firstValue("Authorization").isEmpty());
    }
    @Test void staleCoverRefreshesMetadataAndNullApiErrorIsAccepted() throws Exception {
        Script script=new Script(response(404,""),response(200,"{\"error\":null,\"result\":[{\"id\":\"1\",\"albums\":[{\"coverUri\":\"avatars.yandex.net/new/%%\"}]}]}"),response(200,"jpeg"));
        Track old=new Track("1","t","a",100,null,null,"https://avatars.yandex.net/old/200x200","");
        assertEquals(4,script.client().cover(old).length);
        assertEquals("https://avatars.yandex.net/new/200x200",script.requests.getLast().uri().toString());
        assertTrue(Arrays.asList(body(script.requests.get(1)).split("&")).contains("track-ids=1"));
    }
}
