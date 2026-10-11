package dev.musicraft.client;

import dev.musicraft.core.Settings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final Settings s = MusicraftClient.player.settings;
    public SettingsScreen(Screen parent) { super(Component.literal("Настройки Musicraft")); this.parent = parent; }
    @Override protected void init() {
        int x = width / 2 - 155, y = 30;
        int step = Math.min(26, Math.max(17, (height - 66) / 8));
        option("Панель: " + yes(s.hud), x, y, () -> s.hud = !s.hud);
        option("Обложки: " + yes(s.covers), x + 160, y, () -> s.covers = !s.covers);
        option("3D-субтитры: " + yes(s.lyrics3d), x, y + step * 1, () -> s.lyrics3d = !s.lyrics3d);
        option("Только при беге: " + yes(s.sprintOnly), x + 160, y + step * 1, () -> s.sprintOnly = !s.sprintOnly);
        option("Громкость −", x, y + step * 2, () -> s.volume = Math.max(0, s.volume - .05f));
        option("Громкость +", x + 160, y + step * 2, () -> s.volume = Math.min(1, s.volume + .05f));
        option("Текст ближе", x, y + step * 3, () -> s.lyricDistance -= .25f);
        option("Текст дальше", x + 160, y + step * 3, () -> s.lyricDistance += .25f);
        option("Текст ниже", x, y + step * 4, () -> s.lyricHeight -= .1f);
        option("Текст выше", x + 160, y + step * 4, () -> s.lyricHeight += .1f);
        option("Текст меньше", x, y + step * 5, () -> s.lyricScale -= .0025f);
        option("Текст больше", x + 160, y + step * 5, () -> s.lyricScale += .0025f);
        option("Сдвиг текста −100 мс", x, y + step * 6, () -> s.lyricOffsetMillis -= 100);
        option("Сдвиг текста +100 мс", x + 160, y + step * 6, () -> s.lyricOffsetMillis += 100);
        option("Цвет: сменить", x, y + step * 7, () -> s.accent = s.accent == 0x82e39b ? 0xffc870 : s.accent == 0xffc870 ? 0x8bc8ef : 0x82e39b);
        option("Переместить панель", x + 160, y + step * 7, () -> minecraft.gui.setScreen(new HudEditor(this)), false);
        option("Назад", x + 80, height - 34, this::onClose, false);
    }
    private static String yes(boolean value) { return value ? "вкл" : "выкл"; }
    private void option(String title, int x, int y, Runnable action) { option(title, x, y, action, true); }
    private void option(String title, int x, int y, Runnable action, boolean refresh) {
        addRenderableWidget(Button.builder(Component.literal(title), b -> {
            action.run(); s.normalize(); MusicraftClient.player.saveSettings(); if (refresh) rebuildWidgets();
        }).bounds(x, y, 150, Math.min(20, Math.max(16, (height - 66) / 8 - 2))).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xef101813);
        g.text(font, "Настройки Musicraft", width / 2 - 155, 12, PlayerHud.TEXT, true);
        String values = "Громкость " + Math.round(s.volume * 100) + "%  •  " + String.format("%.1f м", s.lyricDistance)
            + "  •  Сдвиг " + s.lyricOffsetMillis + " мс";
        g.text(font, values, width / 2 - 155, height - 12, PlayerHud.MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { MusicraftClient.player.saveSettings(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
