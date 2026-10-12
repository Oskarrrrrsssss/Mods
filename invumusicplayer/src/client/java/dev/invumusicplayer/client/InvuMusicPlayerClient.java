package dev.invumusicplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.invumusicplayer.core.Player;
import dev.invumusicplayer.core.Messages;
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

public final class InvuMusicPlayerClient implements ClientModInitializer {
    public static Player player;
    private static boolean wasInWorld;
    public static final CoverTexture cover = new CoverTexture();
    @Override public void onInitializeClient() {
        Messages.setTranslator((key, arguments) -> Component.translatable(key, arguments).getString());
        try { player = new Player(FabricLoader.getInstance().getGameDir()); }
        catch (Exception e) {
            LoggerFactory.getLogger("invumusicplayer").error(
                "InvuMusicPlayer: config/library initialization failed / Не удалось открыть настройки или библиотеку ({})",
                e.getClass().getSimpleName());
            return;
        }
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("invumusicplayer", "controls"));
        KeyMapping open = key("open", SDLScancode.SDL_SCANCODE_M, category);
        KeyMapping lyrics = key("lyrics", SDLScancode.SDL_SCANCODE_K, category);
        KeyMapping pause = key("pause", SDLScancode.SDL_SCANCODE_F8, category);
        KeyMapping next = key("next", SDLScancode.SDL_SCANCODE_F9, category);
        KeyMapping previous = key("previous", SDLScancode.SDL_SCANCODE_F7, category);
        KeyMapping wave=key("wave",SDLScancode.SDL_SCANCODE_F10,category);
        KeyMapping like=key("like",SDLScancode.SDL_SCANCODE_UNKNOWN,category);
        KeyMapping dislike=key("dislike",SDLScancode.SDL_SCANCODE_UNKNOWN,category);
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof net.minecraft.client.gui.screens.TitleScreen) net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(
                new ModernButton(w-124,8,116,20,Component.literal("invumusicplayer"),b->client.gui.setScreen(new PlayerScreen())));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean inWorld=client.level!=null;
            if(wasInWorld&&!inWorld)player.suspend();wasInWorld=inWorld;
            cover.update(player.coverBytes());
            while (open.consumeClick()) client.gui.setScreen(new PlayerScreen());
            while (lyrics.consumeClick()) {
                player.settings.lyrics3d = !player.settings.lyrics3d; player.saveSettings();
                if (client.player != null) client.gui.hud.setOverlayMessage(Component.translatable(
                    player.settings.lyrics3d ? "invumusicplayer.overlay.lyrics_enabled" : "invumusicplayer.overlay.lyrics_disabled"), false);
            }
            while (pause.consumeClick()) player.togglePause();
            while (next.consumeClick()) player.next();
            while (previous.consumeClick()) player.previous();
            while(wave.consumeClick())player.startWave();
            while(like.consumeClick())player.rate(true);
            while(dislike.consumeClick())player.rate(false);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("invumusicplayer", "player"), (graphics, delta) -> PlayerHud.draw(graphics));
        Lyrics3D.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { player.close(); cover.close(); });
    }
    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.invumusicplayer." + name, InputConstants.Type.KEYBOARD, code, category));
    }
}
