package dev.invumusicplayer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerTest {
    @TempDir Path temp;
    private static Track track(String id) { return new Track(id, id, "Artist", 120_000, null, null, "", "batch"); }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "Asynchronous operation did not complete");
    }
    private static final class Audio implements Player.Playback {
        final List<String> played = new CopyOnWriteArrayList<>();
        volatile boolean loaded, paused;
        volatile float[] spectrum=new float[24];
        public float[] spectrum(){return spectrum.clone();}
        volatile long position;
        volatile long seekOffset;
        volatile Runnable ended;
        volatile Consumer<Exception> failed;
        public void play(AudioEngine.Input input, long seek, boolean paused, Runnable ready, Runnable ended, Consumer<Exception> failed) {
            try (InputStream stream = input.open()) { played.add(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
            catch (Exception e) { failed.accept(e); return; }
            this.position = this.seekOffset = seek; this.paused = paused; this.loaded = true; this.ended = ended; this.failed = failed; ready.run();
        }
        public boolean playing() { return loaded && !paused; }
        public boolean loaded() { return loaded; }
        public long positionMillis() { return position; }
        public long playedMillis() { return Math.max(0, position - seekOffset); }
        public long durationMillis() { return 120_000; }
        public void togglePause() { paused = !paused; }
        public void volume(float value) {}
        public void stop() { loaded = false; position = 0; }
        public void close() { stop(); }
    }
    private static class Remote implements Player.RemoteProvider {
        final List<String> fetched = new CopyOnWriteArrayList<>();
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<String> waveRequests = new CopyOnWriteArrayList<>();
        final List<String> authTokens = new CopyOnWriteArrayList<>();
        volatile CountDownLatch blockAudio, blockSearch, blockAuth;
        volatile String blockedId = "", blockedAuthToken = "";
        final AtomicBoolean interrupted = new AtomicBoolean();
        List<List<Track>> batches = List.of(List.of(track("a"), track("b"), track("c")), List.of(track("d"), track("e")));
        final AtomicInteger waveCalls = new AtomicInteger();
        public void checkAccount(String token) throws Exception {
            authTokens.add(token); if (blockedAuthToken.isBlank() || blockedAuthToken.equals(token)) waitIgnoringInterrupt(blockAuth);
        }
        public List<Track> search(String query) { waitIgnoringInterrupt(blockSearch); return List.of(track(query)); }
        public List<Track> wave(String previous) {
            waveRequests.add(previous == null ? "new" : previous); return batches.get(waveCalls.getAndIncrement());
        }
        public void feedback(String type, Track track, long played) {
            events.add(type + ":" + (track == null ? "" : track.id()) + ":" + played);
        }
        public byte[] audio(Track track) {
            fetched.add(track.id()); if (track.id().equals(blockedId)) waitIgnoringInterrupt(blockAudio);
            return track.id().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        public byte[] cover(Track track) { return new byte[0]; }
        private void waitIgnoringInterrupt(CountDownLatch latch) {
            if (latch == null) return;
            while (true) {
                try { latch.await(); return; }
                catch (InterruptedException ignored) { interrupted.set(true); }
            }
        }
    }
    @Test void cancelledDownloadDoesNotDelayNewTrackOrOverwriteItsState() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        remote.blockAudio = new CountDownLatch(1); remote.blockedId = "a";
        try (Player player = new Player(temp, remote, audio)) {
            player.choose(List.of(track("a")), 0); await(() -> remote.fetched.contains("a"));
            player.choose(List.of(track("b")), 0); await(player::playing);
            assertEquals(List.of("b"), audio.played); assertEquals("b", player.current().id());
            await(remote.interrupted::get);
            remote.blockAudio.countDown();
            assertEquals(List.of("b"), audio.played); assertEquals("", player.error());
        } finally { remote.blockAudio.countDown(); }
    }
    @Test void rapidNextChangesQueueIndexBeforeNetworkCompletes() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        remote.blockAudio = new CountDownLatch(1); remote.blockedId = "a";
        try (Player player = new Player(temp, remote, audio)) {
            player.choose(List.of(track("a"), track("b"), track("c")), 0);
            await(() -> remote.fetched.contains("a"));
            player.next(); player.next(); await(() -> player.playing() && player.current().id().equals("c"));
            assertEquals("c", audio.played.getLast());
            player.previous(); await(() -> player.playing() && player.current().id().equals("b"));
            assertEquals("b", audio.played.getLast());
        } finally { remote.blockAudio.countDown(); }
    }
    @Test void previousReturnsToTheSelectedTrackRatherThanAnUnplayedListNeighbour() throws Exception {
        Audio audio=new Audio();List<Track> tracks=List.of(track("a"),track("b"),track("c"),track("d"));
        try(Player p=new Player(temp,new Remote(),audio)){
            p.choose(tracks,0);await(p::playing);p.choose(tracks,3);await(()->p.playing()&&p.current().id().equals("d"));
            p.previous();await(()->p.playing()&&p.current().id().equals("a"));
            assertEquals(List.of("a","d","a"),audio.played);
            assertEquals(List.of("d"),p.queue().stream().map(Track::id).toList());
            p.next();await(()->p.playing()&&p.current().id().equals("d"));p.next();assertFalse(p.playing());
        }
    }
    @Test void previousTraversesExplicitQueueOneTrackAtATimeAndNextRetracesIt() throws Exception {
        Audio audio=new Audio();List<Track> tracks=List.of(track("a"),track("b"),track("c"));
        try(Player p=new Player(temp,new Remote(),audio)){
            p.choose(tracks,0);await(p::playing);p.next();await(()->p.playing()&&p.current().id().equals("b"));
            p.enqueue(track("x"));p.enqueue(track("y"));p.next();await(()->p.playing()&&p.current().id().equals("x"));
            p.previous();await(()->p.playing()&&p.current().id().equals("b"));
            assertEquals(List.of("x","y","c"),p.queue().stream().map(Track::id).toList());
            p.previous();await(()->p.playing()&&p.current().id().equals("a"));
            assertEquals(List.of("b","x","y","c"),p.queue().stream().map(Track::id).toList());
            for(String id:List.of("b","x","y","c")){p.next();await(()->p.playing()&&p.current().id().equals(id));}
            p.next();assertFalse(p.playing());
        }
    }
    @Test void previousAtTheBeginningRestartsTheFirstTrackWithoutWrappingToTheLast() throws Exception {
        Audio audio=new Audio();
        try(Player p=new Player(temp,new Remote(),audio)){
            p.choose(List.of(track("a"),track("b"),track("c")),0);await(p::playing);audio.position=5000;
            p.previous();await(()->p.playing()&&p.positionMillis()==0);assertEquals("a",p.current().id());
            p.next();await(()->p.playing()&&p.current().id().equals("b"));
        }
    }
    @Test void wavePreviousDoesNotSkipASelectedTrackWhoseDownloadIsStillLoading() throws Exception {
        Remote remote=new Remote();Audio audio=new Audio();remote.blockAudio=new CountDownLatch(1);remote.blockedId="b";
        try(Player p=new Player(temp,remote,audio)){
            p.startWave();await(p::playing);p.next();await(()->remote.fetched.contains("b"));
            p.next();await(()->p.playing()&&p.current().id().equals("c"));p.previous();
            assertEquals("b",p.current().id());assertTrue(p.loading());
            remote.blockAudio.countDown();await(()->p.playing()&&p.current().id().equals("b"));
            p.previous();await(()->p.playing()&&p.current().id().equals("a"));
        }finally{remote.blockAudio.countDown();}
    }
    @Test void waveHistoryRetracesAcrossBatchBoundariesWithoutFetchingOrDuplicatingTracks() throws Exception {
        Remote remote=new Remote();Audio audio=new Audio();
        try(Player p=new Player(temp,remote,audio)){
            p.startWave();await(p::playing);
            for(String id:List.of("b","c","d","e")){p.next();await(()->p.playing()&&p.current().id().equals(id));}
            for(String id:List.of("d","c","b")){p.previous();await(()->p.playing()&&p.current().id().equals(id));}
            assertEquals(List.of("c","d","e"),p.queue().stream().map(Track::id).toList());
            for(String id:List.of("c","d","e")){p.next();await(()->p.playing()&&p.current().id().equals(id));}
            assertEquals(2,remote.waveCalls.get());assertTrue(p.isWave());
        }
    }
    @Test void queuedSelectionAndRemovalRespectHistoryForwardEntries() throws Exception {
        Audio audio=new Audio();
        try(Player p=new Player(temp,new Remote(),audio)){
            p.choose(List.of(track("a"),track("b"),track("c"),track("d")),0);await(p::playing);
            for(String id:List.of("b","c")){p.next();await(()->p.playing()&&p.current().id().equals(id));}
            p.previous();await(()->p.playing()&&p.current().id().equals("b"));p.previous();await(()->p.playing()&&p.current().id().equals("a"));
            p.removeQueued(0);assertEquals(List.of("c","d"),p.queue().stream().map(Track::id).toList());
            p.enqueue(track("x"));assertEquals(List.of("c","x","d"),p.queue().stream().map(Track::id).toList());
            p.playQueued(1);await(()->p.playing()&&p.current().id().equals("x"));
            assertEquals(List.of("d"),p.queue().stream().map(Track::id).toList());
            p.previous();await(()->p.playing()&&p.current().id().equals("a"));
            assertEquals(List.of("x","d"),p.queue().stream().map(Track::id).toList());
        }
    }
    @Test void restoredPlaylistFallsBackToExactlyOnePreviousPosition() throws Exception {
        var tracks=List.of(track("a"),track("b"),track("c"));
        try(Player p=new Player(temp,new Remote(),new Audio())){p.choose(tracks,2);await(p::playing);}
        try(Player p=new Player(temp,new Remote(),new Audio())){p.previous();await(()->p.playing()&&p.current().id().equals("b"));p.previous();await(()->p.playing()&&p.current().id().equals("a"));}
    }
    @Test void backNavigationContinuationIsSavedWithoutLosingExplicitQueueOrder() throws Exception {
        try(Player p=new Player(temp,new Remote(),new Audio())){
            p.choose(List.of(track("a"),track("b"),track("c"),track("d")),0);await(p::playing);
            for(String id:List.of("b","c")){p.next();await(()->p.playing()&&p.current().id().equals(id));}
            p.enqueue(track("x"));p.previous();await(()->p.playing()&&p.current().id().equals("b"));
            assertEquals(List.of("c","x","d"),p.queue().stream().map(Track::id).toList());
        }
        try(Player p=new Player(temp,new Remote(),new Audio())){
            assertEquals("b",p.current().id());assertEquals(List.of("c","x","d"),p.queue().stream().map(Track::id).toList());
            for(String id:List.of("c","x","d")){p.next();await(()->p.playing()&&p.current().id().equals(id));}
            p.next();assertFalse(p.playing());
        }
    }
    @Test void selectingAForwardEntryPreservesTheRemainingHistoryAndPlaylistCursor() throws Exception {
        try(Player p=new Player(temp,new Remote(),new Audio())){
            p.choose(List.of(track("a"),track("b"),track("c"),track("d")),0);await(p::playing);
            for(String id:List.of("b","c")){p.next();await(()->p.playing()&&p.current().id().equals(id));}
            p.previous();await(()->p.playing()&&p.current().id().equals("b"));p.previous();await(()->p.playing()&&p.current().id().equals("a"));
            p.playQueued(0);await(()->p.playing()&&p.current().id().equals("b"));
            assertEquals(List.of("c","d"),p.queue().stream().map(Track::id).toList());
            p.toggleShuffle();p.next();await(()->p.playing()&&p.current().id().equals("c"));
            p.next();await(()->p.playing()&&p.current().id().equals("d"));p.next();assertFalse(p.playing());
        }
    }
    @Test void shuffledPreviousKeepsTheActualPlaybackOrderAcrossExplicitQueueEntries() throws Exception {
        try(Player p=new Player(temp,new Remote(),new Audio())){
            p.toggleShuffle();p.choose(List.of(track("a"),track("b"),track("c"),track("d")),0);await(p::playing);
            p.next();await(()->p.playing()&&!p.current().id().equals("a"));String previous=p.current().id();
            p.enqueue(track("x"));p.next();await(()->p.playing()&&p.current().id().equals("x"));
            p.previous();await(()->p.playing()&&p.current().id().equals(previous));
            p.previous();await(()->p.playing()&&p.current().id().equals("a"));
            p.next();await(()->p.playing()&&p.current().id().equals(previous));
            p.next();await(()->p.playing()&&p.current().id().equals("x"));
            assertEquals(2,p.queue().size());assertFalse(p.queue().stream().anyMatch(t->t.id().equals(previous)||t.id().equals("a")||t.id().equals("x")));
        }
    }
    @Test void stalledSearchDoesNotBlockPlaybackAndStaleListIsDiscarded() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio(); remote.blockSearch = new CountDownLatch(1);
        try (Player player = new Player(temp, remote, audio)) {
            player.search("old"); player.choose(List.of(track("a")), 0); await(player::playing);
            player.refreshLocal(); await(() -> player.items().isEmpty()); remote.blockSearch.countDown();
            assertEquals("a", player.current().id()); assertTrue(player.items().isEmpty());
        } finally { remote.blockSearch.countDown(); }
    }
    @Test void staleNaturalEndCannotSkipReplacementTrack() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        try (Player player = new Player(temp, remote, audio)) {
            player.choose(List.of(track("a"), track("b"), track("c")), 0); await(player::playing);
            Runnable obsolete = audio.ended;
            player.choose(List.of(track("a"), track("b"), track("c")), 1); await(() -> player.playing() && audio.played.size() == 2);
            obsolete.run();
            assertEquals("b", player.current().id()); assertEquals(List.of("a", "b"), audio.played);
            audio.position = 120_000; audio.ended.run(); await(() -> player.playing() && player.current().id().equals("c"));
        }
    }
    @Test void seekRetainsPauseAndNaturalEndGuard() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        try (Player player = new Player(temp, remote, audio)) {
            player.choose(List.of(track("a"), track("b")), 0); await(player::playing);
            Runnable obsolete = audio.ended; player.togglePause(); assertFalse(player.playing());
            player.seek(32_000); assertFalse(player.playing()); assertEquals(32_000, player.positionMillis());
            obsolete.run(); assertEquals("a", player.current().id());
            player.togglePause(); assertTrue(player.playing());
            audio.ended.run(); await(() -> player.playing() && player.current().id().equals("b"));
        }
    }
    @Test void waveConsumesBatchAndSendsOrderedFeedbackWithPlayedClock() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        try (Player player = new Player(temp, remote, audio)) {
            player.startWave(); await(player::playing);
            audio.position = 12_300; player.next(); await(() -> player.playing() && player.current().id().equals("b"));
            audio.position = 120_000; audio.ended.run(); await(() -> player.playing() && player.current().id().equals("c"));
            assertEquals(1, remote.waveCalls.get(), "The initial batch must be consumed before asking for more recommendations");
            audio.position = 17_000; player.next(); await(() -> player.playing() && player.current().id().equals("d"));
            assertEquals(List.of("new", "c"), remote.waveRequests);
            await(() -> remote.events.size() == 8);
            assertEquals(List.of("radioStarted::0", "trackStarted:a:0", "skip:a:12300", "trackStarted:b:0",
                "trackFinished:b:120000", "trackStarted:c:0", "skip:c:17000", "trackStarted:d:0"), remote.events);
            player.previous(); await(() -> player.playing() && player.current().id().equals("c"));
        }
    }
    @Test void decoderFailureCanBeRetriedWithoutReopeningPlayer() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        try (Player player = new Player(temp, remote, audio)) {
            player.choose(List.of(track("a")), 0); await(player::playing);
            audio.loaded = false; audio.failed.accept(new IOException("Аудиоустройство недоступно."));
            assertFalse(player.loading()); assertFalse(player.error().isBlank());
            player.togglePause(); await(() -> player.playing() && audio.played.size() == 2);
            assertEquals("", player.error());
        }
    }
    @Test void waveFeedbackMeasuresListeningAcrossSeekInsteadOfSongPosition() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        try (Player player = new Player(temp, remote, audio)) {
            player.startWave(); await(player::playing);
            audio.position = 10_000; player.seek(60_000); assertEquals(60_000, player.positionMillis());
            audio.position = 65_000; player.next(); await(() -> player.playing() && player.current().id().equals("b"));
            await(() -> remote.events.size() == 4);
            assertEquals(List.of("radioStarted::0", "trackStarted:a:0", "skip:a:15000", "trackStarted:b:0"), remote.events);
        }
    }
    @Test void waveRefillRetainsSeparatelySelectedSearchResults() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        try (Player player = new Player(temp, remote, audio)) {
            player.startWave(); await(player::playing);
            player.search("search-result"); await(() -> player.items().size() == 1 && player.items().getFirst().id().equals("search-result"));
            player.next(); await(() -> player.playing() && player.current().id().equals("b"));
            player.next(); await(() -> player.playing() && player.current().id().equals("c"));
            player.next(); await(() -> player.playing() && player.current().id().equals("d"));
            assertEquals("search-result", player.items().getFirst().id());
        }
    }
    @Test void disconnectCancelsPendingAuthorizationAndPreventsCredentialWrite() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio(); remote.blockAuth = new CountDownLatch(1);
        try (Player player = new Player(temp, remote, audio)) {
            player.connect("valid.test-token"); await(player::connecting);
            player.disconnect(); remote.blockAuth.countDown(); await(() -> !player.connecting());
            assertFalse(player.connected()); assertFalse(Files.exists(temp.resolve("config/invumusicplayer-auth.dat")));
        } finally { remote.blockAuth.countDown(); }
    }
    @Test void closePreventsPendingDownloadFromStartingAudio() throws Exception {
        Remote remote = new Remote(); Audio audio = new Audio();
        remote.blockAudio = new CountDownLatch(1); remote.blockedId = "a";
        Player player = new Player(temp, remote, audio);
        player.choose(List.of(track("a")), 0); await(() -> remote.fetched.contains("a")); player.close();
        remote.blockAudio.countDown(); assertFalse(player.playing()); assertTrue(audio.played.isEmpty());
    }
    private TokenStore testStore() {
        return new TokenStore(temp.resolve("config/invumusicplayer-auth.dat"), new TokenStore.Protection() {
            private byte[] transform(byte[] value) { byte[] copy = value.clone(); for (int i = 0; i < copy.length; i++) copy[i] ^= 0x5a; return copy; }
            public byte[] protect(byte[] plain) { return transform(plain); }
            public byte[] unprotect(byte[] encrypted) { return transform(encrypted); }
        });
    }
    @Test void savedAccountIsNotReportedConnectedUntilValidationSucceeds() throws Exception {
        Remote remote = new Remote(); remote.blockAuth = new CountDownLatch(1); TokenStore store = testStore();
        store.save("saved-token");
        try (Player player = new Player(temp, remote, new Audio(), store)) {
            await(player::connecting); assertFalse(player.connected());
            remote.blockAuth.countDown(); await(() -> player.connected() && !player.connecting());
            assertEquals(List.of("saved-token"), remote.authTokens); assertEquals("", player.accountError());
        } finally { remote.blockAuth.countDown(); }
    }
    @Test void disconnectDuringSavedAccountValidationCannotRestoreIt() throws Exception {
        Remote remote = new Remote(); remote.blockAuth = new CountDownLatch(1); TokenStore store = testStore();
        store.save("saved-token");
        try (Player player = new Player(temp, remote, new Audio(), store)) {
            await(player::connecting); player.disconnect(); remote.blockAuth.countDown();
            await(() -> !Files.exists(temp.resolve("config/invumusicplayer-auth.dat")));
            assertFalse(player.connected()); assertFalse(player.connecting());
        } finally { remote.blockAuth.countDown(); }
    }
    @Test void laterAuthorizationWinsEvenIfCanceledCheckIgnoresInterrupt() throws Exception {
        Remote remote = new Remote(); remote.blockAuth = new CountDownLatch(1); remote.blockedAuthToken = "old-token";
        TokenStore store = testStore();
        try (Player player = new Player(temp, remote, new Audio(), store)) {
            player.connect("old-token"); await(() -> remote.authTokens.contains("old-token"));
            player.connect("new-token"); await(() -> player.connected() && !player.connecting());
            remote.blockAuth.countDown();
            assertEquals("new-token", store.load()); assertEquals("", player.accountError());
        } finally { remote.blockAuth.countDown(); }
    }
    @Test void rejectedReplacementPreservesConnectedAccountAndDoesNotEchoToken() throws Exception {
        Remote remote = new Remote() {
            @Override public void checkAccount(String token) throws Exception {
                if (token.equals("bad-token")) throw new IOException("bad-token");
                super.checkAccount(token);
            }
        };
        TokenStore store = testStore();
        try (Player player = new Player(temp, remote, new Audio(), store)) {
            player.connect("good-token"); await(() -> player.connected() && !player.connecting());
            player.connect("bad-token"); await(() -> !player.accountError().isBlank() && !player.connecting());
            assertTrue(player.connected()); assertEquals("good-token", store.load());
            assertFalse(player.accountError().contains("bad-token"));
            assertEquals("Яндекс Музыка подключена", player.accountStatus());
        }
    }

    @Test void nativeAccountErrorsDoNotExposeFilesystemPathsOrUsernames() throws Exception {
        Remote remote = new Remote() {
            @Override public void checkAccount(String token) throws Exception {
                throw new IOException("C:\\Users\\PrivateUser\\config\\invumusicplayer-auth.dat: access denied");
            }
        };
        try (Player player = new Player(temp, remote, new Audio(), testStore())) {
            player.connect("valid-token");
            await(() -> !player.accountError().isBlank() && !player.connecting());
            assertEquals(Messages.get("invumusicplayer.error.playback"), player.accountError());
            assertFalse(player.accountError().contains("PrivateUser"));
            assertFalse(player.accountError().contains("invumusicplayer-auth.dat"));
        }
    }

    @Test void choosingWaveTrackKeepsRadioAndFetchesNextBatchRatherThanLooping() throws Exception {
        Remote remote=new Remote();Audio audio=new Audio();
        try(Player player=new Player(temp,remote,audio)) {
            player.startWave();await(player::playing);
            player.choose(player.items(),2);await(()->player.playing()&&player.current().id().equals("c"));
            assertTrue(player.isWave());audio.ended.run();await(()->player.playing()&&player.current().id().equals("d"));
            assertEquals(List.of("new","c"),remote.waveRequests);assertEquals("d",player.items().getFirst().id());
            player.previous();await(()->player.playing()&&player.current().id().equals("c"));assertTrue(player.isWave());
            player.next();await(()->player.playing()&&player.current().id().equals("d"));assertEquals(2,remote.waveCalls.get());
        }
    }
    @Test void explicitStopRetainsQueueAndSavedPlaylistsAcrossRestart() throws Exception {
        Remote remote=new Remote();Audio audio=new Audio();
        try(Player player=new Player(temp,remote,audio)) {
            player.choose(List.of(track("a"),track("b")),0);await(player::playing);player.enqueue(track("c"));
            player.savePlaylist("My list");player.suspend();assertFalse(player.playing());
            assertEquals(List.of("c","b"),player.queue().stream().map(Track::id).toList());
        }
        try(Player restored=new Player(temp,new Remote(),new Audio())) {
            assertEquals(List.of("c","b"),restored.queue().stream().map(Track::id).toList());
            assertTrue(restored.savedPlaylists().contains("My list"));assertFalse(restored.playing());
        }
    }
    @Test void explicitQueuePrecedesPlaylistAndRemovingUpcomingDoesNotTouchCurrent() throws Exception {
        Remote remote=new Remote();Audio audio=new Audio();
        try(Player player=new Player(temp,remote,audio)) {
            player.choose(List.of(track("a"),track("b"),track("c")),0);await(player::playing);
            player.enqueue(track("x"));player.removeQueued(2);player.next();await(()->player.playing()&&player.current().id().equals("x"));
            player.next();await(()->player.playing()&&player.current().id().equals("b"));
            player.next();assertFalse(player.playing());assertEquals(List.of("a","x","b"),audio.played);
        }
    }
    private static void waitForGate(CountDownLatch gate){try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    @Test void selectingExplicitQueuedTrackDoesNotDuplicateRemainderAndPersistsCurrent() throws Exception {
        Audio audio = new Audio();
        try(Player player = new Player(temp,new Remote(),audio)) {
            player.choose(List.of(track("a"),track("b"),track("c")),0);await(player::playing);
            player.enqueue(track("x"));player.enqueue(track("y"));player.playQueued(0);
            await(()->player.playing()&&player.current().id().equals("x"));
            assertEquals(List.of("y","b","c"),player.queue().stream().map(Track::id).toList());
        }
        try(Player player = new Player(temp,new Remote(),new Audio())) {
            assertEquals("x",player.current().id());assertFalse(player.playing());player.togglePause();await(player::playing);
            player.next();await(()->player.playing()&&player.current().id().equals("y"));
            player.next();await(()->player.playing()&&player.current().id().equals("b"));
            player.next();await(()->player.playing()&&player.current().id().equals("c"));
            player.next();assertFalse(player.playing());
        }
    }
    @Test void selectingUpcomingWaveTrackRetainsRadioAndRefillsAfterBatch() throws Exception {
        Remote remote=new Remote();Audio audio=new Audio();
        try(Player player=new Player(temp,remote,audio)) {
            player.startWave();await(player::playing);player.enqueue(track("x"));player.enqueue(track("y"));
            player.playQueued(3);await(()->player.playing()&&player.current().id().equals("c"));
            assertTrue(player.isWave());assertEquals(List.of("x","y"),player.queue().stream().map(Track::id).toList());
            player.next();await(()->player.playing()&&player.current().id().equals("x"));
            player.next();await(()->player.playing()&&player.current().id().equals("y"));
            player.next();await(()->player.playing()&&player.current().id().equals("d"));
            assertEquals(List.of("new","c"),remote.waveRequests);
        }
    }
    @Test void artworkStartsBeforeSlowAudioAndCanRetryAfterFailure() throws Exception {
        AtomicInteger calls=new AtomicInteger();Remote remote=new Remote(){public byte[] cover(Track t){if(calls.incrementAndGet()==1)throw new IllegalStateException();return new byte[]{1,2,3};}};
        remote.blockAudio=new CountDownLatch(1);remote.blockedId="a";
        try(Player player=new Player(temp,remote,new Audio())) {
            player.choose(List.of(track("a")),0);await(()->!player.coverStatus().isBlank()&&calls.get()==1);
            await(()->player.coverStatus().contains(Messages.get("invumusicplayer.error.playback")));
            assertTrue(player.loading());player.retryArtwork();await(()->player.coverBytes().length==3);
            assertSame(player.coverBytes(),player.coverFor(track("a")));
        } finally { remote.blockAudio.countDown(); }
    }
    @Test void naturalAdvancePersistsTheCurrentPlaylistIndex() throws Exception {
        Audio audio=new Audio();
        try(Player player=new Player(temp,new Remote(),audio)){
            player.choose(List.of(track("a"),track("b"),track("c")),0);await(player::playing);audio.ended.run();
            await(()->player.playing()&&player.current().id().equals("b"));
        }
        try(Player restored=new Player(temp,new Remote(),new Audio())){
            assertEquals("b",restored.current().id());assertEquals(List.of("c"),restored.queue().stream().map(Track::id).toList());
        }
    }
    @Test void playerExposesMeasuredSpectrumOnlyWhilePlaying() throws Exception {
        Audio audio=new Audio();audio.spectrum[5]=.8f;
        try(Player player=new Player(temp,new Remote(),audio)) {
            player.choose(List.of(track("a")),0);await(player::playing);
            assertEquals(.8f,player.spectrum()[5]);player.togglePause();assertArrayEquals(new float[24],player.spectrum());
            player.togglePause();assertEquals(.8f,player.spectrum()[5]);player.suspend();assertArrayEquals(new float[24],player.spectrum());
        }
    }
    @Test void clearingWaveQueueStopsRefillsButKeepsCurrentAudio() throws Exception {
        Remote remote=new Remote();Audio audio=new Audio();
        try(Player player=new Player(temp,remote,audio)) {
            player.startWave();await(player::playing);player.enqueue(track("x"));player.clearQueue();
            assertTrue(player.playing());assertFalse(player.isWave());assertTrue(player.queue().isEmpty());
            audio.ended.run();assertFalse(player.playing());assertEquals(1,remote.waveCalls.get());
        }
    }
    @Test void shuffleAfterPreviousReordersForwardContinuationAndRestoresIt()throws Exception{
        Audio audio=new Audio();try(Player p=new Player(temp,new Remote(),audio)){var tracks=List.of(track("a"),track("b"),track("c"));p.choose(tracks,0);await(p::playing);p.next();await(()->p.playing()&&p.current().id().equals("b"));p.next();await(()->p.playing()&&p.current().id().equals("c"));p.previous();await(()->p.playing()&&p.current().id().equals("b"));p.previous();await(()->p.playing()&&p.current().id().equals("a"));var before=p.queue();int starts=audio.played.size();assertEquals(List.of("b","c"),before.stream().map(Track::id).toList());p.toggleShuffle();assertEquals(List.of("c","b"),p.queue().stream().map(Track::id).toList());assertEquals(starts,audio.played.size());assertEquals("a",p.current().id());p.toggleShuffle();assertEquals(before,p.queue());}
    }
    @Test void shuffleKeepsCurrentAndExplicitQueueAndRestoresUpcomingOrder() throws Exception {
        Audio audio=new Audio();List<Track> tracks=List.of(track("a"),track("b"),track("c"),track("d"));
        try(Player p=new Player(temp,new Remote(),audio)){
            p.choose(tracks,0);await(p::playing);p.enqueue(track("x"));int starts=audio.played.size();p.toggleShuffle();
            assertEquals("a",p.current().id());assertEquals(starts,audio.played.size());assertEquals("x",p.queue().getFirst().id());
            assertEquals(Set.of("x","b","c","d"),new HashSet<>(p.queue().stream().map(Track::id).toList()));
            p.toggleShuffle();assertEquals(List.of("x","b","c","d"),p.queue().stream().map(Track::id).toList());
            p.next();await(()->p.playing()&&p.current().id().equals("x"));p.next();await(()->p.playing()&&p.current().id().equals("b"));
        }
    }
    @Test void shuffleChangesManualQueueWithoutRestartingAndRestoresItsRemainder()throws Exception{
        Audio audio=new Audio();try(Player p=new Player(temp,new Remote(),audio)){p.choose(List.of(track("a"),track("b"),track("c")),0);await(p::playing);p.enqueue(track("x"));p.enqueue(track("y"));p.enqueue(track("z"));var before=p.queue();int count=audio.played.size();p.toggleShuffle();assertNotEquals(before.subList(0,3),p.queue().subList(0,3));assertNotEquals(before.subList(3,5),p.queue().subList(3,5));assertEquals(count,audio.played.size());p.toggleShuffle();assertEquals(before,p.queue());p.toggleShuffle();Track consumed=p.queue().getFirst();p.next();await(()->p.playing()&&p.current().equals(consumed));p.toggleShuffle();assertEquals(before.stream().filter(t->!t.equals(consumed)).toList(),p.queue());}
    }
    @Test void shuffledSelectionPlaysChosenSongAndConsumesEverySongExactlyOnce() throws Exception {
        Audio audio=new Audio();List<Track> tracks=List.of(track("a"),track("b"),track("c"),track("d"));
        try(Player p=new Player(temp,new Remote(),audio)){
            p.toggleShuffle();p.choose(tracks,2);await(p::playing);assertEquals("c",p.current().id());
            for(int n=1;n<4;n++){int count=n+1;p.next();await(()->p.playing()&&audio.played.size()==count);}
            p.next();assertFalse(p.playing());assertEquals(4,new HashSet<>(audio.played).size());
        }
    }
    @Test void shufflePersistsAndTurningItOffAfterRestartRestoresOriginalRemainder() throws Exception {
        List<Track> tracks=List.of(track("a"),track("b"),track("c"),track("d"));
        try(Player p=new Player(temp,new Remote(),new Audio())){p.choose(tracks,0);await(p::playing);p.toggleShuffle();}
        try(Player p=new Player(temp,new Remote(),new Audio())){assertTrue(p.settings.shuffle);assertEquals("a",p.current().id());p.toggleShuffle();assertEquals(List.of("b","c","d"),p.queue().stream().map(Track::id).toList());}
    }
    @Test void repeatedLikeRemovesOnlyLikeAndRatingSwitchRemovesOppositeFirst() throws Exception {
        List<String> calls=new CopyOnWriteArrayList<>();Remote remote=new Remote(){public void rate(Track t,boolean like,boolean remove){calls.add(like+":"+remove);}};
        try(Player p=new Player(temp,remote,new Audio())){
            p.choose(List.of(track("123:456")),0);await(p::playing);p.rate(true);await(()->p.rating()==1);p.rate(true);await(()->p.rating()==0);
            assertEquals(List.of("true:false","true:true"),calls);
            p.rate(false);await(()->p.rating()==-1);assertEquals("123:456",p.current().id());assertTrue(p.playing());
            p.rate(true);await(()->p.rating()==1);assertEquals(List.of("true:false","true:true","false:false","false:true","true:false"),calls);
        }
    }
    @Test void likedListHasNoManualMoodFilterAndDoesNotOverwriteLaterSearch() throws Exception {
        CountDownLatch gate=new CountDownLatch(1);Remote remote=new Remote(){public List<Track> liked(){waitForGate(gate);return List.of(track("123"),track("456"));}};
        try(Player p=new Player(temp,remote,new Audio(),testStore())){
            p.connect("test-token");await(p::connected);p.showLiked();p.search("other");gate.countDown();await(()->p.items().size()==1&&p.items().getFirst().id().equals("other"));
            p.showLiked();await(()->p.items().size()==2);assertEquals("liked",p.listingSection());assertEquals(List.of("123","456"),p.items().stream().map(Track::id).toList());
        }finally{gate.countDown();}
    }
    @Test void waveVisibleListMovesOnEverySkipAndDoesNotOverwriteLiked() throws Exception {
        Remote remote=new Remote();Audio audio=new Audio();try(Player p=new Player(temp,remote,audio)){
            p.startWave();await(p::playing);assertEquals(List.of("a","b","c"),p.items().stream().map(Track::id).toList());
            p.next();await(()->p.playing()&&p.current().id().equals("b"));assertEquals(List.of("b","c"),p.items().stream().map(Track::id).toList());
            p.next();await(()->p.playing()&&p.current().id().equals("c"));assertEquals(List.of("c"),p.items().stream().map(Track::id).toList());
            p.search("other");await(()->p.items().getFirst().id().equals("other"));p.next();await(()->p.playing()&&p.current().id().equals("d"));assertEquals("other",p.items().getFirst().id());
            p.showWave();assertEquals(List.of("d","e"),p.items().stream().map(Track::id).toList());assertEquals("wave",p.listingSection());
        }
    }
    @Test void deletingLocalPlaylistKeepsCurrentAudioAndQueueAndPersistsDeletion() throws Exception {
        try(Player p=new Player(temp,new Remote(),new Audio())){
            p.choose(List.of(track("a"),track("b")),0);await(p::playing);p.savePlaylist("Example");assertTrue(p.savedPlaylists().contains("Example"));
            p.deleteSavedPlaylist("Example");assertFalse(p.savedPlaylists().contains("Example"));assertTrue(p.playing());assertEquals("a",p.current().id());assertEquals("b",p.queue().getFirst().id());
        }
        try(Player p=new Player(temp,new Remote(),new Audio())){assertFalse(p.savedPlaylists().contains("Example"));assertEquals("b",p.queue().getFirst().id());}
    }

    @Test void restoredWaveUsesCorrectSectionAndVisibleCurrentTrack() throws Exception {
        try(Player p=new Player(temp,new Remote(),new Audio())){p.startWave();await(p::playing);p.next();await(()->p.playing()&&p.current().id().equals("b"));}
        try(Player p=new Player(temp,new Remote(),new Audio())){assertEquals("wave",p.listingSection());assertEquals(List.of("b","c"),p.items().stream().map(Track::id).toList());assertFalse(p.playing());}
    }

}
