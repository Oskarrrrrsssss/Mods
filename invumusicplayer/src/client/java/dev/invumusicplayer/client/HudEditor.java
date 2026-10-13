package dev.invumusicplayer.client;

import dev.invumusicplayer.core.Settings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudEditor extends Screen {
    private final Screen parent;
    private final Settings s = InvuMusicPlayerClient.player.settings;
    private boolean dragging;
    private double offsetX, offsetY;
    public HudEditor(Screen parent) { super(Component.translatable("invumusicplayer.hud_editor.title")); this.parent = parent; }
    @Override protected void init() {
        int x = width / 2 - 140, y = height - 72;
        button("invumusicplayer.hud_editor.scale_down", x, y, 88, () -> { scale(-.1f); });
        button("invumusicplayer.hud_editor.scale_up", x + 96, y, 88, () -> { scale(.1f); });
        button("invumusicplayer.hud_editor.opacity", x + 192, y, 88, () -> { s.opacity = s.opacity >= .99f ? .3f : s.opacity + .1f; s.normalize(); });
        button("invumusicplayer.hud_editor.top_left", x, y + 24, 88, () -> corner(false, false));
        button("invumusicplayer.hud_editor.top_right", x + 96, y + 24, 88, () -> corner(true, false));
        button("invumusicplayer.hud_editor.done", x + 192, y + 24, 88, this::onClose);
        button("invumusicplayer.hud_editor.bottom_left", 8, 36, 88, () -> corner(false, true));
        button("invumusicplayer.hud_editor.bottom_right", width - 96, 36, 88, () -> corner(true, true));
    }
    private void button(String key, int x, int y, int w, Runnable action) {
        addRenderableWidget(new ModernButton(x,y,w,20,Component.translatable(key),b->action.run()));
    }
    private int panelWidth() { return s.covers && (InvuMusicPlayerClient.player.current()==null || !InvuMusicPlayerClient.player.current().youtube()) ? 244 : 196; }
    private void scale(float change) {
        float x = s.hudX * s.hudScale, y = s.hudY * s.hudScale;
        s.hudScale += change; s.normalize();
        s.hudScale = Math.min(s.hudScale, (float) width / panelWidth());
        s.hudX = Math.round(x / s.hudScale); s.hudY = Math.round(y / s.hudScale); clamp();
    }
    private void clamp() {
        s.normalize(); s.hudX = Math.max(0, Math.min(s.hudX, (int) (width / s.hudScale) - panelWidth()));
        s.hudY = Math.max(0, Math.min(s.hudY, (int) (height / s.hudScale) - PlayerHud.panelHeight()));
    }
    private void corner(boolean right, boolean bottom) {
        s.hudX = right ? (int) (width / s.hudScale) - panelWidth() - 8 : 8;
        s.hudY = bottom ? (int) (height / s.hudScale) - PlayerHud.panelHeight()-8 : 8; clamp();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double x = event.x() / s.hudScale, y = event.y() / s.hudScale;
        if (x >= s.hudX && x <= s.hudX + panelWidth() && y >= s.hudY && y <= s.hudY + PlayerHud.panelHeight()) {
            dragging = true; offsetX = x - s.hudX; offsetY = y - s.hudY; return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            s.hudX = (int) (event.x() / s.hudScale - offsetX); s.hudY = (int) (event.y() / s.hudScale - offsetY); clamp(); return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) { dragging = false; InvuMusicPlayerClient.player.saveSettings(); return true; }
        return super.mouseReleased(event);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x8008100a);
        g.text(font, UiText.text("invumusicplayer.hud_editor.hint"), 8, 12, PlayerHud.TEXT, false);
        PlayerHud.preview(g);
        g.text(font, UiText.text("invumusicplayer.hud_editor.scale", Math.round(s.hudScale * 100)), width / 2 - 140, height - 86, PlayerHud.MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { dragging = false; InvuMusicPlayerClient.player.saveSettings(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent!=null && parent.isPauseScreen(); }
}
