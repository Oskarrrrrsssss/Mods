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
        byte[] cover(Track track) throws Exception;
        default void resetWave() {}
        default void waveSettings(String mood, String diversity, String language) throws Exception {}
        default void rate(Track track, boolean like, boolean remove) throws Exception {}
        default List<Track> liked() throws Exception { return List.of(); }
        default List<YandexMusic.Playlist> playlists() throws Exception { return List.of(); }
        default List<Track> playlist(String owner, String kind) throws Exception { return List.of(); }
        default List<Track> playlistUuid(String uuid) throws Exception { return List.of(); }
        default void deletePlaylist(String owner,String kind) throws Exception {}
    }
    public interface Playback extends AutoCloseable {
        void play(AudioEngine.Input input, long seekMillis, boolean paused, Runnable ready,
                  Runnable ended, Consumer<Exception> failed);
        boolean playing();
        boolean loaded();
        long positionMillis();
        default long playedMillis() { return positionMillis(); }
        long durationMillis();
        default float[] spectrum() { return new float[24]; }
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
    private final Map<String, byte[]> covers = Collections.synchronizedMap(new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, byte[]> entry) { return size() > 128; }
    });
    private final Set<String> pendingCovers = ConcurrentHashMap.newKeySet();
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
    private List<Track> queue = List.of(), originalQueue = List.of();
    private volatile List<Track> likedCache = List.of();
    private volatile String listingSection="local";
    public String listingSection(){return listingSection;}
    private volatile boolean showingLiked;
    private int queueIndex = -1;
    private volatile Track current;
    private static final byte[] EMPTY_IMAGE = new byte[0];
    private volatile byte[] cover = EMPTY_IMAGE;
    private volatile Messages.Text coverStatus = Messages.EMPTY;
    private final Map<String, Long> coverRetryAfter = new ConcurrentHashMap<>();
    private volatile boolean loading, wave, closed, connecting;
    private volatile Messages.Text status = Messages.text("invumusicplayer.status.local_library"), error = Messages.EMPTY;
    private volatile Messages.Text accountStatus = Messages.text("invumusicplayer.account.disconnected"), accountError = Messages.EMPTY;
    private final Deque<Track> history = new ArrayDeque<>(), forward = new ArrayDeque<>();
    private byte[] currentAudio;
    private boolean currentStarted;
    private long listenedMillis;
    private long serial, listSerial, authSerial, waveSerial, waveListSerial;
    private Future<?> playbackJob, coverJob, listJob, authJob;

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
        originalQueue = savedState.originalQueue().isEmpty()?queue:savedState.originalQueue();
        items = queue; upNext.addAll(savedState.pending()); ratings.putAll(savedState.ratings()); savedLists.putAll(savedState.playlists());
        current = savedState.current();
        if (current == null && queueIndex >= 0 && queueIndex < queue.size()) current = queue.get(queueIndex);
        listingSection=wave?"wave":queue.stream().anyMatch(Track::remote)?"tracks":"local";
        if(wave)publishWaveList();
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
            public byte[] cover(Track track) throws Exception { return yandex.cover(track); }
            public void resetWave() { yandex.resetWave(); }
            public void waveSettings(String m, String d, String l) throws Exception { yandex.waveSettings(m, d, l); }
            public void rate(Track t, boolean like, boolean remove) throws Exception { yandex.rate(t, like, remove); }
            public List<Track> liked() throws Exception { return yandex.liked(); }
            public List<YandexMusic.Playlist> playlists() throws Exception { return yandex.playlists(); }
            public List<Track> playlist(String o, String k) throws Exception { return yandex.playlist(o, k); }
            public List<Track> playlistUuid(String uuid) throws Exception { return yandex.playlistUuid(uuid); }
            public void deletePlaylist(String owner,String kind) throws Exception { yandex.deletePlaylist(owner,kind); }
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
        if (closed) return;showingLiked=false;listingSection="local";
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
        showingLiked=false;listingSection="search";
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
        forward.clear();
        originalQueue = List.copyOf(selectedQueue); queueIndex = index;
        if(settings.shuffle) {
            List<Track> shuffled=new ArrayList<>(selectedQueue);Track chosen=shuffled.remove(index);Collections.shuffle(shuffled);shuffled.addFirst(chosen);
            queue=List.copyOf(shuffled);queueIndex=0;
        } else queue=originalQueue;
        wave = wave && !queue.get(queueIndex).batchId().isBlank(); ++waveSerial; upNext.remove(queue.get(queueIndex));
        persist();
        requestStartLocked(queue.get(queueIndex), true);
    }
    private void requestStartLocked(Track track, boolean remember) {
        if (remember && current != null && !current.id().equals(track.id())) remember(history,current);
        long request = invalidateLocked();
        current = track; publishWaveList();cover = covers.getOrDefault(track.id(), EMPTY_IMAGE); currentAudio = null; currentStarted = false;
        coverStatus = Messages.EMPTY;
        persist();
        listenedMillis = 0;
        error = Messages.EMPTY; status = Messages.text("invumusicplayer.status.loading_track", track.title());
        long waveRequest = waveSerial;
        loadMetadata(track, request);
        playbackJob = workers.submit(() -> {
            try {
                byte[] data = track.youtube() ? YouTubeAudio.audio(track, gameDirectory) : track.remote() ? remote.audio(track) : null;
                synchronized (this) {
                    if (!valid(request)) return;
                    currentAudio = data;
                    startAudioLocked(track, data, request, 0, false, waveRequest, true);
                }
            } catch (Exception e) { synchronized (this) { if (valid(request)) { loading = false; fail(e); } } }
        });
    }
    private static void remember(Deque<Track> list,Track track){list.push(track);while(list.size()>100)list.removeLast();}
    private void startAudioLocked(Track track, byte[] data, long request, long target, boolean paused,
                                  long waveRequest, boolean sendStarted) {
        audio.play(() -> track.remote() ? new ByteArrayInputStream(data) : Files.newInputStream(track.file()),
            target, paused, () -> {
                synchronized (this) {
                    if (!valid(request)) return;
                    currentStarted = true; loading = false;
                    status = track.youtube() ? Messages.text("invumusicplayer.status.youtube") : wave ? Messages.text("invumusicplayer.status.wave") : track.remote() ? Messages.text("invumusicplayer.status.yandex") : Messages.text("invumusicplayer.status.local_playing");
                    if (wave && sendStarted && !track.batchId().isBlank()) submitFeedback("trackStarted", track, 0, waveRequest);
                }
            }, () -> advance(true, request), e -> {
                synchronized (this) { if (valid(request)) { loading = false; fail(e); } }
            });
    }
    private synchronized void loadMetadata(Track track, long request) {
        if (!valid(request) || track.youtube()) return;
        coverStatus = Messages.text("invumusicplayer.cover.loading");
        coverJob = workers.submit(() -> {
            try {
                byte[] image = artwork(track);
                synchronized (this) {
                    if (valid(request)) { cover = image; coverStatus = Messages.text(image.length == 0 ? "invumusicplayer.cover.none" : "invumusicplayer.cover.ready"); }
                }
            } catch (Exception e) {
                synchronized (this) { if (valid(request)) coverStatus = Messages.text("invumusicplayer.cover.failed", safeError(e, token)); }
                logArtworkFailure(e);
            }
        });
    }
    private byte[] artwork(Track track) throws Exception {
        byte[] cached = covers.get(track.id()); if (cached != null) return cached;
        byte[] image = track.remote() ? remote.cover(track) : track.coverFile() == null ? EMPTY_IMAGE : readCover(track.coverFile());
        cacheCover(track.id(), image); return image;
    }
    private static void logArtworkFailure(Exception e) {
        System.getLogger("invumusicplayer").log(System.Logger.Level.WARNING,
            "Artwork unavailable / Обложка недоступна: " + (e instanceof Messages.Failure f ? f.text().key() : e.getClass().getSimpleName()));
    }
    public String coverStatus() { return coverStatus.getString(); }
    public synchronized void retryArtwork() {
        if (closed || current == null || current.youtube()) return;
        covers.remove(current.id()); coverRetryAfter.remove(current.id()); cancel(coverJob); loadMetadata(current, serial);
    }
    private void cacheCover(String id, byte[] bytes) { covers.put(id, bytes); coverRetryAfter.remove(id); }
    public byte[] coverFor(Track track) {
        if (closed || track.youtube()) return EMPTY_IMAGE;
        byte[] cached = covers.get(track.id()); if (cached != null) return cached;
        if (System.nanoTime() < coverRetryAfter.getOrDefault(track.id(), 0L)) return EMPTY_IMAGE;
        if (pendingCovers.size() < 16 && pendingCovers.add(track.id())) workers.submit(() -> {
            try { artwork(track); }
            catch (Exception e) { coverRetryAfter.put(track.id(), System.nanoTime() + TimeUnit.SECONDS.toNanos(30)); logArtworkFailure(e); }
            finally { pendingCovers.remove(track.id()); }
        });
        return EMPTY_IMAGE;
    }
    private static byte[] readCover(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) { return YandexMusic.bounded(in, 8_000_000); }
    }
    public synchronized void startWave() {
        if (closed) return;showingLiked=false;listingSection="wave";
        if(current!=null)remember(history,current);forward.clear();
        long request = invalidateLocked(); wave = true; ++waveSerial;
        long listRequest = ++listSerial; cancel(listJob);
        waveListSerial = listRequest;
        current = null; currentStarted = false; currentAudio = null; cover = EMPTY_IMAGE; queue = List.of(); originalQueue=queue;queueIndex = -1;
        status = Messages.text("invumusicplayer.status.wave_starting"); error = Messages.EMPTY;
        playbackJob = workers.submit(() -> {
            try {
                // Only successful session creation can emit radioStarted; feedback does not delay audio.
                if(settings.waveCustom)remote.waveSettings(settings.waveMood, settings.waveDiversity, settings.waveLanguage);
                List<Track> found = remote.wave(null);
                synchronized (this) {
                    if (!valid(request)) return;
                    if (found.isEmpty()) throw Messages.error("invumusicplayer.error.wave_empty");
                    queue = List.copyOf(found); originalQueue=queue;queueIndex = 0;reorderUpcoming(settings.shuffle);
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
        if (wave && previous != null && currentStarted && !previous.batchId().isBlank()) submitFeedback(finished ? "trackFinished" : "skip", previous, played, waveSerial);
        if (!forward.isEmpty()) { requestStartLocked(forward.removeFirst(),true);persist();return; }
        if (!upNext.isEmpty()) { Track explicit = upNext.removeFirst(); requestStartLocked(explicit, true); persist(); return; }
        if (!wave) {
            if (queue.isEmpty()) { invalidateLocked(); loading = false; return; }
            if (queueIndex + 1 >= queue.size() && !settings.repeat) { suspend(); return; }
            if(queueIndex+1>=queue.size()&&settings.shuffle){
                List<Track> nextCycle=new ArrayList<>(originalQueue);Collections.shuffle(nextCycle);
                if(nextCycle.size()>1&&nextCycle.getFirst().equals(current))for(int i=1;i<nextCycle.size();i++)if(!nextCycle.get(i).equals(current)){Collections.swap(nextCycle,0,i);break;}
                queue=List.copyOf(nextCycle);queueIndex=0;
            }else queueIndex = (queueIndex + 1) % queue.size();
            requestStartLocked(queue.get(queueIndex), true);
        } else if (queueIndex + 1 < queue.size()) {
            ++queueIndex; requestStartLocked(queue.get(queueIndex), true);
        } else {
            long request = invalidateLocked(); currentStarted = false;
            status = Messages.text("invumusicplayer.status.wave_next");
            String seed = queue.isEmpty() ? null : queue.getLast().id();
            playbackJob = workers.submit(() -> {
                try {
                    List<Track> found = remote.wave(seed);
                    synchronized (this) {
                        if (!valid(request)) return;
                        if (found.isEmpty()) throw Messages.error("invumusicplayer.error.wave_next_empty");
                        queue = List.copyOf(found); originalQueue=queue;queueIndex = 0;reorderUpcoming(settings.shuffle);
                        if (waveListSerial == listSerial) items = queue;
                        requestStartLocked(queue.getFirst(), true); persist();
                    }
                } catch (Exception e) { synchronized (this) { if (valid(request)) { loading = false; fail(e); } } }
            });
        }
    }
    public synchronized void previous() {
        if (closed || current==null) return;
        Track track=history.pollFirst();
        // Old saved sessions have no history. Use the current position, never wrap to the last song.
        if(track==null){int position=queueIndex>=0&&queueIndex<queue.size()&&queue.get(queueIndex).equals(current)&&forward.isEmpty()?queueIndex:queue.indexOf(current);
            if(position>0)track=queue.get(position-1);
        }
        if(track==null){seek(0);return;}
        if(wave&&currentStarted&&!current.batchId().isBlank())submitFeedback("skip",current,listenedMillis+audio.playedMillis(),waveSerial);
        remember(forward,current);
        // The playlist cursor keeps its continuation point; Next retraces forward before resuming it.
        requestStartLocked(track,false);
    }
    public synchronized void togglePause() {
        if (closed || loading) return;
        if (current == null) { if (!upNext.isEmpty()) { Track first=upNext.removeFirst(); queue=List.of(first);originalQueue=queue;queueIndex=0;wave=false;requestStartLocked(first,true);persist(); } else if (!items.isEmpty()) choose(items, 0); }
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
    public float[] spectrum() { return playing() ? audio.spectrum() : new float[24]; }
    public synchronized boolean isWave() { return wave; }
    public synchronized List<Track> queue() { List<Track> list = new ArrayList<>(forward);list.addAll(upNext); if (queueIndex + 1 < queue.size()) list.addAll(queue.subList(queueIndex + 1, queue.size())); return List.copyOf(list); }
    public synchronized void enqueue(Track track) { upNext.addLast(track); persist(); }
    /** Play one upcoming item without copying the explicit queue into the playlist. */
    public synchronized void playQueued(int index) {
        if (closed || index < 0 || index >= queue().size()) return;
        if (wave && currentStarted && current != null && !current.batchId().isBlank())
            submitFeedback("skip", current, listenedMillis + audio.playedMillis(), waveSerial);
        Track selected;
        if(index<forward.size()){
            selected=null;for(int i=0;i<=index;i++)selected=forward.removeFirst();
        }else if (index-forward.size() < upNext.size()) {
            List<Track> explicit = new ArrayList<>(upNext);
            selected = explicit.remove(index-forward.size()); upNext.clear(); upNext.addAll(explicit);forward.clear();
        } else {
            queueIndex += 1 + index - forward.size() - upNext.size(); selected = queue.get(queueIndex);forward.clear();
        }
        requestStartLocked(selected, true);
    }
    public synchronized void removeQueued(int index) {
        if(index<0)return;
        if(index<forward.size()){List<Track> copy=new ArrayList<>(forward);copy.remove(index);forward.clear();forward.addAll(copy);persist();publishWaveList();return;}
        index-=forward.size();
        if(index<upNext.size()){List<Track> copy=new ArrayList<>(upNext);copy.remove(index);upNext.clear();upNext.addAll(copy);}
        else{int actual=queueIndex+1+index-upNext.size();if(actual>=queue.size())return;List<Track> copy=new ArrayList<>(queue);Track removed=copy.remove(actual);queue=List.copyOf(copy);List<Track> original=new ArrayList<>(originalQueue);original.remove(removed);originalQueue=List.copyOf(original);}
        persist();
    }
    public synchronized void clearQueue() {
        upNext.clear();forward.clear(); queue = current == null ? List.of() : List.of(current); originalQueue=queue;queueIndex = current == null ? -1 : 0;
        wave = false; ++waveSerial; persist();
    }
    public void volume(float value) { settings.volume = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : settings.volume; audio.volume(settings.volume); }
    public synchronized void suspend() { invalidateLocked(); loading = false; currentStarted = false; persist(); }
    private synchronized void persist() {
        List<Track> pending=new ArrayList<>(forward);pending.addAll(upNext);
        SessionStore.State snapshot = new SessionStore.State(queue, queueIndex, wave, List.copyOf(pending), Map.copyOf(ratings), Map.copyOf(savedLists), current,originalQueue,Map.of());
        submitStorage(() -> { try { session.save(snapshot); } catch (IOException e) { error = Messages.text("invumusicplayer.error.session_save"); } });
    }
    public synchronized void toggleShuffle() {
        if(closed)return;settings.shuffle=!settings.shuffle;reorderUpcoming(settings.shuffle);publishWaveList();saveSettings();persist();
    }
    private void reorderUpcoming(boolean shuffled) {
        int start=Math.max(0,queueIndex+1);if(start>=queue.size())return;
        List<Track> future=new ArrayList<>(queue.subList(start,queue.size()));
        if(shuffled)Collections.shuffle(future);
        else {
            Map<Track,Integer> remaining=new HashMap<>();for(Track t:future)remaining.merge(t,1,Integer::sum);
            List<Track> ordered=new ArrayList<>();
            for(Track t:originalQueue)if(remaining.getOrDefault(t,0)>0){ordered.add(t);remaining.merge(t,-1,Integer::sum);}
            for(Track t:future)if(remaining.getOrDefault(t,0)>0){ordered.add(t);remaining.merge(t,-1,Integer::sum);}
            future=ordered;
        }
        List<Track> result=new ArrayList<>(queue.subList(0,start));result.addAll(future);queue=List.copyOf(result);
    }
    private static String trackKey(Track track) { return track.remote()&&track.id().matches("[0-9]+:[0-9]+")?track.id().split(":")[0]:track.id(); }
    public int rating() { Track t=current;return t==null?0:ratings.getOrDefault(trackKey(t),ratings.getOrDefault(t.id(),0)); }
    public void rate(boolean like) {
        Track track=current;if(closed||track==null||track.youtube())return;
        int value=like?1:-1;String key=trackKey(track);
        try { feedback.execute(()->{
            int previous=ratings.getOrDefault(key,ratings.getOrDefault(track.id(),0));boolean remove=previous==value;
            try {
                if(track.remote()) {
                    if(!remove&&previous==-value){remote.rate(track,!like,true);ratings.put(key,0);}
                    remote.rate(track,like,remove);
                }
                synchronized(this){if(closed)return;ratings.put(key,remove?0:value);persist();if(showingLiked)items=filterLiked();}
                if(!like&&!remove&&track.equals(current)&&isWave())next();
            }catch(Exception e){fail(e);}
        }); }catch(RejectedExecutionException ignored){}
    }
    public synchronized void showLiked() {
        if(closed)return;showingLiked=true;listingSection="liked";
        long request=++listSerial;cancel(listJob);
        listJob=workers.submit(()->{try{
            List<Track> remoteTracks=connected()?remote.liked():List.of();List<Track> localTracks=local.scan();
            synchronized(this){if(!validList(request))return;
                for(Track t:remoteTracks)ratings.put(trackKey(t),1);
                List<Track> found=new ArrayList<>(remoteTracks);for(Track t:localTracks)if(ratings.getOrDefault(trackKey(t),0)==1)found.add(t);
                likedCache=List.copyOf(found);items=filterLiked();persist();
            }
        }catch(Exception e){synchronized(this){if(validList(request))fail(e);}}});
    }
    private List<Track> filterLiked(){return likedCache.stream().filter(t->ratings.getOrDefault(trackKey(t),0)==1).toList();}
    private void publishWaveList(){
        if(!wave||waveListSerial!=listSerial||current==null)return;
        List<Track> visible=new ArrayList<>();visible.add(current);visible.addAll(queue());items=List.copyOf(visible);
    }
    public synchronized void showWave(){if(closed)return;if(!wave){startWave();return;}showingLiked=false;listingSection="wave";waveListSerial=++listSerial;cancel(listJob);publishWaveList();}
    @FunctionalInterface private interface Listing { List<Track> get() throws Exception; }
    private synchronized void browse(Listing list) {
        showingLiked=false;listingSection="tracks";
        long request = ++listSerial; cancel(listJob); listJob = workers.submit(() -> { try { List<Track> found = List.copyOf(list.get()); synchronized(this) { if (validList(request)) items = found; } } catch (Exception e) { if (validList(request)) fail(e); } });
    }
    public List<YandexMusic.Playlist> playlists() { return playlists; }
    public void loadPlaylists() { if(closed)return;if(!connected()){playlists=List.of();return;}workers.submit(() -> { try { playlists = remote.playlists(); } catch (Exception e) { fail(e); } }); }
    public void openPlaylist(String owner, String kind) { browse(() -> remote.playlist(owner, kind)); }
    public synchronized void importLink(String link) {
        if (closed) return;showingLiked=false;listingSection="tracks";
        long request = ++listSerial; cancel(listJob);
        status = Messages.text("invumusicplayer.youtube.importing"); error = Messages.EMPTY;
        listJob = workers.submit(() -> {
            try {
                java.net.URI uri = java.net.URI.create(link.strip()); String host = uri.getHost();
                if (host == null || !"https".equals(uri.getScheme()) || uri.getUserInfo() != null || (uri.getPort()!=-1 && uri.getPort()!=443))
                    throw Messages.error("invumusicplayer.error.playlist_link");
                List<Track> found; boolean youtube = host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com");
                if (youtube) {
                    YouTubeAudio.validate(link);
                    YouTubeTools.prepare(gameDirectory, message -> { synchronized(this) { if(validList(request))status=message; } });
                    found = List.of(YouTubeAudio.describe(link, gameDirectory));
                } else {
                    if (!Set.of("music.yandex.ru", "music.yandex.com", "music.yandex.kz").contains(host)) throw Messages.error("invumusicplayer.error.playlist_link");
                    java.util.regex.Matcher old = java.util.regex.Pattern.compile("^/users/([^/]+)/playlists/(\\d+)/?$").matcher(uri.getPath());
                    java.util.regex.Matcher modern = java.util.regex.Pattern.compile("^/playlists/([a-zA-Z0-9-]{8,80})/?$").matcher(uri.getPath());
                    if(old.matches())found=remote.playlist(old.group(1),old.group(2));
                    else if(modern.matches())found=remote.playlistUuid(modern.group(1));
                    else throw Messages.error("invumusicplayer.error.playlist_link");
                }
                synchronized(this) {
                    if(!validList(request))return;
                    items=List.copyOf(found); if(youtube)enqueue(found.getFirst());
                    status=Messages.text("invumusicplayer.status.search_count", found.size());error=Messages.EMPTY;
                }
            } catch(Exception e) { synchronized(this) { if(validList(request))fail(e); } }
        });
    }
    public Set<String> savedPlaylists() { return Set.copyOf(savedLists.keySet()); }
    public synchronized void savePlaylist(String title) { String name = title.strip(); if (name.isEmpty() || name.length() > 80) return; List<Track> saved = new ArrayList<>(); if (current != null) saved.add(current); saved.addAll(queue()); savedLists.put(name, List.copyOf(saved)); persist(); }
    public void openSavedPlaylist(String name) { browse(() -> savedLists.getOrDefault(name, List.of())); }
    public synchronized void deleteSavedPlaylist(String name){if(closed)return;savedLists.remove(name);persist();}
    public void deletePlaylist(YandexMusic.Playlist playlist){if(closed)return;workers.submit(()->{try{
        remote.deletePlaylist(playlist.owner(),playlist.kind());
        synchronized(this){playlists=playlists.stream().filter(p->!p.owner().equals(playlist.owner())||!p.kind().equals(playlist.kind())).toList();}
        status=Messages.text("invumusicplayer.status.playlist_deleted",playlist.title());
    }catch(Exception e){fail(e);}});}

    public synchronized void stop() {
        if (closed) return;
        invalidateLocked(); loading = false; current = null; currentStarted = false;
        cover = EMPTY_IMAGE; coverStatus = Messages.EMPTY; currentAudio = null; wave = false; ++waveSerial;
        queue = List.of(); originalQueue=queue;queueIndex = -1; upNext.clear();history.clear();forward.clear(); persist(); status = Messages.text("invumusicplayer.status.stopped");
    }
    private long invalidateLocked() {
        ++serial; cancel(playbackJob); cancel(coverJob); playbackJob = coverJob = null;
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
