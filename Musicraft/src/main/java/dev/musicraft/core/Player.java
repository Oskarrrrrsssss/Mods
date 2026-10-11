package dev.musicraft.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class Player implements AutoCloseable {
    /** Small injectable boundaries let playback races be tested without an account or sound device. */
    interface RemoteProvider {
        void checkAccount(String credential) throws Exception;
        List<Track> search(String query) throws Exception;
        List<Track> wave(String previousId) throws Exception;
        void feedback(String type, Track track, long playedMillis) throws Exception;
        byte[] audio(Track track) throws Exception;
        Lyrics lyrics(Track track) throws Exception;
        byte[] cover(Track track) throws Exception;
        default void resetWave() {}
    }
    public interface Playback extends AutoCloseable {
        void play(AudioEngine.Input input, long seekMillis, boolean paused, Runnable ready,
                  Runnable ended, Consumer<Exception> failed);
        boolean playing();
        boolean loaded();
        long positionMillis();
        long durationMillis();
        void togglePause();
        void volume(float value);
        void stop();
        @Override void close();
    }
    public final Settings settings;
    public final LocalLibrary local;
    private final Path configPath;
    private final TokenStore credentials;
    private final Playback audio;
    // Playback, metadata, searches and authorization never wait behind a stale network request.
    private final ExecutorService workers = Executors.newCachedThreadPool(daemonFactory("musicraft-services"));
    private final ExecutorService storage = Executors.newSingleThreadExecutor(daemonFactory("musicraft-storage"));
    private final ExecutorService feedback = Executors.newSingleThreadExecutor(daemonFactory("musicraft-feedback"));
    private final RemoteProvider remote;
    private final Object authLock = new Object();
    private volatile String token = "";
    private volatile List<Track> items = List.of();
    private List<Track> queue = List.of();
    private int queueIndex = -1;
    private volatile Track current;
    private volatile Lyrics lyrics = Lyrics.EMPTY;
    private volatile byte[] cover = new byte[0];
    private volatile boolean loading, wave, closed, connecting;
    private volatile String status = "Локальная библиотека", error = "";
    private volatile String accountStatus = "Аккаунт не подключён", accountError = "";
    private final Deque<Track> history = new ArrayDeque<>();
    private byte[] currentAudio;
    private boolean currentStarted;
    private long serial, listSerial, authSerial, waveSerial;
    private Future<?> playbackJob, metadataJob, listJob, authJob;

    public Player(Path gameDir) throws IOException { this(gameDir, null, new AudioEngine()); }
    Player(Path gameDir, RemoteProvider provider, Playback audio) throws IOException {
        this.audio = audio;
        configPath = gameDir.resolve("config/musicraft.json");
        settings = Settings.load(configPath); audio.volume(settings.volume);
        local = new LocalLibrary(gameDir.resolve("music"));
        credentials = new TokenStore(gameDir.resolve("config/musicraft-auth.dat"));
        YandexMusic yandex = provider == null ? new YandexMusic(() -> token) : null;
        remote = provider != null ? provider : new RemoteProvider() {
            public void checkAccount(String value) throws Exception { new YandexMusic(() -> value).checkAccount(); }
            public List<Track> search(String query) throws Exception { return yandex.search(query); }
            public List<Track> wave(String previous) throws Exception { return yandex.wave(previous); }
            public void feedback(String type, Track track, long played) throws Exception { yandex.feedback(type, track, played); }
            public byte[] audio(Track track) throws Exception { return yandex.audio(track); }
            public Lyrics lyrics(Track track) throws Exception { return yandex.lyrics(track); }
            public byte[] cover(Track track) throws Exception { return yandex.cover(track); }
            public void resetWave() { yandex.resetWave(); }
        };
        synchronized (authLock) {
            long request = ++authSerial;
            authJob = workers.submit(() -> {
                try {
                    String saved = credentials.load();
                    if (saved.isBlank()) return;
                    synchronized (authLock) {
                        if (!validAuth(request)) return;
                        connecting = true; accountStatus = "Проверка сохранённого аккаунта…";
                    }
                    remote.checkAccount(saved);
                    synchronized (authLock) {
                        if (!validAuth(request)) return;
                        token = saved; accountError = ""; accountStatus = "Яндекс Музыка подключена";
                    }
                } catch (Exception e) {
                    synchronized (authLock) {
                        if (validAuth(request)) { accountError = safeError(e, ""); accountStatus = "Подключи аккаунт заново"; }
                    }
                } finally {
                    synchronized (authLock) { if (validAuth(request)) connecting = false; }
                }
            });
        }
        refreshLocal();
    }
    private static ThreadFactory daemonFactory(String name) {
        return runnable -> { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; };
    }
    public List<Track> items() { return items; }
    public Track current() { return current; }
    public Lyrics lyrics() { return lyrics; }
    public byte[] coverBytes() { return cover; }
    public boolean playing() { return audio.playing(); }
    public boolean loading() { return loading; }
    public boolean connected() { return !token.isBlank(); }
    public boolean connecting() { return connecting; }
    public String accountStatus() { return accountStatus; }
    public String accountError() { return accountError; }
    public String status() { return status; }
    public String error() { return error; }
    public long positionMillis() { return audio.positionMillis(); }
    public long durationMillis() {
        Track track = current; return track != null && track.durationMillis() > 0 ? track.durationMillis() : audio.durationMillis();
    }
    public void saveSettings() {
        audio.volume(settings.volume);
        submitStorage(() -> { try { settings.save(configPath); } catch (IOException e) { error = "Не удалось сохранить настройки."; } });
    }
    public synchronized void refreshLocal() {
        if (closed) return;
        long request = ++listSerial; cancel(listJob);
        listJob = workers.submit(() -> {
            try {
                List<Track> found = local.scan();
                synchronized (this) {
                    if (!validList(request)) return;
                    items = found; status = "Локальные треки: " + found.size(); error = "";
                }
            } catch (Exception e) { synchronized (this) { if (validList(request)) error = "Не удалось прочитать папку music."; } }
        });
    }
    public synchronized void search(String query) {
        if (closed || query == null || query.isBlank()) return;
        long request = ++listSerial; cancel(listJob);
        status = "Поиск в Яндекс Музыке…";
        listJob = workers.submit(() -> {
            try {
                List<Track> found = remote.search(query.strip());
                synchronized (this) {
                    if (!validList(request)) return;
                    items = List.copyOf(found); status = "Яндекс Музыка: " + found.size(); error = "";
                }
            } catch (Exception e) { synchronized (this) { if (validList(request)) fail(e); } }
        });
    }
    public void connect(String candidate) {
        String clean = candidate == null ? "" : candidate.strip().replaceFirst("(?i)^OAuth\\s+", "");
        if (clean.isBlank() || clean.length() > 4096 || clean.chars().anyMatch(c -> c < 33 || c > 126)) {
            accountError = error = "Некорректный токен авторизации."; return;
        }
        synchronized (authLock) {
            if (closed) return;
            long request = ++authSerial; cancel(authJob);
            connecting = true; accountError = ""; accountStatus = "Проверка аккаунта…";
            authJob = workers.submit(() -> {
                try {
                    remote.checkAccount(clean);
                    synchronized (authLock) {
                        if (!validAuth(request)) return;
                        credentials.save(clean); token = clean; remote.resetWave();
                        accountError = ""; accountStatus = "Яндекс Музыка подключена";
                    }
                } catch (Exception e) {
                    synchronized (authLock) { if (validAuth(request)) accountError = safeError(e, clean); }
                } finally {
                    synchronized (authLock) { if (validAuth(request)) connecting = false; }
                }
            });
        }
    }
    public void disconnect() {
        synchronized (authLock) {
            if (closed) return;
            long request = ++authSerial; cancel(authJob); token = ""; connecting = false;
            remote.resetWave(); accountError = ""; accountStatus = "Аккаунт отключён";
            submitStorage(() -> {
                synchronized (authLock) {
                    if (!validAuth(request)) return;
                    try { credentials.clear(); }
                    catch (IOException e) { accountError = "Не удалось удалить сохранённую авторизацию."; }
                }
            });
        }
        synchronized (this) { if (wave || current != null && current.remote()) stop(); }
    }
    public synchronized void choose(List<Track> selectedQueue, int index) {
        if (closed || index < 0 || index >= selectedQueue.size()) return;
        queue = List.copyOf(selectedQueue); queueIndex = index; wave = false; ++waveSerial;
        requestStartLocked(queue.get(index), true);
    }
    private void requestStartLocked(Track track, boolean remember) {
        if (remember && currentStarted && current != null && !current.id().equals(track.id())) {
            history.push(current); while (history.size() > 100) history.removeLast();
        }
        long request = invalidateLocked();
        current = track; lyrics = Lyrics.EMPTY; cover = new byte[0]; currentAudio = null; currentStarted = false;
        error = ""; status = "Загрузка: " + track.title();
        long waveRequest = waveSerial;
        playbackJob = workers.submit(() -> {
            try {
                byte[] data = track.remote() ? remote.audio(track) : null;
                synchronized (this) {
                    if (!valid(request)) return;
                    currentAudio = data;
                    startAudioLocked(track, data, request, 0, false, waveRequest, true);
                }
                loadMetadata(track, request);
            } catch (Exception e) { synchronized (this) { if (valid(request)) { loading = false; fail(e); } } }
        });
    }
    private void startAudioLocked(Track track, byte[] data, long request, long target, boolean paused,
                                  long waveRequest, boolean sendStarted) {
        audio.play(() -> track.remote() ? new ByteArrayInputStream(data) : Files.newInputStream(track.file()),
            target, paused, () -> {
                synchronized (this) {
                    if (!valid(request)) return;
                    currentStarted = true; loading = false;
                    status = wave ? "Моя волна" : track.remote() ? "Яндекс Музыка" : "Локальная музыка";
                    if (wave && sendStarted) submitFeedback("trackStarted", track, 0, waveRequest);
                }
            }, () -> advance(true, request), e -> {
                synchronized (this) { if (valid(request)) { loading = false; fail(e); } }
            });
    }
    private void loadMetadata(Track track, long request) {
        synchronized (this) {
            if (!valid(request)) return;
            metadataJob = workers.submit(() -> {
                try {
                    Lyrics found = track.remote() ? remote.lyrics(track) : local.lyrics(track);
                    synchronized (this) { if (valid(request)) lyrics = found; }
                } catch (Exception ignored) { /* Lyrics are optional. */ }
                synchronized (this) { if (!valid(request)) return; }
                try {
                    byte[] image = track.remote() ? remote.cover(track) : track.coverFile() == null ? new byte[0] : readCover(track.coverFile());
                    synchronized (this) { if (valid(request)) cover = image; }
                } catch (Exception ignored) { /* Cover art is optional. */ }
            });
        }
    }
    private static byte[] readCover(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) { return YandexMusic.bounded(in, 8_000_000); }
    }
    public synchronized void startWave() {
        if (closed) return;
        long request = invalidateLocked(); wave = true; ++waveSerial;
        long listRequest = ++listSerial; cancel(listJob);
        current = null; currentStarted = false; currentAudio = null;
        lyrics = Lyrics.EMPTY; cover = new byte[0]; queue = List.of(); queueIndex = -1;
        status = "Запуск Моей волны…"; error = "";
        playbackJob = workers.submit(() -> {
            try {
                // The provider creates the radio session and sends radioStarted after receiving its id.
                List<Track> found = remote.wave(null);
                synchronized (this) {
                    if (!valid(request)) return;
                    if (found.isEmpty()) throw new IOException("Моя волна не вернула доступных треков.");
                    queue = List.copyOf(found); queueIndex = 0;
                    if (listRequest == listSerial) items = queue;
                    requestStartLocked(queue.getFirst(), true);
                }
            } catch (Exception e) { synchronized (this) { if (valid(request)) { loading = false; fail(e); } } }
        });
    }
    public synchronized void next() { if (!closed && (current != null || !queue.isEmpty())) advance(false, serial); }
    private synchronized void advance(boolean finished, long expectedRequest) {
        if (!valid(expectedRequest)) return;
        Track previous = current; long played = audio.positionMillis();
        if (wave && previous != null && currentStarted) submitFeedback(finished ? "trackFinished" : "skip", previous, played, waveSerial);
        if (!wave) {
            if (queue.isEmpty()) { invalidateLocked(); loading = false; return; }
            queueIndex = (queueIndex + 1) % queue.size();
            requestStartLocked(queue.get(queueIndex), true);
        } else if (queueIndex + 1 < queue.size()) {
            ++queueIndex; requestStartLocked(queue.get(queueIndex), true);
        } else {
            long request = invalidateLocked(); currentStarted = false;
            status = "Следующий трек Моей волны…";
            playbackJob = workers.submit(() -> {
                try {
                    List<Track> found = remote.wave(previous == null ? null : previous.id());
                    synchronized (this) {
                        if (!valid(request)) return;
                        if (found.isEmpty()) throw new IOException("Моя волна не вернула следующий трек.");
                        queue = List.copyOf(found); queueIndex = 0; items = queue;
                        requestStartLocked(queue.getFirst(), true);
                    }
                } catch (Exception e) { synchronized (this) { if (valid(request)) { loading = false; fail(e); } } }
            });
        }
    }
    public synchronized void previous() {
        if (closed) return;
        Track track;
        if (!wave && queue.size() > 1) {
            queueIndex = (queueIndex - 1 + queue.size()) % queue.size(); track = queue.get(queueIndex);
        } else {
            track = history.poll();
            if (track != null) { wave = false; ++waveSerial; queue = List.of(track); queueIndex = 0; }
        }
        if (track != null) requestStartLocked(track, false); else seek(0);
    }
    public synchronized void togglePause() {
        if (closed || loading) return;
        if (current == null) { if (!items.isEmpty()) choose(items, 0); }
        else if (audio.loaded()) audio.togglePause();
        else requestStartLocked(current, false);
    }
    public synchronized void seek(long millis) {
        Track track = current; byte[] data = currentAudio;
        if (closed || track == null || loading || !audio.loaded() || track.remote() && data == null) return;
        long limit = durationMillis();
        long target = Math.max(0, limit > 0 ? Math.min(millis, Math.max(0, limit - 100)) : millis);
        boolean paused = !audio.playing();
        long request = invalidateLocked();
        startAudioLocked(track, data, request, target, paused, waveSerial, false);
        loadMetadata(track, request);
    }
    public synchronized void stop() {
        if (closed) return;
        invalidateLocked(); loading = false; current = null; currentStarted = false;
        lyrics = Lyrics.EMPTY; cover = new byte[0]; currentAudio = null; wave = false; ++waveSerial;
        queue = List.of(); queueIndex = -1; status = "Воспроизведение остановлено";
    }
    private long invalidateLocked() {
        ++serial; cancel(playbackJob); cancel(metadataJob); playbackJob = metadataJob = null;
        audio.stop(); loading = true; return serial;
    }
    private boolean valid(long request) { return !closed && serial == request; }
    private boolean validList(long request) { return !closed && listSerial == request; }
    private boolean validAuth(long request) { return !closed && authSerial == request; }
    private void submitFeedback(String type, Track track, long played, long waveRequest) {
        try {
            feedback.execute(() -> {
                synchronized (this) { if (closed || waveRequest != waveSerial) return; }
                try { remote.feedback(type, track, played); } catch (Exception ignored) {}
            });
        } catch (RejectedExecutionException ignored) {}
    }
    private void submitStorage(Runnable task) {
        if (closed) return;
        try { storage.execute(() -> { if (!closed) task.run(); }); } catch (RejectedExecutionException ignored) {}
    }
    private static void cancel(Future<?> job) { if (job != null) job.cancel(true); }
    private void fail(Exception e) { error = safeError(e, token); }
    private static String safeError(Exception e, String secret) {
        // Never publish response bodies, request URLs or authorization strings to the HUD.
        String message = e instanceof IOException ? e.getMessage() : "Не удалось выполнить действие (" + e.getClass().getSimpleName() + ").";
        if (message == null || message.contains("http:") || message.contains("https:") || !secret.isBlank() && message.contains(secret))
            return "Ошибка подключения или воспроизведения.";
        return message;
    }
    @Override public void close() {
        synchronized (this) { if (closed) return; closed = true; ++serial; ++waveSerial; audio.close(); }
        synchronized (authLock) { ++authSerial; token = ""; connecting = false; }
        workers.shutdownNow(); feedback.shutdownNow(); storage.shutdownNow();
    }
}
