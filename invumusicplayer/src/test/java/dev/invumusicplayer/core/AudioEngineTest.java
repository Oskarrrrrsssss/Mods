package dev.invumusicplayer.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.sound.sampled.*;
import static org.junit.jupiter.api.Assertions.*;

final class AudioEngineTest {
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "Audio state did not settle");
    }
    private static byte[] wav() throws Exception {
        AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(new byte[44100 * 4 * 3]), format, 44100 * 3)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, output);
        }
        return output.toByteArray();
    }
    /** A controllable output line measures decoder state independently of a physical audio device. */
    private static final class Line implements InvocationHandler {
        volatile boolean open, running;
        volatile AudioFormat format;
        final AtomicLong micros = new AtomicLong();
        final CountDownLatch writes = new CountDownLatch(1), drain = new CountDownLatch(1);
        final AtomicInteger writeCount = new AtomicInteger();
        SourceDataLine proxy() {
            return (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SourceDataLine.class}, this);
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            return switch (method.getName()) {
                case "open" -> { format = (AudioFormat) args[0]; open = true; yield null; }
                case "close" -> { open = false; running = false; writes.countDown(); drain.countDown(); yield null; }
                case "start" -> { running = true; yield null; }
                case "stop" -> { running = false; yield null; }
                case "write" -> {
                    writeCount.incrementAndGet(); writes.await();
                    yield open ? (int) args[2] : 0;
                }
                case "drain" -> { drain.await(); yield null; }
                case "getMicrosecondPosition" -> micros.get();
                case "getLongFramePosition" -> (long) (micros.get() * format.getFrameRate() / 1_000_000);
                case "getFramePosition" -> (int) (micros.get() * format.getFrameRate() / 1_000_000);
                case "isOpen" -> open;
                case "isRunning", "isActive" -> running;
                case "getFormat" -> format;
                case "getBufferSize", "available" -> 32768;
                case "getLevel" -> 0f;
                case "getControls" -> new Control[0];
                case "isControlSupported" -> false;
                case "getLineInfo" -> new DataLine.Info(SourceDataLine.class, format);
                case "toString" -> "Test output line";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        }
    }
    @Test void pausedPlaybackFreezesClockAndResumesSameLine() throws Exception {
        Line line = new Line(); byte[] wav = wav(); AtomicReference<Exception> failure = new AtomicReference<>();
        try (AudioEngine audio = new AudioEngine(format -> line.proxy())) {
            audio.play(() -> new ByteArrayInputStream(wav), 500, false, () -> {}, failure::set);
            await(audio::playing); await(() -> line.writeCount.get() > 0);
            assertEquals(3000, audio.durationMillis());
            line.micros.set(350_000); assertEquals(850, audio.positionMillis());
            assertEquals(350, audio.playedMillis());
            audio.togglePause(); assertFalse(audio.playing()); assertTrue(audio.loaded());
            line.micros.set(700_000); assertEquals(850, audio.positionMillis(), "Paused clocks must not follow a device position update");
            assertEquals(350, audio.playedMillis());
            audio.togglePause(); assertTrue(audio.playing()); assertEquals(1200, audio.positionMillis());
            assertNull(failure.get());
        }
    }
    @Test void initiallyPausedSeekStartsAtDecodedOffsetAndStopResetsDuration() throws Exception {
        Line line = new Line(); byte[] wav = wav(); AtomicReference<Exception> failure = new AtomicReference<>();
        try (AudioEngine audio = new AudioEngine(format -> line.proxy())) {
            audio.play(() -> new ByteArrayInputStream(wav), 1000, true, () -> {}, failure::set);
            await(audio::loaded);
            assertFalse(audio.playing()); assertFalse(line.running); assertEquals(1000, audio.positionMillis());
            assertEquals(0, line.writeCount.get());
            audio.togglePause(); await(() -> line.writeCount.get() > 0); assertTrue(audio.playing());
            audio.stop(); assertFalse(audio.loaded()); assertEquals(0, audio.positionMillis()); assertEquals(0, audio.durationMillis());
            assertNull(failure.get(), "Stopping a decoder must not report a playback failure");
        }
    }
    @Test void completionPreservesFinalClockAndReplacedLineCannotCompleteNewPlayback() throws Exception {
        byte[] wav = wav(); Line first = new Line(), second = new Line(); AtomicInteger created = new AtomicInteger();
        AtomicInteger ended = new AtomicInteger(); AtomicReference<Exception> failure = new AtomicReference<>();
        try (AudioEngine audio = new AudioEngine(format -> created.getAndIncrement() == 0 ? first.proxy() : second.proxy())) {
            audio.play(() -> new ByteArrayInputStream(wav), 0, false, ended::incrementAndGet, failure::set);
            await(audio::playing);
            audio.play(() -> new ByteArrayInputStream(wav), 500, false, ended::incrementAndGet, failure::set);
            await(() -> created.get() == 2 && audio.playing());
            assertEquals(0, ended.get());
            second.micros.set(2_500_000); second.writes.countDown(); second.drain.countDown();
            await(() -> ended.get() == 1);
            assertFalse(audio.playing()); assertEquals(3000, audio.positionMillis()); assertNull(failure.get());
        }
    }
    @Test void failedDeviceCanBeReplacedByWorkingOutput() throws Exception {
        byte[] wav = wav(); Line line = new Line(); AtomicInteger created = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();
        try (AudioEngine audio = new AudioEngine(format -> {
            if (created.getAndIncrement() == 0) throw new LineUnavailableException("Test device unavailable");
            return line.proxy();
        })) {
            audio.play(() -> new ByteArrayInputStream(wav), 0, false, () -> {}, failure::set);
            await(() -> failure.get() != null); assertFalse(audio.playing());
            audio.play(() -> new ByteArrayInputStream(wav), 0, false, () -> {}, failure::set);
            await(audio::playing); assertEquals(3000, audio.durationMillis());
        }
    }
}
