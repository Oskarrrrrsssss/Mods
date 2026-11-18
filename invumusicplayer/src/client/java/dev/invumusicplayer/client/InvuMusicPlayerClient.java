package dev.invumusicplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.invumusicplayer.core.Player;
import dev.invumusicplayer.core.Messages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
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
        String category = "key.categories.invumusicplayer";
        KeyMapping open = key("open", GLFW.GLFW_KEY_M, category);
        KeyMapping pause = key("pause", GLFW.GLFW_KEY_F8, category);
        KeyMapping next = key("next", GLFW.GLFW_KEY_F9, category);
        KeyMapping previous = key("previous", GLFW.GLFW_KEY_F7, category);
        KeyMapping wave=key("wave",GLFW.GLFW_KEY_F10,category);
        KeyMapping shuffle=key("shuffle",GLFW.GLFW_KEY_UNKNOWN,category);
        KeyMapping like=key("like",GLFW.GLFW_KEY_UNKNOWN,category);
        KeyMapping dislike=key("dislike",GLFW.GLFW_KEY_UNKNOWN,category);
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof net.minecraft.client.gui.screens.TitleScreen||screen instanceof net.minecraft.client.gui.screens.PauseScreen) MenuPlayerPanel.install(screen,w,h);
            if(screen instanceof net.minecraft.client.gui.screens.options.OptionsScreen){
                var entry=new PlayerMenuButton(8,8,Math.min(150,w/2-20),Component.translatable("invumusicplayer.visibility.open_settings"),PlayerMenuButton.Icon.SETTINGS,true,b->client.setScreen(new SettingsScreen(screen)));
                entry.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("invumusicplayer.visibility.recovery")));
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(entry);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            cover.update(player.coverBytes());
            while (open.consumeClick()) client.setScreen(new PlayerScreen());
            while (pause.consumeClick()) player.togglePause();
            while (next.consumeClick()) player.next();
            while (previous.consumeClick()) player.previous();
            while(wave.consumeClick())player.startWave();
            while(shuffle.consumeClick())player.toggleShuffle();
            while(like.consumeClick())player.rate(true);
            while(dislike.consumeClick())player.rate(false);
        });
        HudRenderCallback.EVENT.register((graphics, delta) -> PlayerHud.draw(graphics));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { player.close(); cover.close(); });
    }
    private static KeyMapping key(String name, int code, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.invumusicplayer." + name, InputConstants.Type.KEYSYM, code, category));
    }
}
