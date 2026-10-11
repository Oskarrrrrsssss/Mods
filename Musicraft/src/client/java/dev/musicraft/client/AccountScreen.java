package dev.musicraft.client;

import dev.musicraft.core.TokenInput;
import java.io.IOException;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Credentials are entered on the user's PC and never echoed on screen. */
public final class AccountScreen extends Screen {
    private static final String HELP = "https://yandex-music.readthedocs.io/en/main/token.html";
    private final Screen parent;
    private EditBox token;
    private Button connect, disconnect;
    private String inputError = "";

    public AccountScreen(Screen parent) { super(Component.literal("Яндекс Музыка")); this.parent = parent; }

    @Override protected void init() {
        int x = width / 2 - 155, y = Math.max(24, height / 2 - 106);
        token = new EditBox(font, x, y + 72, 310, 20, Component.literal("Токен Яндекс Музыки"));
        token.setMaxLength(TokenInput.MAX_LENGTH + 2048);
        token.setFormatter((value, cursor) -> Component.literal("*".repeat(value.length())).getVisualOrderText());
        token.setResponder(value -> inputError = "");
        addRenderableWidget(token);
        connect = addRenderableWidget(Button.builder(Component.literal("Подключить"), b -> connect())
            .bounds(x, y + 100, 151, 20).build());
        disconnect = addRenderableWidget(Button.builder(Component.literal("Отключить"), b -> {
            token.setValue(""); inputError = ""; MusicraftClient.player.disconnect();
        }).bounds(x + 159, y + 100, 151, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Открыть инструкцию получения токена"),
            b -> Util.getPlatform().openUri(HELP)).bounds(x, y + 128, 310, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose())
            .bounds(x, y + 196, 310, 20).build());
        setInitialFocus(token);
        updateButtons();
    }

    private void connect() {
        try {
            String candidate = TokenInput.normalize(token.getValue());
            token.setValue(""); inputError = "";
            MusicraftClient.player.connect(candidate);
        } catch (IOException e) {
            token.setValue(""); inputError = TokenInput.INVALID;
        }
        updateButtons();
    }

    @Override public void tick() { super.tick(); updateButtons(); }

    private void updateButtons() {
        if (connect == null || token == null) return;
        boolean pending = MusicraftClient.player.connecting();
        connect.active = !pending && !token.getValue().isBlank();
        connect.setMessage(Component.literal(pending ? "Проверка аккаунта…" : "Подключить"));
        disconnect.active = pending || MusicraftClient.player.connected();
        disconnect.setMessage(Component.literal(pending ? "Отменить вход" : "Отключить"));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xef101813);
        int x = width / 2 - 155, y = Math.max(24, height / 2 - 106);
        g.text(font, "Яндекс Музыка", x, y, PlayerHud.TEXT, true);
        g.text(font, "Музыка и Моя волна играют внутри Minecraft.", x, y + 18, PlayerHud.MUTED, false);
        g.text(font, "Вставь токен своего аккаунта. Пароль не нужен.", x, y + 32, PlayerHud.MUTED, false);
        g.text(font, "Токен хранится зашифрованным на этом ПК.", x, y + 46, PlayerHud.MUTED, false);
        g.text(font, "Токен или адрес страницы с access_token:", x, y + 60, PlayerHud.TEXT, false);
        String error = inputError.isBlank() ? MusicraftClient.player.accountError() : inputError;
        String status = error.isBlank() ? MusicraftClient.player.accountStatus() : error;
        int color = error.isBlank() ? PlayerHud.MUTED : 0xfff28b82;
        drawWrapped(g, status, x, y + 158, 310, color);
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color) {
        // Two lines keep service errors legible without displacing controls.
        for (int line = 0; line < 2 && !text.isEmpty(); line++) {
            String visible = font.plainSubstrByWidth(text, maxWidth);
            if (visible.length() < text.length()) {
                int space = visible.lastIndexOf(' ');
                if (space > 0) visible = visible.substring(0, space);
            }
            g.text(font, visible, x, y + line * 11, color, false);
            text = text.substring(visible.length()).stripLeading();
        }
    }

    @Override public void removed() { if (token != null) token.setValue(""); super.removed(); }
    @Override public void onClose() { if (token != null) token.setValue(""); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
