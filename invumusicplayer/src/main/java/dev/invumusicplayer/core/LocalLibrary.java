package dev.invumusicplayer.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import javax.sound.sampled.*;

public final class LocalLibrary {
    private final Path directory;
    public LocalLibrary(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public Path directory() { return directory; }
    public List<Track> scan() throws IOException {
        Files.createDirectories(directory);
        List<Track> result = new ArrayList<>();
        try (Stream<Path> files = Files.walk(directory, 4)) {
            for (Path file : files.filter(Files::isRegularFile).filter(LocalLibrary::supported).toList()) {
                if (!file.toRealPath().startsWith(directory.toRealPath())) continue;
                String name = stem(file), artist = "", title = name;
                int split = name.indexOf(" - ");
                if (split > 0) { artist = name.substring(0, split); title = name.substring(split + 3); }
                Path metadata = sibling(file, ".json");
                if (Files.isRegularFile(metadata) && Files.size(metadata) < 65536) {
                    try {
                        JsonObject data = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
                        if (data.has("title")) title = data.get("title").getAsString();
                        if (data.has("artist")) artist = data.get("artist").getAsString();
                    } catch (RuntimeException ignored) { /* Broken sidecar must not hide the audio. */ }
                }
                Path cover = sibling(file, ".png");
                if (!Files.isRegularFile(cover)) cover = sibling(file, ".jpg");
                if (!Files.isRegularFile(cover) || !cover.toRealPath().startsWith(directory.toRealPath())) cover = null;
                long duration = 0;
                try {
                    AudioFileFormat audio = AudioSystem.getAudioFileFormat(file.toFile());
                    Object micros = audio.properties().get("duration");
                    if (micros instanceof Number n) duration = n.longValue() / 1000;
                    else if (audio.getFrameLength() > 0 && audio.getFormat().getFrameRate() > 0)
                        duration = (long) (audio.getFrameLength() * 1000.0 / audio.getFormat().getFrameRate());
                } catch (Exception ignored) {}
                result.add(new Track(file.toString(), title, artist, duration, file, cover, "", ""));
            }
        }
        result.sort(Comparator.comparing(Track::display, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }
    public Lyrics lyrics(Track track) throws IOException {
        Path lrc = sibling(track.file(), ".lrc");
        if (!Files.isRegularFile(lrc)) return Lyrics.EMPTY;
        if (!lrc.toRealPath().startsWith(directory.toRealPath()) || Files.size(lrc) > 2_000_000) return Lyrics.EMPTY;
        return Lyrics.parse(Files.readString(lrc));
    }
    private static boolean supported(Path file) { return file.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(mp3|wav|ogg)"); }
    private static String stem(Path file) { String s = file.getFileName().toString(); return s.substring(0, s.lastIndexOf('.')); }
    private static Path sibling(Path file, String suffix) { return file.resolveSibling(stem(file) + suffix); }
}
