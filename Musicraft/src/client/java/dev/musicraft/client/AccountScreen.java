package dev.musicraft.client;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AccountScreen extends Screen {
    private final Screen parent;
    private EditBox token;
    public AccountScreen(Screen parent) { super(Component.literal("Яндекс Музыка")); this.parent = parent; }
    @Override protected void init() {
        int x = width / 2 - 140, y = height / 2 - 20;
        token = new EditBox(font, x, y, 280, 20, Component.literal("Токен Яндекс Музыки"));
        token.setMaxLength(4096);
        token.setFormatter((value, cursor) -> Component.literal("*".repeat(value.length())).getVisualOrderText());
        addRenderableWidget(token);
        addRenderableWidget(Button.builder(Component.literal("Подключить"), b -> {
            MusicraftClient.player.connect(token.getValue()); token.setValue("");
        }).bounds(x, y + 28, 136, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отключить аккаунт"), b -> MusicraftClient.player.disconnect()).bounds(x + 144, y + 28, 136, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Как получить токен"), b -> Util.getPlatform().openUri("https://yandex-music.readthedocs.io/en/main/token.html")).bounds(x, y + 56, 280, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose()).bounds(x, y + 84, 280, 20).build());
        setInitialFocus(token);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xef101813);
        int x = width / 2 - 140, y = height / 2 - 20;
        g.text(font, "Яндекс Музыка", x, y - 60, PlayerHud.TEXT, true);
        g.text(font, "Вставь токен своего аккаунта. Пароль не нужен.", x, y - 42, PlayerHud.MUTED, false);
        g.text(font, "Авторизация хранится на этом ПК через Windows.", x, y - 28, PlayerHud.MUTED, false);
        String status = MusicraftClient.player.error().isBlank() ? MusicraftClient.player.status() : MusicraftClient.player.error();
        g.text(font, font.plainSubstrByWidth(status, 280), x, y + 116, PlayerHud.MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { token.setValue(""); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
