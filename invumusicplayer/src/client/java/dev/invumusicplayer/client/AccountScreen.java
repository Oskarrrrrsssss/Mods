package dev.invumusicplayer.client;

import dev.invumusicplayer.core.TokenInput;
import java.io.IOException;
import com.mojang.blaze3d.Blaze3D;
import java.net.URI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Credentials are entered on the user's PC and never echoed on screen. */
public final class AccountScreen extends Screen {
    private static final String HELP = "https://github.com/Oskarrrrrsssss/Mods/tree/codex/musicraft-playback/invumusicplayer#readme";
    public static Screen open(Screen parent){return InvuMusicPlayerClient.player.spotifySelected()?new SpotifyAccountScreen(parent):new AccountScreen(parent);}
    private final Screen parent;
    private EditBox token;
    private Button connect, disconnect;
    private boolean invalidInput;

    public AccountScreen(Screen parent) { super(Component.translatable("invumusicplayer.account.title")); this.parent = parent; }

    @Override protected void init() {
        int x = width / 2 - 155, y = Math.max(24, height / 2 - 106);
        var yandex=addRenderableWidget(new ModernButton(x,y+18,151,20,Component.translatable("invumusicplayer.service.yandex"),b->{}));yandex.selected(()->true);
        addRenderableWidget(new ModernButton(x+159,y+18,151,20,Component.translatable("invumusicplayer.service.spotify"),b->{InvuMusicPlayerClient.player.setMusicService("spotify");minecraft.gui.setScreen(new SpotifyAccountScreen(parent));}));
        token = new EditBox(font, x, y + 98, 310, 20, Component.translatable("invumusicplayer.account.token_field")) {
            @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
                return Component.translatable("invumusicplayer.account.token_narration");
            }
        };
        token.setMaxLength(TokenInput.MAX_LENGTH + 2048);
        token.addFormatter((value, cursor) -> Component.literal("*".repeat(value.length())).getVisualOrderText());
        token.setResponder(value -> invalidInput = false);
        addRenderableWidget(token);
        connect = addRenderableWidget(new ModernButton(x, y + 122, 151, 20, Component.translatable("invumusicplayer.account.connect"), b -> connect()));
        disconnect = addRenderableWidget(new ModernButton(x + 159, y + 122, 151, 20, Component.translatable("invumusicplayer.account.disconnect"), b -> {
            token.setValue(""); invalidInput = false; InvuMusicPlayerClient.player.disconnect();
        }));
        addRenderableWidget(new ModernButton(x,y+148,310,20,Component.translatable("invumusicplayer.account.help"),b->Blaze3D.openUri(URI.create(HELP))));
        addRenderableWidget(new ModernButton(x, y + 196, 310, 20, Component.translatable("invumusicplayer.account.back"), b -> onClose()));
        setInitialFocus(token);
        updateButtons();
    }

    private void connect() {
        try {
            String candidate = TokenInput.normalize(token.getValue());
            token.setValue(""); invalidInput = false;
            InvuMusicPlayerClient.player.connect(candidate);
        } catch (IOException e) {
            token.setValue(""); invalidInput = true;
        }
        updateButtons();
    }

    @Override public void tick() { super.tick(); updateButtons(); }

    private void updateButtons() {
        if (connect == null || token == null) return;
        boolean pending = InvuMusicPlayerClient.player.connecting();
        connect.active = !pending && !token.getValue().isBlank();
        connect.setMessage(Component.translatable(pending ? "invumusicplayer.account.checking" : "invumusicplayer.account.connect"));
        disconnect.active = pending || InvuMusicPlayerClient.player.connected();
        disconnect.setMessage(Component.translatable(pending ? "invumusicplayer.account.cancel" : "invumusicplayer.account.disconnect"));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xf00c1220);
        int x = width / 2 - 155, y = Math.max(24, height / 2 - 106);
        g.text(font, UiText.text("invumusicplayer.account.title"), x, y, PlayerHud.TEXT, false);
        g.text(font, UiText.text("invumusicplayer.account.playback_hint"), x, y + 44, PlayerHud.MUTED, false);
        g.text(font, UiText.text("invumusicplayer.account.token_hint"), x, y + 58, PlayerHud.MUTED, false);
        g.text(font, UiText.text("invumusicplayer.account.storage_hint"), x, y + 72, PlayerHud.MUTED, false);
        g.text(font, UiText.text("invumusicplayer.account.token_prompt"), x, y + 86, PlayerHud.TEXT, false);
        String error = invalidInput ? UiText.text("invumusicplayer.error.token_invalid") : InvuMusicPlayerClient.player.accountError();
        String status = error.isBlank() ? InvuMusicPlayerClient.player.accountStatus() : error;
        int color = error.isBlank() ? PlayerHud.MUTED : 0xfff28b82;
        drawWrapped(g, status, x, y + 168, 310, color);
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
    @Override public boolean isPauseScreen() { return parent!=null&&parent.isPauseScreen(); }
}
