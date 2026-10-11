package dev.musicraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.musicraft.core.Player;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;
import org.slf4j.LoggerFactory;

public final class MusicraftClient implements ClientModInitializer {
    public static Player player;
    public static final CoverTexture cover = new CoverTexture();
    @Override public void onInitializeClient() {
        try { player = new Player(FabricLoader.getInstance().getGameDir()); }
        catch (Exception e) { LoggerFactory.getLogger("musicraft").error("Musicraft could not initialize its config/library ({})", e.getClass().getSimpleName()); return; }
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("musicraft", "controls"));
        KeyMapping open = key("open", SDLScancode.SDL_SCANCODE_M, category);
        KeyMapping lyrics = key("lyrics", SDLScancode.SDL_SCANCODE_K, category);
        KeyMapping pause = key("pause", SDLScancode.SDL_SCANCODE_F8, category);
        KeyMapping next = key("next", SDLScancode.SDL_SCANCODE_F9, category);
        KeyMapping previous = key("previous", SDLScancode.SDL_SCANCODE_F7, category);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            cover.update(player.coverBytes());
            while (open.consumeClick()) client.setScreen(new PlayerScreen());
            while (lyrics.consumeClick()) {
                player.settings.lyrics3d = !player.settings.lyrics3d; player.saveSettings();
                if (client.player != null) client.player.displayClientMessage(Component.literal("3D-субтитры: " + (player.settings.lyrics3d ? "включены" : "выключены")), true);
            }
            while (pause.consumeClick()) player.togglePause();
            while (next.consumeClick()) player.next();
            while (previous.consumeClick()) player.previous();
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("musicraft", "player"), (graphics, delta) -> PlayerHud.draw(graphics));
        Lyrics3D.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { player.close(); cover.close(); });
    }
    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.musicraft." + name, InputConstants.Type.KEYBOARD, code, category));
    }
}
