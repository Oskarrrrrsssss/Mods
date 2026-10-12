package dev.invumusicplayer.core;

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
        default void waveSettings(String mood, String diversity, String language) throws Exception {}
        default void rate(Track track, boolean like, boolean remove) throws Exception {}
        default List<Track> liked() throws Exception { return List.of(); }
        default List<YandexMusic.Playlist> playlists() throws Exception { return List.of(); }
        default List<Track> playlist(String owner, String kind) throws Exception { return List.of(); }
        default List<Track> playlistUuid(String uuid) throws Exception { return List.of(); }
    }
    public interface Playback extends AutoCloseable {
        void play(AudioEngine.Input input, long seekMillis, boolean paused, Runnable ready,
                  Runnable ended, Consumer<Exception> failed);
        boolean playing();
        boolean loaded();
        long positionMillis();
        default long playedMillis() { return positionMillis(); }
        long durationMillis();
        void togglePause();
        void volume(float value);
        void stop();
        @Override void close();
    }
    public final Settings settings;
    public final LocalLibrary local;
    private final Path configPath;
    private final SessionStore session;
    private final Path gameDirectory;
    private final Deque<Track> upNext = new ArrayDeque<>();
    private final Map<String, Integer> ratings = new ConcurrentHashMap<>();
    private final Map<String, List<Track>> savedLists = new ConcurrentHashMap<>();
    private volatile List<YandexMusic.Playlist> playlists = List.of();
    private final Map<String, byte[]> covers = new ConcurrentHashMap<>();
    private final Set<String> pendingCovers = ConcurrentHashMap.newKeySet();
    private volatile Messages.Text lyricStatus = Messages.EMPTY;
    private final TokenStore credentials;
    private final Playback audio;
    // Playback, metadata, searches and authorization never wait behind a stale network request.
    private final ExecutorService workers = Executors.newCachedThreadPool(daemonFactory("invumusicplayer-services"));
    private final ExecutorService storage = Executors.newSingleThreadExecutor(daemonFactory("invumusicplayer-storage"));
    private final ExecutorService feedback = Executors.newSingleThreadExecutor(daemonFactory("invumusicplayer-feedback"));
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
    private volatile Messages.Text status = Messages.text("invumusicplayer.status.local_library"), error = Messages.EMPTY;
    private volatile Messages.Text accountStatus = Messages.text("invumusicplayer.account.disconnected"), accountError = Messages.EMPTY;
    private final Deque<Track> history = new ArrayDeque<>();
    private byte[] currentAudio;
    private boolean currentStarted;
    private long listenedMillis;
    private long serial, listSerial, authSerial, waveSerial, waveListSerial;
    private Future<?> playbackJob, metadataJob, listJob, authJob;

    public Player(Path gameDir) throws IOException { this(migrate(gameDir), null, new AudioEngine()); }
    Player(Path gameDir, RemoteProvider provider, Playback audio) throws IOException {
        this(gameDir, provider, audio, new TokenStore(gameDir.resolve("config/invumusicplayer-auth.dat")));
    }
    Player(Path gameDir, RemoteProvider provider, Playback audio, TokenStore credentials) throws IOException {
        this.audio = audio; gameDirectory = gameDir;
        configPath = gameDir.resolve("config/invumusicplayer.json");
        session = new SessionStore(gameDir.resolve("config/invumusicplayer-session.json"));
        SessionStore.State savedState = session.load();
        queue = savedState.queue(); queueIndex = savedState.index(); wave = savedState.wave();
        items = queue; upNext.addAll(savedState.pending()); ratings.putAll(savedState.ratings()); savedLists.putAll(savedState.playlists());
        if (queueIndex >= 0 && queueIndex < queue.size()) current = queue.get(queueIndex);
        settings = Settings.load(configPath); audio.volume(settings.volume);
        local = new LocalLibrary(gameDir.resolve("music"));
        this.credentials = credentials;
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
            public void waveSettings(String m, String d, String l) throws Exception { yandex.waveSettings(m, d, l); }
            public void rate(Track t, boolean like, boolean remove) throws Exception { yandex.rate(t, like, remove); }
            public List<Track> liked() throws Exception { return yandex.liked(); }
            public List<YandexMusic.Playlist> playlists() throws Exception { return yandex.playlists(); }
            public List<Track> playlist(String o, String k) throws Exception { return yandex.playlist(o, k); }
            public List<Track> playlistUuid(String uuid) throws Exception { return yandex.playlistUuid(uuid); }
        };
        synchronized (authLock) {
            long request = ++authSerial;
            authJob = workers.submit(() -> {
                String saved = "";
                try {
                    saved = credentials.load();
                    if (saved.isBlank()) return;
                    synchronized (authLock) {
                        if (!validAuth(request)) return;
                        connecting = true; accountStatus = Messages.text("invumusicplayer.account.check_saved");
                    }
                    remote.checkAccount(saved);
                    synchronized (authLock) {
                        if (!validAuth(request)) return;
                        token = saved; accountError = Messages.EMPTY; accountStatus = Messages.text("invumusicplayer.account.connected");
                    }
                } catch (Exception e) {
                    synchronized (authLock) {
                        if (validAuth(request)) { accountError = safeError(e, saved); accountStatus = Messages.text("invumusicplayer.account.reconnect"); }
                    }
                } finally {
                    synchronized (authLock) { if (validAuth(request)) connecting = false; }
                }
            });
        }
        if (items.isEmpty()) refreshLocal();
    }
    private static Path migrate(Path gameDir) throws IOException {
        Path config=gameDir.resolve("config");
        for(String suffix:new String[]{".json","-auth.dat","-session.json"}){
            Path old=config.resolve("musicraft"+suffix), current=config.resolve("invumusicplayer"+suffix);
            if(!Files.exists(current)&&Files.exists(old)){Files.createDirectories(config);Files.copy(old,current);}
        }
        return gameDir;
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
    public String accountStatus() { return accountStatus.getString(); }
    public String accountError() { return accountError.getString(); }
    public String status() { return status.getString(); }
    public String error() { return error.getString(); }
    public long positionMillis() { return audio.positionMillis(); }
    public long durationMillis() {
        Track track = current; return track != null && track.durationMillis() > 0 ? track.durationMillis() : audio.durationMillis();
    }
    public void saveSettings() {
        audio.volume(settings.volume);
        submitStorage(() -> { try { settings.save(configPath); } catch (IOException e) { error = Messages.text("invumusicplayer.error.settings_save"); } });
    }
    public synchronized void refreshLocal() {
        if (closed) return;
        long request = ++listSerial; cancel(listJob);
        listJob = workers.submit(() -> {
            try {
                List<Track> found = local.scan();
                synchronized (this) {
                    if (!validList(request)) return;
                    items = found; status = Messages.text("invumusicplayer.status.local_count", found.size()); error = Messages.EMPTY;
                }
            } catch (Exception e) { synchronized (this) { if (validList(request)) error = Messages.text("invumusicplayer.error.library_read"); } }
        });
    }
    public synchronized void search(String query) {
        if (closed || query == null || query.isBlank()) return;
        long request = ++listSerial; cancel(listJob);
        status = Messages.text("invumusicplayer.status.searching");
        listJob = workers.submit(() -> {
            try {
                List<Track> found = remote.search(query.strip());
                synchronized (this) {
                    if (!validList(request)) return;
                    items = List.copyOf(found); status = Messages.text("invumusicplayer.status.search_count", found.size()); error = Messages.EMPTY;
                }
            } catch (Exception e) { synchronized (this) { if (validList(request)) fail(e); } }
        });
    }
    public void connect(String candidate) {
        final String clean;
        try { clean = TokenInput.normalize(candidate); }
        catch (IOException e) { accountError = error = Messages.text("invumusicplayer.error.token_invalid"); return; }
        synchronized (authLock) {
            if (closed) return;
            long request = ++authSerial; cancel(authJob);
            connecting = true; accountError = Messages.EMPTY; accountStatus = Messages.text("invumusicplayer.account.checking");
            authJob = workers.submit(() -> {
                try {
                    remote.checkAccount(clean);
                    synchronized (authLock) {
                        if (!validAuth(request)) return;
                        credentials.save(clean); token = clean; remote.resetWave();
                        accountError = Messages.EMPTY; accountStatus = Messages.text("invumusicplayer.account.connected");
                    }
                    synchronized (this) { if (wave || current != null && current.remote()) stop(); }
                } catch (Exception e) {
                    synchronized (authLock) {
                        if (validAuth(request)) {
                            accountError = safeError(e, clean);
                            accountStatus = connected() ? Messages.text("invumusicplayer.account.connected") : Messages.text("invumusicplayer.account.failed");
                        }
                    }
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
            remote.resetWave(); accountError = Messages.EMPTY; accountStatus = Messages.text("invumusicplayer.account.signed_out");
            submitStorage(() -> {
                synchronized (authLock) {
                    if (!validAuth(request)) return;
                    try { credentials.clear(); }
                    catch (IOException e) { accountError = Messages.text("invumusicplayer.error.auth_delete"); }
                }
            });
        }
        synchronized (this) { if (wave || current != null && current.remote()) stop(); }
    }
    public synchronized void choose(List<Track> selectedQueue, int index) {
        if (closed || index < 0 || index >= selectedQueue.size()) return;
        queue = List.copyOf(selectedQueue); queueIndex = index;
        wave = wave && !queue.get(index).batchId().isBlank(); ++waveSerial; upNext.remove(queue.get(index));
        persist();
        requestStartLocked(queue.get(index), true);
    }
    private void requestStartLocked(Track track, boolean remember) {
        if (remember && currentStarted && current != null && !current.id().equals(track.id())) {
            history.push(current); while (history.size() > 100) history.removeLast();
        }
        long request = invalidateLocked();
        current = track; lyrics = Lyrics.EMPTY; lyricStatus = Messages.text("invumusicplayer.lyrics.loading"); cover = new byte[0]; currentAudio = null; currentStarted = false;
        listenedMillis = 0;
        error = Messages.EMPTY; status = Messages.text("invumusicplayer.status.loading_track", track.title());
        long waveRequest = waveSerial;
        playbackJob = workers.submit(() -> {
            try {
                byte[] data = track.youtube() ? YouTubeAudio.audio(track, gameDirectory) : track.remote() ? remote.audio(track) : null;
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
                    status = wave ? Messages.text("invumusicplayer.status.wave") : track.remote() ? Messages.text("invumusicplayer.status.yandex") : Messages.text("invumusicplayer.status.local_playing");
                    if (wave && sendStarted) submitFeedback("trackStarted", track, 0, waveRequest);
                }
            }, () -> advance(true, request), e -> {
                synchronized (this) { if (valid(request)) { loading = false; fail(e); } }
            });
    }
    private void loadMetadata(Track track, long request) {
        if (track.youtube()) { lyricStatus = Messages.text("invumusicplayer.lyrics.none"); return; }
        workers.submit(() -> {
            try {
                Lyrics found = track.remote() ? remote.lyrics(track) : local.lyrics(track);
                synchronized (this) { if (valid(request)) { lyrics = found; lyricStatus = Messages.text(found.lines().isEmpty() ? "invumusicplayer.lyrics.none" : "invumusicplayer.lyrics.ready"); } }
            } catch (Exception e) { synchronized (this) { if (valid(request)) lyricStatus = safeError(e, token); } }
        });
        workers.submit(() -> {
            try {
                byte[] image = track.remote() ? remote.cover(track) : track.coverFile() == null ? new byte[0] : readCover(track.coverFile());
                synchronized (this) { if (valid(request)) { cover = image; cacheCover(track.id(), image); } }
            } catch (Exception ignored) { /* An absent cover never blocks audio or lyrics. */ }
        });
    }
    private void cacheCover(String id, byte[] bytes) { if (covers.size() >= 96) covers.clear(); covers.put(id, bytes); }
    public byte[] coverFor(Track track) {
        if (track.youtube()) return new byte[0];
        byte[] cached = covers.get(track.id()); if (cached != null) return cached;
        if (pendingCovers.size() < 16 && pendingCovers.add(track.id())) workers.submit(() -> {
            try { cacheCover(track.id(), track.remote() ? remote.cover(track) : track.coverFile() == null ? new byte[0] : readCover(track.coverFile())); }
            catch (Exception ignored) { cacheCover(track.id(), new byte[0]); }
            finally { pendingCovers.remove(track.id()); }
        });
        return new byte[0];
    }
    private static byte[] readCover(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) { return YandexMusic.bounded(in, 8_000_000); }
    }
    public synchronized void startWave() {
        if (closed) return;
        long request = invalidateLocked(); wave = true; ++waveSerial;
        long listRequest = ++listSerial; cancel(listJob);
        waveListSerial = listRequest;
        current = null; currentStarted = false; currentAudio = null;
        lyrics = Lyrics.EMPTY; cover = new byte[0]; queue = List.of(); queueIndex = -1;
        status = Messages.text("invumusicplayer.status.wave_starting"); error = Messages.EMPTY;
        playbackJob = workers.submit(() -> {
            try {
                // Only successful session creation can emit radioStarted; feedback does not delay audio.
                remote.waveSettings(settings.waveMood, settings.waveDiversity, settings.waveLanguage);
                List<Track> found = remote.wave(null);
                synchronized (this) {
                    if (!valid(request)) return;
                    if (found.isEmpty()) throw Messages.error("invumusicplayer.error.wave_empty");
                    queue = List.copyOf(found); queueIndex = 0;
                    if (listRequest == listSerial) items = queue;
                    submitFeedback("radioStarted", null, 0, waveSerial);
                    requestStartLocked(queue.getFirst(), true); persist();
                }
            } catch (Exception e) { synchronized (this) { if (valid(request)) { loading = false; fail(e); } } }
        });
    }
    public synchronized void next() { if (!closed && (current != null || !queue.isEmpty())) advance(false, serial); }
    private synchronized void advance(boolean finished, long expectedRequest) {
        if (!valid(expectedRequest)) return;
        Track previous = current; long played = listenedMillis + audio.playedMillis();
        if (wave && previous != null && currentStarted) submitFeedback(finished ? "trackFinished" : "skip", previous, played, waveSerial);
        if (!upNext.isEmpty()) { Track explicit = upNext.removeFirst(); requestStartLocked(explicit, true); persist(); return; }
        if (!wave) {
            if (queue.isEmpty()) { invalidateLocked(); loading = false; return; }
            if (queueIndex + 1 >= queue.size() && !settings.repeat) { suspend(); return; }
            queueIndex = (queueIndex + 1) % queue.size();
            requestStartLocked(queue.get(queueIndex), true);
        } else if (queueIndex + 1 < queue.size()) {
            ++queueIndex; requestStartLocked(queue.get(queueIndex), true);
        } else {
            if (currentStarted && previous != null) {
                history.push(previous); while (history.size() > 100) history.removeLast();
            }
            long request = invalidateLocked(); currentStarted = false;
            status = Messages.text("invumusicplayer.status.wave_next");
            playbackJob = workers.submit(() -> {
                try {
                    List<Track> found = remote.wave(previous == null ? null : previous.id());
                    synchronized (this) {
                        if (!valid(request)) return;
                        if (found.isEmpty()) throw Messages.error("invumusicplayer.error.wave_next_empty");
                        queue = List.copyOf(found); queueIndex = 0;
                        if (waveListSerial == listSerial) items = queue;
                        requestStartLocked(queue.getFirst(), true); persist();
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
        if (current == null) { if (!upNext.isEmpty()) { Track first=upNext.removeFirst(); queue=List.of(first);queueIndex=0;wave=false;requestStartLocked(first,true);persist(); } else if (!items.isEmpty()) choose(items, 0); }
        else if (audio.loaded()) audio.togglePause();
        else if (wave && current.batchId().isBlank()) startWave(); else requestStartLocked(current, false);
    }
    public synchronized void seek(long millis) {
        Track track = current; byte[] data = currentAudio;
        if (closed || track == null || loading || !audio.loaded() || track.remote() && data == null) return;
        long limit = durationMillis();
        long target = Math.max(0, limit > 0 ? Math.min(millis, Math.max(0, limit - 100)) : millis);
        boolean paused = !audio.playing();
        listenedMillis += audio.playedMillis();
        long request = invalidateLocked();
        startAudioLocked(track, data, request, target, paused, waveSerial, false);
        loadMetadata(track, request);
    }
    public String lyricStatus() { return lyricStatus.getString(); }
    public float[] spectrum() { return audio instanceof AudioEngine engine && playing() ? engine.spectrum() : new float[24]; }
    public synchronized boolean isWave() { return wave; }
    public synchronized List<Track> queue() { List<Track> list = new ArrayList<>(upNext); if (queueIndex + 1 < queue.size()) list.addAll(queue.subList(queueIndex + 1, queue.size())); return List.copyOf(list); }
    public synchronized void enqueue(Track track) { upNext.addLast(track); persist(); }
    public synchronized void removeQueued(int index) {
        if(index<0)return;
        if(index<upNext.size()){List<Track> copy=new ArrayList<>(upNext);copy.remove(index);upNext.clear();upNext.addAll(copy);}
        else{int actual=queueIndex+1+index-upNext.size();if(actual>=queue.size())return;List<Track> copy=new ArrayList<>(queue);copy.remove(actual);queue=List.copyOf(copy);}
        persist();
    }
    public synchronized void clearQueue() { upNext.clear(); queue = current == null ? List.of() : List.of(current); queueIndex = current == null ? -1 : 0; persist(); }
    public void volume(float value) { settings.volume = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : settings.volume; audio.volume(settings.volume); }
    public synchronized void suspend() { invalidateLocked(); loading = false; currentStarted = false; persist(); }
    private synchronized void persist() {
        SessionStore.State snapshot = new SessionStore.State(queue, queueIndex, wave, List.copyOf(upNext), Map.copyOf(ratings), Map.copyOf(savedLists));
        submitStorage(() -> { try { session.save(snapshot); } catch (IOException e) { error = Messages.text("invumusicplayer.error.session_save"); } });
    }
    public int rating() { return current == null ? 0 : ratings.getOrDefault(current.id(), 0); }
    public void rate(boolean like) {
        Track track = current; if (track == null || track.youtube()) return;
        int value = like ? 1 : -1;
        feedback.execute(() -> { boolean remove=ratings.getOrDefault(track.id(),0)==value; try { if (track.remote()) remote.rate(track, like, remove); ratings.put(track.id(), remove ? 0 : value); persist(); if (!like && !remove && track.equals(current)) next(); }
            catch (Exception e) { fail(e); } });
    }
    public void showLiked() { browse(() -> { List<Track> result = new ArrayList<>(); if (connected()) result.addAll(remote.liked()); for (Track t : local.scan()) if (ratings.getOrDefault(t.id(), 0) == 1) result.add(t); return result; }); }
    @FunctionalInterface private interface Listing { List<Track> get() throws Exception; }
    private synchronized void browse(Listing list) {
        long request = ++listSerial; cancel(listJob); listJob = workers.submit(() -> { try { List<Track> found = List.copyOf(list.get()); synchronized(this) { if (validList(request)) items = found; } } catch (Exception e) { if (validList(request)) fail(e); } });
    }
    public List<YandexMusic.Playlist> playlists() { return playlists; }
    public void loadPlaylists() { workers.submit(() -> { try { playlists = remote.playlists(); } catch (Exception e) { fail(e); } }); }
    public void openPlaylist(String owner, String kind) { browse(() -> remote.playlist(owner, kind)); }
    public void importLink(String link) {
        workers.submit(() -> { try {
            java.net.URI uri = java.net.URI.create(link.strip()); String host = uri.getHost();
            if (host == null || !"https".equals(uri.getScheme())) throw Messages.error("invumusicplayer.error.playlist_link");
            if (host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com")) { Track t = YouTubeAudio.describe(link, gameDirectory); synchronized(this) { items = List.of(t); enqueue(t); } return; }
            if (!host.equals("music.yandex.ru") && !host.equals("music.yandex.com") && !host.equals("music.yandex.kz")) throw Messages.error("invumusicplayer.error.playlist_link");
            java.util.regex.Matcher match = java.util.regex.Pattern.compile("/users/([^/]+)/playlists/(\\d+)").matcher(uri.getPath());
            if (match.find()) openPlaylist(match.group(1), match.group(2));
            else { java.util.regex.Matcher modern=java.util.regex.Pattern.compile("/playlists/([a-zA-Z0-9-]{8,80})").matcher(uri.getPath());
                if(!modern.find())throw Messages.error("invumusicplayer.error.playlist_link");String uuid=modern.group(1);browse(()->remote.playlistUuid(uuid)); }
        } catch (Exception e) { fail(e); } });
    }
    public Set<String> savedPlaylists() { return Set.copyOf(savedLists.keySet()); }
    public synchronized void savePlaylist(String title) { String name = title.strip(); if (name.isEmpty() || name.length() > 80) return; List<Track> saved = new ArrayList<>(); if (current != null) saved.add(current); saved.addAll(queue()); savedLists.put(name, List.copyOf(saved)); persist(); }
    public void openSavedPlaylist(String name) { browse(() -> savedLists.getOrDefault(name, List.of())); }
    public synchronized void stop() {
        if (closed) return;
        invalidateLocked(); loading = false; current = null; currentStarted = false;
        lyrics = Lyrics.EMPTY; cover = new byte[0]; currentAudio = null; wave = false; ++waveSerial;
        queue = List.of(); queueIndex = -1; upNext.clear(); persist(); status = Messages.text("invumusicplayer.status.stopped");
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
        try { storage.execute(() -> { task.run(); }); } catch (RejectedExecutionException ignored) {}
    }
    private static void cancel(Future<?> job) { if (job != null) job.cancel(true); }
    private void fail(Exception e) { error = safeError(e, token); }
    private static Messages.Text safeError(Exception e, String secret) {
        // Only our bounded, keyed messages may reach the HUD. Native I/O exceptions can
        // contain a local username, file path, request URL or account credential.
        if (e instanceof Messages.Failure failure) {
            String message = failure.getMessage();
            if (!message.contains("http:") && !message.contains("https:")
                && (secret.isBlank() || !message.contains(secret))) return failure.text();
        }
        if (e instanceof javax.sound.sampled.UnsupportedAudioFileException)
            return Messages.text("invumusicplayer.error.audio_format");
        if (e instanceof javax.sound.sampled.LineUnavailableException)
            return Messages.text("invumusicplayer.error.audio_device");
        return Messages.text("invumusicplayer.error.playback");
    }
    @Override public void close() {
        synchronized (this) { if (closed) return; persist(); saveSettings(); closed = true; ++serial; ++waveSerial; audio.close(); }
        synchronized (authLock) { ++authSerial; token = ""; connecting = false; }
        workers.shutdownNow(); feedback.shutdownNow(); storage.shutdown();
        try { if (!storage.awaitTermination(3, TimeUnit.SECONDS)) storage.shutdownNow(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
