package dev.invumusicplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.invumusicplayer.core.Player;
import dev.invumusicplayer.core.Messages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;
import org.slf4j.LoggerFactory;

public final class InvuMusicPlayerClient implements ClientModInitializer {
    public static Player player;
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
        KeyMapping pause = key("pause", SDLScancode.SDL_SCANCODE_F8, category);
        KeyMapping next = key("next", SDLScancode.SDL_SCANCODE_F9, category);
        KeyMapping previous = key("previous", SDLScancode.SDL_SCANCODE_F7, category);
        KeyMapping wave=key("wave",SDLScancode.SDL_SCANCODE_F10,category);
        KeyMapping shuffle=key("shuffle",SDLScancode.SDL_SCANCODE_UNKNOWN,category);
        KeyMapping like=key("like",SDLScancode.SDL_SCANCODE_UNKNOWN,category);
        KeyMapping dislike=key("dislike",SDLScancode.SDL_SCANCODE_UNKNOWN,category);
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof net.minecraft.client.gui.screens.TitleScreen||screen instanceof net.minecraft.client.gui.screens.PauseScreen) MenuPlayerPanel.install(screen,w,h);
            if(screen instanceof net.minecraft.client.gui.screens.options.OptionsScreen){
                var entry=new PlayerMenuButton(8,8,Math.min(150,w/2-20),Component.translatable("invumusicplayer.visibility.open_settings"),PlayerMenuButton.Icon.SETTINGS,true,b->client.gui.setScreen(new SettingsScreen(screen)));
                entry.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("invumusicplayer.visibility.recovery")));
                net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(entry);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            cover.update(player.coverBytes());
            while (open.consumeClick()) client.gui.setScreen(new PlayerScreen());
            while (pause.consumeClick()) player.togglePause();
            while (next.consumeClick()) player.next();
            while (previous.consumeClick()) player.previous();
            while(wave.consumeClick())player.startWave();
            while(shuffle.consumeClick())player.toggleShuffle();
            while(like.consumeClick())player.rate(true);
            while(dislike.consumeClick())player.rate(false);
        });
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("invumusicplayer", "player"), (graphics, delta) -> PlayerHud.draw(graphics));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { player.close(); cover.close(); });
    }
    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.invumusicplayer." + name, InputConstants.Type.KEYBOARD, code, category));
    }
}
