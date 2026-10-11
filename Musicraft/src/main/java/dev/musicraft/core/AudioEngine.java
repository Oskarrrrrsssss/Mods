package dev.musicraft.core;

import javax.sound.sampled.*;
import java.io.*;
import java.util.function.Consumer;

/** Streaming decoder/output. No decoding or I/O runs on the Minecraft thread. */
public final class AudioEngine implements Player.Playback {
    public interface Input { InputStream open() throws Exception; }
    @FunctionalInterface interface LineFactory { SourceDataLine create(AudioFormat format) throws Exception; }
    private final LineFactory lines;
    private final Object lock = new Object();
    private Session active;
    private volatile float volume = .7f;

    private static final class Session {
        final long requestedPosition;
        volatile InputStream source;
        volatile SourceDataLine line;
        Thread worker;
        boolean paused, ready, ended;
        long offset, frozenPosition, duration;
        Session(long position, boolean paused) {
            requestedPosition = offset = frozenPosition = Math.max(0, position); this.paused = paused;
        }
    }

    public AudioEngine() { this(AudioSystem::getSourceDataLine); }
    AudioEngine(LineFactory lines) { this.lines = lines; }

    public void play(Input input, long seekMillis, boolean initiallyPaused, Runnable ended, Consumer<Exception> failed) {
        play(input, seekMillis, initiallyPaused, () -> {}, ended, failed);
    }
    @Override public void play(Input input, long seekMillis, boolean initiallyPaused, Runnable ready,
                               Runnable ended, Consumer<Exception> failed) {
        synchronized (lock) {
            stopLocked();
            Session session = active = new Session(seekMillis, initiallyPaused);
            session.worker = Thread.ofPlatform().daemon().name("musicraft-audio").unstarted(
                () -> decode(session, input, ready, ended, failed));
            session.worker.start();
        }
    }

    private void decode(Session session, Input input, Runnable ready, Runnable ended, Consumer<Exception> failed) {
        SourceDataLine line = null;
        try (InputStream source = new BufferedInputStream(input.open())) {
            synchronized (lock) { if (active != session) return; session.source = source; }
            try (AudioInputStream encoded = AudioSystem.getAudioInputStream(source)) {
                AudioFormat src = encoded.getFormat();
                AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(), 16, src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
                try (AudioInputStream pcm = AudioSystem.getAudioInputStream(format, encoded)) {
                    long frames = pcm.getFrameLength();
                    long duration = frames > 0 ? (long) (frames * 1000.0 / format.getFrameRate()) : 0;
                    long skip = (long) (session.requestedPosition * format.getFrameRate() / 1000) * format.getFrameSize();
                    long skipped = 0;
                    byte[] buffer = new byte[8192 - 8192 % format.getFrameSize()];
                    while (skipped < skip && isActive(session)) {
                        int count = pcm.read(buffer, 0, (int) Math.min(buffer.length, skip - skipped));
                        if (count < 0) break;
                        skipped += count;
                    }
                    if (!isActive(session)) return;
                    line = lines.create(format); line.open(format, 32768);
                    synchronized (lock) {
                        if (active != session) return;
                        session.line = line; session.duration = duration;
                        session.offset = (long) (skipped * 1000.0 / format.getFrameSize() / format.getFrameRate());
                        session.frozenPosition = session.offset; session.ready = true;
                        if (!session.paused) line.start();
                    }
                    ready.run();
                    int read;
                    while (isActive(session) && (read = pcm.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        synchronized (lock) {
                            while (active == session && session.paused) lock.wait();
                            if (active != session) return;
                        }
                        applyVolume(buffer, read, volume);
                        int written = 0;
                        while (written < read && isActive(session)) {
                            int count = line.write(buffer, written, read - written);
                            if (count <= 0) {
                                if (!line.isOpen()) throw new IOException("Аудиоустройство было отключено.");
                                Thread.sleep(2);
                            } else written += count;
                        }
                    }
                    if (!isActive(session)) return;
                    line.drain();
                    synchronized (lock) {
                        if (active != session) return;
                        session.frozenPosition = livePosition(session); session.ended = true;
                    }
                    ended.run();
                }
            }
        } catch (Exception e) {
            boolean report;
            synchronized (lock) {
                report = active == session;
                if (report) { session.frozenPosition = livePosition(session); session.ended = true; }
            }
            if (report) failed.accept(e);
        } finally {
            if (line != null) { line.stop(); line.close(); }
            synchronized (lock) {
                if (active == session) { session.line = null; session.source = null; }
            }
        }
    }

    private boolean isActive(Session session) { synchronized (lock) { return active == session; } }
    private static long livePosition(Session session) {
        return session.offset + (session.line == null ? 0 : session.line.getMicrosecondPosition() / 1000);
    }
    static void applyVolume(byte[] pcm, int length, float gain) {
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
            int scaled = Math.round(sample * gain);
            pcm[i] = (byte) scaled; pcm[i + 1] = (byte) (scaled >> 8);
        }
    }
    @Override public long positionMillis() {
        synchronized (lock) {
            if (active == null) return 0;
            return active.paused || active.ended ? active.frozenPosition : livePosition(active);
        }
    }
    @Override public long durationMillis() { synchronized (lock) { return active == null ? 0 : active.duration; } }
    @Override public long playedMillis() {
        synchronized (lock) { return active == null ? 0 : Math.max(0, positionMillis() - active.offset); }
    }
    @Override public boolean playing() { synchronized (lock) { return active != null && active.ready && !active.paused && !active.ended; } }
    @Override public boolean loaded() { synchronized (lock) { return active != null && active.ready && !active.ended; } }
    @Override public void volume(float value) { volume = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : .7f; }
    @Override public void togglePause() {
        synchronized (lock) {
            if (active == null || active.ended) return;
            Session session = active;
            session.paused = !session.paused;
            if (session.line != null) {
                if (session.paused) { session.line.stop(); session.frozenPosition = livePosition(session); }
                else session.line.start();
            }
            lock.notifyAll();
        }
    }
    private void stopLocked() {
        Session previous = active; active = null;
        if (previous != null) {
            if (previous.worker != Thread.currentThread()) previous.worker.interrupt();
            if (previous.line != null) { previous.line.stop(); previous.line.flush(); previous.line.close(); }
            if (previous.source != null) try { previous.source.close(); } catch (IOException ignored) {}
        }
        lock.notifyAll();
    }
    @Override public void stop() { synchronized (lock) { stopLocked(); } }
    @Override public void close() { stop(); }
}
