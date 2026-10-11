package dev.musicraft.client;

import dev.musicraft.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PlayerHud {
    static final int TEXT = 0xffe6eee5, MUTED = 0xff9bad9f;
    public static void draw(GuiGraphicsExtractor g) { draw(g, false); }
    public static void preview(GuiGraphicsExtractor g) { draw(g, true); }
    private static void draw(GuiGraphicsExtractor g, boolean preview) {
        Player player = MusicraftClient.player; Settings s = player.settings; Track track = player.current();
        if (!preview && (!s.hud || track == null || Minecraft.getInstance().screen != null)) return;
        if (track == null) track = new Track("preview", "Название трека", "Исполнитель", 180000, null, null, "", "");
        int width = s.covers ? 244 : 196, height = 54;
        int x = Math.max(0, Math.min(s.hudX, (int) (g.guiWidth() / s.hudScale) - width));
        int y = Math.max(0, Math.min(s.hudY, (int) (g.guiHeight() / s.hudScale) - height));
        g.pose().pushMatrix(); g.pose().scale(s.hudScale);
        int bg = ((int) (s.opacity * 255) << 24) | 0x101813;
        g.fill(x, y, x + width, y + height, bg);
        g.fill(x, y, x + 2, y + height, 0xff000000 | s.accent);
        int tx = x + 10;
        if (s.covers) { MusicraftClient.cover.draw(g, x + 8, y + 7, 38, s.accent); tx = x + 54; }
        var font = Minecraft.getInstance().font;
        g.text(font, font.plainSubstrByWidth(track.title(), width - (tx - x) - 8), tx, y + 8, TEXT, true);
        g.text(font, font.plainSubstrByWidth(track.artist().isBlank() ? "Локальная музыка" : track.artist(), width - (tx - x) - 8), tx, y + 20, MUTED, false);
        String clock = (player.loading() ? "Загрузка…" : player.playing() ? "> " : "|| ") + time(player.positionMillis()) + " / " + time(player.durationMillis());
        g.text(font, clock, tx, y + 33, MUTED, false);
        int bar = width - (tx - x) - 8; g.fill(tx, y + 46, tx + bar, y + 48, 0xff324036);
        long duration = player.durationMillis();
        if (duration > 0) g.fill(tx, y + 46, tx + (int) (bar * Math.min(1, (double) player.positionMillis() / duration)), y + 48, 0xff000000 | s.accent);
        g.pose().popMatrix();
    }
    public static String time(long millis) {
        if (millis <= 0) return "0:00";
        long seconds = millis / 1000; return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
