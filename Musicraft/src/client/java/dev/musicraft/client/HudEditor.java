package dev.musicraft.client;

import dev.musicraft.core.Settings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudEditor extends Screen {
    private final Screen parent;
    private final Settings s = MusicraftClient.player.settings;
    private boolean dragging;
    private double offsetX, offsetY;
    public HudEditor(Screen parent) { super(Component.literal("Расположение плеера")); this.parent = parent; }
    @Override protected void init() {
        int x = width / 2 - 140, y = height - 72;
        button("− Масштаб", x, y, 88, () -> { scale(-.1f); });
        button("+ Масштаб", x + 96, y, 88, () -> { scale(.1f); });
        button("Прозрачность", x + 192, y, 88, () -> { s.opacity = s.opacity >= .99f ? .3f : s.opacity + .1f; s.normalize(); });
        button("Слева сверху", x, y + 24, 88, () -> corner(false, false));
        button("Справа сверху", x + 96, y + 24, 88, () -> corner(true, false));
        button("Готово", x + 192, y + 24, 88, this::onClose);
        button("Слева снизу", 8, 36, 88, () -> corner(false, true));
        button("Справа снизу", width - 96, 36, 88, () -> corner(true, true));
    }
    private void button(String title, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(title), b -> action.run()).bounds(x, y, w, 20).build());
    }
    private int panelWidth() { return s.covers ? 244 : 196; }
    private void scale(float change) {
        float x = s.hudX * s.hudScale, y = s.hudY * s.hudScale;
        s.hudScale += change; s.normalize();
        s.hudScale = Math.min(s.hudScale, (float) width / panelWidth());
        s.hudX = Math.round(x / s.hudScale); s.hudY = Math.round(y / s.hudScale); clamp();
    }
    private void clamp() {
        s.normalize(); s.hudX = Math.max(0, Math.min(s.hudX, (int) (width / s.hudScale) - panelWidth()));
        s.hudY = Math.max(0, Math.min(s.hudY, (int) (height / s.hudScale) - 54));
    }
    private void corner(boolean right, boolean bottom) {
        s.hudX = right ? (int) (width / s.hudScale) - panelWidth() - 8 : 8;
        s.hudY = bottom ? (int) (height / s.hudScale) - 62 : 8; clamp();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double x = event.x() / s.hudScale, y = event.y() / s.hudScale;
        if (x >= s.hudX && x <= s.hudX + panelWidth() && y >= s.hudY && y <= s.hudY + 54) {
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
        if (dragging) { dragging = false; MusicraftClient.player.saveSettings(); return true; }
        return super.mouseReleased(event);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x8008100a);
        g.text(font, "Перетащи панель мышью. Размер меняется кнопками ниже.", 8, 12, PlayerHud.TEXT, true);
        PlayerHud.preview(g);
        g.text(font, "Масштаб: " + Math.round(s.hudScale * 100) + "%", width / 2 - 140, height - 86, PlayerHud.MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { dragging = false; MusicraftClient.player.saveSettings(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
