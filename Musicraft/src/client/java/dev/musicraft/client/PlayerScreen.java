package dev.musicraft.client;

import dev.musicraft.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class PlayerScreen extends Screen {
    private final Player player = MusicraftClient.player;
    private List<Track> seen = List.of();
    private int page;
    private String query = "";
    private EditBox search;
    private int left, top, panelWidth, panelHeight, listWidth, rows;
    private Button pause, serviceStatus;
    public PlayerScreen() { super(Component.literal("Musicraft")); }
    @Override protected void init() {
        panelWidth = Math.min(620, width - 20); panelHeight = Math.min(360, height - 20);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        listWidth = (panelWidth - 32) / 2; rows = Math.max(1, (panelHeight - 138) / 24);
        button("Локальные", left + 10, top + 32, (listWidth - 4) / 2, () -> { page = 0; player.refreshLocal(); });
        button("Моя волна", left + 14 + (listWidth - 4) / 2, top + 32, (listWidth - 4) / 2, () -> { if (player.connected()) player.startWave(); else minecraft.gui.setScreen(new AccountScreen(this)); });
        search = new EditBox(font, left + 10, top + 58, listWidth - 44, 20, Component.literal("Поиск трека"));
        search.setMaxLength(256); search.setValue(query); search.setResponder(value -> query = value); addRenderableWidget(search);
        button("Найти", left + listWidth - 30, top + 58, 40, () -> { page = 0; if (player.connected()) player.search(query); else minecraft.gui.setScreen(new AccountScreen(this)); });
        seen = player.items(); int pages = Math.max(1, (seen.size() + rows - 1) / rows); page = Math.max(0, Math.min(page, pages - 1));
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row; if (index >= seen.size()) break;
            Track t = seen.get(index); List<Track> selected = seen;
            Button entry = button(font.plainSubstrByWidth(t.display(), listWidth - 12), left + 10, top + 84 + row * 24,
                listWidth, () -> player.choose(selected, index));
            entry.setTooltip(Tooltip.create(Component.literal(t.display())));
        }
        int bottom = top + panelHeight - 52;
        button("<", left + 10, bottom, 24, () -> { page--; rebuildWidgets(); });
        button(">", left + listWidth - 14, bottom, 24, () -> { page++; rebuildWidgets(); });
        int right = left + listWidth + 22, rightWidth = panelWidth - listWidth - 32;
        button("<<", right, bottom - 28, 36, player::previous);
        pause = button(player.playing() ? "Пауза" : "Играть", right + 40, bottom - 28, rightWidth - 80, player::togglePause);
        button(">>", right + rightWidth - 36, bottom - 28, 36, player::next);
        button("-10 с", right, bottom, (rightWidth - 8) / 3, () -> player.seek(player.positionMillis() - 10000));
        button("Стоп", right + (rightWidth - 8) / 3 + 4, bottom, (rightWidth - 8) / 3, player::stop);
        button("+10 с", right + 2 * ((rightWidth - 8) / 3 + 4), bottom, (rightWidth - 8) / 3, () -> player.seek(player.positionMillis() + 10000));
        int footer = top + panelHeight - 26;
        serviceStatus = button("", left + 110, top + 6, panelWidth - 120, () -> {});
        updateServiceStatus();
        button("Аккаунт", left + 10, footer, 74, () -> minecraft.gui.setScreen(new AccountScreen(this)));
        button("Настройки", left + 88, footer, 84, () -> minecraft.gui.setScreen(new SettingsScreen(this)));
        button("HUD", left + 176, footer, 44, () -> minecraft.gui.setScreen(new HudEditor(this)));
        button("Готово", left + panelWidth - 74, footer, 64, this::onClose);
    }
    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, Math.max(20, w), 20).build());
    }
    @Override public void tick() {
        if (seen != player.items()) rebuildWidgets();
        updateServiceStatus();
        if (pause != null) pause.setMessage(Component.literal(player.loading() ? "Загрузка" : player.playing() ? "Пауза" : "Играть"));
    }
    private void updateServiceStatus() {
        if (serviceStatus == null) return;
        String status = player.error().isBlank() ? player.status() : player.error();
        serviceStatus.setMessage(Component.literal(font.plainSubstrByWidth(status, serviceStatus.getWidth() - 12)));
        serviceStatus.setTooltip(Tooltip.create(Component.literal(status)));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x9a080d0a);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xff111b15);
        g.fill(left, top, left + panelWidth, top + 2, 0xff000000 | player.settings.accent);
        g.text(font, "MUSICRAFT", left + 10, top + 12, 0xff000000 | player.settings.accent, true);
        int right = left + listWidth + 22, rightWidth = panelWidth - listWidth - 32;
        int y = top + 40;
        if (player.settings.covers && panelHeight >= 215) {
            int size = panelHeight < 270 ? 42 : 54;
            MusicraftClient.cover.draw(g, right, y, size, player.settings.accent); y += size + 8;
        }
        Track track = player.current();
        g.text(font, font.plainSubstrByWidth(track == null ? "Выбери трек" : track.title(), rightWidth), right, y, PlayerHud.TEXT, true);
        g.text(font, font.plainSubstrByWidth(track == null ? "Музыка для твоего мира" : track.artist(), rightWidth), right, y + 14, PlayerHud.MUTED, false);
        g.text(font, PlayerHud.time(player.positionMillis()) + " / " + PlayerHud.time(player.durationMillis()), right, y + 32, PlayerHud.MUTED, false);
        int barY = y + 47; g.fill(right, barY, right + rightWidth, barY + 3, 0xff35463b);
        if (player.durationMillis() > 0) g.fill(right, barY, right + (int) (rightWidth * Math.min(1, (double) player.positionMillis() / player.durationMillis())), barY + 3, 0xff000000 | player.settings.accent);
        if (seen.isEmpty()) g.text(font, "Нет треков. Обнови библиотеку.", left + 12, top + 88, PlayerHud.MUTED, false);
        g.text(font, (page + 1) + " / " + Math.max(1, (seen.size() + rows - 1) / rows), left + 44, top + panelHeight - 46, PlayerHud.MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    @Override public boolean isPauseScreen() { return false; }
}
