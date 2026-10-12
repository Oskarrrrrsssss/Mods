package dev.invumusicplayer.client;

import dev.invumusicplayer.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PlayerHud {
    static final int TEXT = 0xffe6eee5, MUTED = 0xff9bad9f;
    public static void draw(GuiGraphicsExtractor g) { draw(g, false); }
    public static void preview(GuiGraphicsExtractor g) { draw(g, true); }
    private static void draw(GuiGraphicsExtractor g, boolean preview) {
        Player player = InvuMusicPlayerClient.player; Settings s = player.settings; Track track = player.current();
        if (!preview && (!s.hud || track == null || Minecraft.getInstance().gui.screen() != null)) return;
        if (track == null) track = new Track("preview", UiText.text("invumusicplayer.hud.preview_title"), UiText.text("invumusicplayer.hud.preview_artist"), 180000, null, null, "", "");
        boolean showCover=s.covers && !track.youtube();
        int width = showCover ? 244 : 196, height = 54;
        int x = Math.max(0, Math.min(s.hudX, (int) (g.guiWidth() / s.hudScale) - width));
        int y = Math.max(0, Math.min(s.hudY, (int) (g.guiHeight() / s.hudScale) - height));
        g.pose().pushMatrix(); g.pose().scale(s.hudScale);
        int bg = ((int) (s.opacity * 255) << 24) | s.background;
        g.fill(x, y, x + width, y + height, bg);
        g.fill(x, y, x + 2, y + height, 0xff000000 | s.accent);
        int tx = x + 10;
        if (showCover) { InvuMusicPlayerClient.cover.draw(g, x + 8, y + 7, 38, s.accent); tx = x + 54; }
        var font = Minecraft.getInstance().font;
        g.text(font, font.plainSubstrByWidth(track.title(), width - (tx - x) - 8), tx, y + 8, 0xff000000|s.textColor, false);
        g.text(font, font.plainSubstrByWidth(track.artist().isBlank() ? UiText.text("invumusicplayer.hud.local_music") : track.artist(), width - (tx - x) - 8), tx, y + 20, 0xb0000000|s.textColor, false);
        if(s.visualizer){float[] values=player.spectrum();int area=width-(tx-x)-8;
            for(int i=0;i<values.length;i++){int bx=tx+i*area/values.length;int h=Math.max(1,(int)(values[i]*17));g.fill(bx,y+49-h,bx+Math.max(1,area/values.length-2),y+49,0xff000000|s.accent);}
        }else{
        String clock = (player.loading() ? UiText.text("invumusicplayer.hud.loading") : player.playing() ? "> " : "|| ") + time(player.positionMillis()) + " / " + time(player.durationMillis());
        g.text(font, clock, tx, y + 33, 0xb0000000|s.textColor, false);
        int bar = width - (tx - x) - 8; g.fill(tx, y + 46, tx + bar, y + 48, 0xff324036);
        long duration = player.durationMillis();
        if (duration > 0) g.fill(tx, y + 46, tx + (int) (bar * Math.min(1, (double) player.positionMillis() / duration)), y + 48, 0xff000000 | s.accent);
        }
        g.pose().popMatrix();
    }
    public static String time(long millis) {
        if (millis <= 0) return "0:00";
        long seconds = millis / 1000; return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
