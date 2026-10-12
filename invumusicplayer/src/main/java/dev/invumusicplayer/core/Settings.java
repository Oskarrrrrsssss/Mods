package dev.invumusicplayer.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class Settings {
    public boolean hud = true, covers = true, lyrics3d = true, sprintOnly = false;
    public float volume = 0.7f, hudScale = 1f, opacity = 0.85f;
    public float lyricDistance = 3f, lyricHeight = -0.25f, lyricScale = 0.025f;
    public int hudX = 12, hudY = 12, lyricOffsetMillis = 0;
    public int accent = 0x82e39b, background = 0x111827, textColor = 0xf1f5f9;
    public boolean visualizer = false, repeat = false;
    public String waveMood = "all", waveDiversity = "default", waveLanguage = "any";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public void normalize() {
        volume = finiteClamp(volume, 0, 1, .7f);
        hudScale = finiteClamp(hudScale, .5f, 2f, 1f);
        opacity = finiteClamp(opacity, .2f, 1, .85f);
        lyricDistance = finiteClamp(lyricDistance, 1.5f, 8f, 3f);
        lyricHeight = finiteClamp(lyricHeight, -2, 2, -.25f);
        lyricScale = finiteClamp(lyricScale, .01f, .06f, .025f);
        hudX = Math.max(0, Math.min(10000, hudX)); hudY = Math.max(0, Math.min(10000, hudY));
        lyricOffsetMillis = Math.max(-10000, Math.min(10000, lyricOffsetMillis));
        accent &= 0xffffff; background &= 0xffffff; textColor &= 0xffffff;
        if (!java.util.Set.of("all", "fun", "sad", "active", "calm").contains(waveMood == null ? "" : waveMood)) waveMood = "all";
        if (!java.util.Set.of("default", "favorite", "popular", "discover").contains(waveDiversity == null ? "" : waveDiversity)) waveDiversity = "default";
        if (!java.util.Set.of("any", "russian", "not-russian").contains(waveLanguage == null ? "" : waveLanguage)) waveLanguage = "any";
    }
    private static float finiteClamp(float n, float lo, float hi, float fallback) {
        return Float.isFinite(n) ? Math.max(lo, Math.min(hi, n)) : fallback;
    }
    public static Settings load(Path path) throws IOException {
        try { return readSettings(path); }
        catch (IOException | SecurityException e) { throw Messages.error("invumusicplayer.error.settings_read"); }
    }
    private static Settings readSettings(Path path) throws IOException {
        if (!Files.exists(path)) return new Settings();
        try {
            Settings settings = JSON.fromJson(Files.readString(path), Settings.class);
            if (settings == null) throw new IllegalArgumentException("Empty config");
            settings.normalize(); return settings;
        } catch (RuntimeException e) {
            Path backup = path.resolveSibling(path.getFileName() + ".invalid-" + System.currentTimeMillis());
            Files.move(path, backup); return new Settings();
        }
    }
    public void save(Path path) throws IOException {
        normalize(); Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), "invumusicplayer-", ".tmp");
        try {
            Files.writeString(tmp, JSON.toJson(this), StandardCharsets.UTF_8);
            try { Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
