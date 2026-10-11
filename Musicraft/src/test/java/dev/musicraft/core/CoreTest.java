package dev.musicraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import javax.sound.sampled.*;
import static org.junit.jupiter.api.Assertions.*;

final class CoreTest {
    @TempDir Path temp;
    @Test void timedLyricsSupportBomOffsetsAndRepeatedChorus() {
        Lyrics lrc = Lyrics.parse("\ufeff[ar:Test]\n[00:01.2][00:03.045]one\n[00:05]two\n[offset:-200]");
        assertEquals(List.of(new Lyrics.Line(1000, "one"), new Lyrics.Line(2845, "one"), new Lyrics.Line(4800, "two")), lrc.lines());
        assertEquals("", lrc.textAt(999)); assertEquals("one", lrc.textAt(1000)); assertEquals("two", lrc.textAt(5000));
    }
    @Test void timelineSortsAndBlankLinesClearSubtitles() {
        Lyrics lrc = Lyrics.parse("[00:08]later\n[00:02]first\n[00:04]\n[00:06]<00:06.000>word");
        assertEquals("first", lrc.textAt(2500)); assertEquals("", lrc.textAt(4000));
        assertEquals("word", lrc.textAt(6500)); assertEquals("later", lrc.textAt(8000));
    }
    @Test void metadataAndPlainTextAreNotMistakenForTimedLyrics() {
        assertEquals(-1, Lyrics.parse("[ti:Test]\nplain words\n[99:99]invalid").indexAt(999999));
    }
    @Test void localLibraryFindsNestedTracksAndSidecars() throws Exception {
        Path dir = temp.resolve("music"); Files.createDirectories(dir.resolve("album"));
        Files.write(dir.resolve("album/Artist - Song.MP3"), new byte[0]);
        Files.writeString(dir.resolve("album/Artist - Song.json"), "{\"title\":\"Custom title\",\"artist\":\"Custom artist\"}");
        Files.writeString(dir.resolve("album/Artist - Song.lrc"), "[00:01]line");
        Files.write(dir.resolve("album/Artist - Song.png"), new byte[0]);
        Files.write(dir.resolve("ignore.exe"), new byte[0]);
        LocalLibrary library = new LocalLibrary(dir); List<Track> tracks = library.scan();
        assertEquals(1, tracks.size()); assertEquals("Custom title", tracks.getFirst().title());
        assertEquals("Custom artist", tracks.getFirst().artist()); assertNotNull(tracks.getFirst().coverFile());
        assertEquals("line", library.lyrics(tracks.getFirst()).textAt(1000));
    }
    @Test void badSidecarDoesNotHideTrackAndFilenameFallbackWorks() throws Exception {
        Files.write(temp.resolve("Artist - Track.wav"), new byte[0]);
        Files.writeString(temp.resolve("Artist - Track.json"), "bad-json");
        Track track = new LocalLibrary(temp).scan().getFirst();
        assertEquals("Artist", track.artist()); assertEquals("Track", track.title());
    }
    @Test void libraryDoesNotFollowTracksOutsideItsDirectory() throws Exception {
        Path outside = temp.resolve("outside.mp3"); Files.write(outside, new byte[0]);
        Path dir = temp.resolve("music"); Files.createDirectories(dir);
        try { Files.createSymbolicLink(dir.resolve("link.mp3"), outside); }
        catch (UnsupportedOperationException | FileSystemException e) { return; }
        assertTrue(new LocalLibrary(dir).scan().isEmpty());
    }
    @Test void settingsPersistAndNormalizeInvalidValues() throws Exception {
        Settings settings = new Settings(); settings.hudX = 23; settings.hudY = 47;
        settings.lyrics3d = false; settings.covers = false; settings.volume = Float.NaN;
        settings.hudScale = 99; settings.lyricDistance = -5;
        Path config = temp.resolve("config/musicraft.json"); settings.save(config);
        Settings loaded = Settings.load(config);
        assertEquals(23, loaded.hudX); assertEquals(47, loaded.hudY); assertFalse(loaded.lyrics3d);
        assertFalse(loaded.covers); assertEquals(.7f, loaded.volume); assertEquals(2f, loaded.hudScale); assertEquals(1.5f, loaded.lyricDistance);
    }
    @Test void corruptedSettingsAreBackedUp() throws Exception {
        Path config = temp.resolve("musicraft.json"); Files.writeString(config, "broken");
        assertTrue(Settings.load(config).hud);
        try (var files = Files.list(temp)) { assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("musicraft.json.invalid-"))); }
    }
    @Test void pcmVolumePreservesSignAndSilencesExactly() {
        byte[] bytes = {0, (byte) 0x80, (byte) 0xff, 0x7f};
        AudioEngine.applyVolume(bytes, bytes.length, .5f);
        assertEquals((short) -16384, (short) ((bytes[0] & 255) | bytes[1] << 8));
        assertEquals((short) 16384, (short) ((bytes[2] & 255) | bytes[3] << 8));
        AudioEngine.applyVolume(bytes, bytes.length, 0); assertArrayEquals(new byte[4], bytes);
    }
    @Test void assetUrlsRejectOtherOriginsAndCredentialInjection() throws Exception {
        YandexMusic.validateAssetUri(URI.create("https://music-storage.yandex.net/audio"));
        assertThrows(IOException.class, () -> YandexMusic.validateAssetUri(URI.create("http://music-storage.yandex.net/audio")));
        assertThrows(IOException.class, () -> YandexMusic.validateAssetUri(URI.create("https://yandex.net.attacker.test/audio")));
        assertThrows(IOException.class, () -> YandexMusic.validateAssetUri(URI.create("https://user@music-storage.yandex.net/audio")));
        assertThrows(IOException.class, () -> YandexMusic.validateAssetUri(URI.create("https://music-storage.yandex.net:8080/audio")));
    }
    @Test void downloadSizeIsBounded() throws Exception {
        assertArrayEquals(new byte[3], YandexMusic.bounded(new ByteArrayInputStream(new byte[3]), 3));
        assertThrows(IOException.class, () -> YandexMusic.bounded(new ByteArrayInputStream(new byte[4]), 3));
    }
    @Test void bundledMp3AndOggProvidersDecodeSyntheticAudio() throws Exception {
        for (String resource : List.of("/tone.mp3", "/tone.ogg")) {
            try (InputStream in = new BufferedInputStream(Objects.requireNonNull(getClass().getResourceAsStream(resource)));
                 AudioInputStream encoded = AudioSystem.getAudioInputStream(in)) {
                AudioFormat source = encoded.getFormat();
                AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getSampleRate(), 16,
                    source.getChannels(), source.getChannels() * 2, source.getSampleRate(), false);
                try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, encoded)) {
                    byte[] audio = decoded.readAllBytes();
                    assertTrue(audio.length > 16000, resource + " should yield PCM samples");
                    boolean nonzero = false; for (byte b : audio) if (b != 0) { nonzero = true; break; }
                    assertTrue(nonzero, resource + " should contain a tone");
                }
            }
        }
    }
    @Test void wavDecoderProducesPcmWithoutNeedingAnAudioDevice() throws Exception {
        AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
        byte[] samples = new byte[44100 * 4];
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        try (AudioInputStream input = new AudioInputStream(new ByteArrayInputStream(samples), format, 44100)) {
            AudioSystem.write(input, AudioFileFormat.Type.WAVE, wav);
        }
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(wav.toByteArray())))) {
            assertEquals(44100, input.getFrameLength()); assertEquals(4, input.getFormat().getFrameSize());
            assertArrayEquals(samples, input.readAllBytes());
        }
    }
}
