package dev.invumusicplayer.core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Test-only fixture: real local library and metadata, deterministic clock and no sound device. */
public final class SmokePlayerFactory {
    private SmokePlayerFactory() {}

    /** Runs inside Knot's production mod classloader to catch missing nested JavaSound SPI providers. */
    public static void verifyBundledCodecs() {
        try(var nativePlayer=new NativeSpotifyPlayback(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir())){nativePlayer.verifyInstallation();}
        catch(Exception e){throw new AssertionError("Bundled Spotify native engine is unavailable",e);}
        ImageIO.scanForPlugins();
        try(var webp=SmokePlayerFactory.class.getResourceAsStream("/invumusicplayer-smoke/cover.webp")) {
            var image=ImageIO.read(webp);if(image==null||image.getWidth()!=3)throw new IOException("WebP reader unavailable");
        }catch(Exception e){throw new AssertionError("Bundled WebP artwork decoder is unavailable",e);}
        for (String extension : new String[] {"mp3", "ogg"}) {
            String resource = "/invumusicplayer-smoke/tone." + extension;
            try (var raw = SmokePlayerFactory.class.getResourceAsStream(resource)) {
                if (raw == null) throw new IOException("Missing synthetic codec fixture: " + resource);
                try (var stream = new BufferedInputStream(raw);
                     var encoded = AudioSystem.getAudioInputStream(stream)) {
                    AudioFormat source = encoded.getFormat();
                    AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        source.getSampleRate(), 16, source.getChannels(), source.getChannels() * 2,
                        source.getSampleRate(), false);
                    try (var decoded = AudioSystem.getAudioInputStream(pcm, encoded)) {
                        byte[] sample = decoded.readNBytes(pcm.getFrameSize() * 256);
                        if (sample.length == 0) throw new IOException("Codec produced no PCM frames");
                    }
                }
            } catch (Exception error) {
                throw new AssertionError("Bundled " + extension.toUpperCase(java.util.Locale.ROOT)
                    + " decoder is unavailable in the Minecraft mod classloader", error);
            }
        }
    }

    public static Player create(Path directory) throws IOException {
        // A fresh subdirectory avoids stale settings/credentials when the client test is rerun locally.
        Path referenceCover = Path.of(System.getProperty("invu.referenceCover", directory.resolve("reference-cover.jpg").toString()));
        directory = Files.createTempDirectory(directory, "invumusicplayer-smoke-fixture-");
        Path music = directory.resolve("music");
        Files.createDirectories(music);
        writeTrack(music, "First", 0x82e39b);
        writeTrack(music, "Second", 0x8bc8ef);
        writeTrack(music, "Third", 0xefbd8b);
        if (Files.isRegularFile(referenceCover)) {
            Files.copy(referenceCover, music.resolve("InvuMusicPlayer Smoke - First.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Yandex CDN JPEG fixture used / Использована JPEG-обложка CDN Яндекса");
        }
        return new Player(directory, null, new SilentPlayback());
    }

    private static void writeTrack(Path directory, String title, int color) throws IOException {
        String name = "InvuMusicPlayer Smoke - " + title;
        AudioFormat format = new AudioFormat(8000, 16, 1, true, false);
        // Three seconds of original synthetic tones; no song or account is used.
        byte[] pcm = new byte[8000 * 2 * 3];
        for(int i=0;i<24000;i++) {
            short value=(short)((Math.sin(2*Math.PI*220*i/8000)+Math.sin(2*Math.PI*1300*i/8000)+Math.sin(2*Math.PI*3200*i/8000))*5000);
            pcm[i*2]=(byte)value;pcm[i*2+1]=(byte)(value>>8);
        }
        try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(pcm), format, 24000)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, directory.resolve(name + ".wav").toFile());
        }
        BufferedImage cover = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            cover.setRGB(x, y, (x + y) % 2 == 0 ? color : 0x101813);
        }
        ImageIO.write(cover, "png", directory.resolve(name + ".png").toFile());
    }

    private static final class SilentPlayback implements Player.Playback {
        private volatile boolean loaded, playing;
        private volatile long position = 500;
        private volatile float[] spectrum = new float[24];
        @Override public void play(AudioEngine.Input input, long seekMillis, boolean paused, Runnable ready,
                                   Runnable ended, Consumer<Exception> failed) {
            // Exercise the actual file-open and WAV decoder path without requesting an audio device.
            try (var stream = new java.io.BufferedInputStream(input.open());
                 AudioInputStream audio = AudioSystem.getAudioInputStream(stream)) {
                if (audio.getFrameLength() != 24000) throw new IOException("Unexpected smoke WAV duration");
                byte[] samples=audio.readAllBytes();spectrum=new SpectrumAnalyzer().accept(samples,samples.length,1,8000);
                loaded = true; playing = !paused;
                position = seekMillis == 0 ? 500 : seekMillis;
                ready.run();
            } catch (Exception error) { loaded = playing = false; failed.accept(error); }
        }
        @Override public boolean playing() { return playing; }
        @Override public boolean loaded() { return loaded; }
        @Override public long positionMillis() { return position; }
        @Override public long durationMillis() { return 3000; }
        @Override public float[] spectrum() { return spectrum.clone(); }
        @Override public void togglePause() { if (loaded) playing = !playing; }
        @Override public void volume(float value) {}
        @Override public void stop() { loaded = playing = false; }
        @Override public void close() { stop(); }
    }
}
