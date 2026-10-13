package dev.invumusicplayer.client;

import dev.invumusicplayer.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PlayerHud {
    private static final AudioVisualizer visualizer = new AudioVisualizer();
    static final int TEXT = 0xffe6eee5, MUTED = 0xff9bad9f;
    static int panelHeight(){return InvuMusicPlayerClient.player.settings.visualizer?70:54;}
    public static void draw(GuiGraphicsExtractor g) { draw(g, false); }
    public static void preview(GuiGraphicsExtractor g) { draw(g, true); }
    public static boolean isVisible() {
        var client=Minecraft.getInstance();var player=InvuMusicPlayerClient.player;
        return player!=null && client.level!=null && player.settings.playersVisible && player.settings.hud && player.current()!=null
            && client.gui.screen()==null && (!client.gui.hud.isHidden() || player.settings.hudWhileHidden);
    }
    private static void draw(GuiGraphicsExtractor g, boolean preview) {
        Player player = InvuMusicPlayerClient.player; Settings s = player.settings; Track track = player.current();
        if (!preview && !isVisible()) return;
        if (track == null) track = new Track("preview", UiText.text("invumusicplayer.hud.preview_title"), UiText.text("invumusicplayer.hud.preview_artist"), 180000, null, null, "", "");
        boolean showCover=s.covers && !track.youtube();
        int width = showCover ? 244 : 196, height = panelHeight();
        int x = Math.max(0, Math.min(s.hudX, (int) (g.guiWidth() / s.hudScale) - width));
        int y = Math.max(0, Math.min(s.hudY, (int) (g.guiHeight() / s.hudScale) - height));
        g.pose().pushMatrix(); g.pose().scale(s.hudScale);
        int accent=PlayerMenuButton.themeAccent();
        int bg = ((int) (s.opacity * 255) << 24) | PlayerMenuButton.themeColor();
        g.fill(x, y, x + width, y + height, bg);
        g.fill(x, y, x + 2, y + height, 0xff000000 | accent);
        int tx = x + 10;
        if (showCover) { InvuMusicPlayerClient.cover.draw(g, x + 8, y + 7, 38, accent); tx = x + 54; }
        var font = Minecraft.getInstance().font;
        g.text(font, font.plainSubstrByWidth(track.title(), width - (tx - x) - 8), tx, y + 8, 0xff000000|s.textColor, false);
        g.text(font, font.plainSubstrByWidth(track.artist().isBlank() ? UiText.text("invumusicplayer.hud.local_music") : track.artist(), width - (tx - x) - 8), tx, y + 20, 0xb0000000|s.textColor, false);
        String clock=(player.loading()?UiText.text("invumusicplayer.hud.loading"):player.playing()?"> ":"|| ")+time(player.positionMillis())+" / "+time(player.durationMillis());
        g.text(font,clock,tx,y+33,0xb0000000|s.textColor,false);
        int bar=width-(tx-x)-8;g.fill(tx,y+46,tx+bar,y+48,0xff324036);
        if(player.durationMillis()>0)g.fill(tx,y+46,tx+(int)(bar*Math.min(1,(double)player.positionMillis()/player.durationMillis())),y+48,0xff000000|accent);
        if(s.visualizer)visualizer.draw(g,player.spectrum(),tx,y+51,bar,15,accent);
        g.pose().popMatrix();
    }
    static int backgroundColor(){return PlayerMenuButton.themeColor();}
    public static String time(long millis) {
        if (millis <= 0) return "0:00";
        long seconds = millis / 1000; return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
