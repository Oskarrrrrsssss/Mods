package dev.invumusicplayer.client;

import dev.invumusicplayer.core.Player;
import dev.invumusicplayer.core.SmokePlayerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.PauseScreen;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import org.lwjgl.sdl.SDLScancode;

/** Runs against the actual client, UI, input registration and world rendering; ships in a separate test mod. */
@SuppressWarnings("UnstableApiUsage")
public final class InvuMusicPlayerClientSmokeTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        SmokePlayerFactory.verifyBundledCodecs();
        Player original = context.computeOnClient(client -> {
            check(InvuMusicPlayerClient.player != null, "InvuMusicPlayer client entrypoint did not initialize the player");
            Set<String> names = Arrays.stream(client.options.keyMappings).map(KeyMapping::getName).collect(Collectors.toSet());
            for (String action : new String[] {"open", "pause", "next", "previous", "wave", "like", "dislike"}) {
                check(names.contains("key.invumusicplayer." + action), "Missing registered key mapping: " + action);
            }
            return InvuMusicPlayerClient.player;
        });
        Path fixtureDirectory = FabricLoader.getInstance().getGameDir();
        String originalLanguage = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        final Player fixture;
        try { fixture = SmokePlayerFactory.create(fixtureDirectory); }
        catch (IOException error) { throw new AssertionError("Cannot create synthetic local music fixture", error); }
        context.runOnClient(client -> InvuMusicPlayerClient.player = fixture);
        try {
            selectLanguage(context, "en_us");
            checkTranslation(context, "invumusicplayer.player.account", "Account");
            checkTranslation(context, "invumusicplayer.player.wave", "My Wave");
            checkTranslation(context, "invumusicplayer.player.settings", "Settings");
            context.waitFor(client -> fixture.items().size() == 2);
            context.runOnClient(client -> {
                fixture.settings.covers = true;
                fixture.choose(fixture.items(), 0);
            });
            context.waitFor(client -> fixture.playing() && !fixture.loading()
                && fixture.coverBytes().length > 0);
            check(fixture.current().title().equals("First"), "The local library did not load its first track");
            check(fixture.settings.visualizer,"The equalizer is not enabled by default");
            check(hasSignal(fixture.spectrum()),"Synthetic PCM did not reach the equalizer");

            context.getInput().resizeWindow(1280, 720);
            context.runOnClient(client -> { client.options.guiScale().set(3); client.resizeGui(); });
            context.waitForScreen(net.minecraft.client.gui.screens.TitleScreen.class);
            context.takeScreenshot("invu-en-title-screen-entry");context.clickScreenButton("invumusicplayer");context.waitForScreen(PlayerScreen.class);
            context.clickScreenButton("invumusicplayer.player.settings");context.waitForScreen(SettingsScreen.class);
            context.takeScreenshot("invu-en-settings-without-world");context.clickScreenButton("invumusicplayer.settings.back");
            context.clickScreenButton("invumusicplayer.player.done");context.waitForScreen(net.minecraft.client.gui.screens.TitleScreen.class);
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                context.waitTicks(4);
                context.takeScreenshot("invumusicplayer-en-world-hud-and-equalizer");
                checkPauseAndHiddenHud(context,fixture);
                openPlayerWithHotkey(context);
                context.waitFor(client -> InvuMusicPlayerClient.cover.ready() && ((PlayerScreen)client.gui.screen()).readyArtworkCount() == 2);
                context.takeScreenshot("invumusicplayer-en-player-with-cover");
                context.clickScreenButton("invumusicplayer.player.library");context.waitForScreen(LibraryScreen.class);
                context.takeScreenshot("invu-en-queue-library");context.clickScreenButton("invumusicplayer.player.done");
                context.waitForScreen(PlayerScreen.class);

                context.clickScreenButton("invumusicplayer.player.account");
                context.waitForScreen(AccountScreen.class);
                EditBox englishTokenInput = enterSyntheticToken(context);
                context.takeScreenshot("invumusicplayer-en-account-disconnected");
                context.clickScreenButton("invumusicplayer.account.back");
                context.waitForScreen(PlayerScreen.class);
                check(context.computeOnClient(client -> englishTokenInput.getValue().isEmpty()),
                    "English account screen did not clear its token on close");

                context.clickScreenButton("invumusicplayer.player.settings");
                context.waitForScreen(SettingsScreen.class);
                context.clickScreenButton("invumusicplayer.tabs.appearance");
                clickToggle(context, "invumusicplayer.settings.covers", true);
                check(!fixture.settings.covers, "Cover visibility setting did not change");
                context.takeScreenshot("invumusicplayer-en-settings");
                context.clickScreenButton("invumusicplayer.settings.back");
                context.waitForScreen(PlayerScreen.class);
                context.takeScreenshot("invumusicplayer-en-player-text-only");

                context.clickScreenButton("invumusicplayer.player.hud");
                context.waitForScreen(HudEditor.class);
                context.clickScreenButton("invumusicplayer.hud_editor.scale_up");
                check(fixture.settings.hudScale > 1, "HUD scaling button did not change the scale");
                context.clickScreenButton("invumusicplayer.hud_editor.bottom_right");
                check(fixture.settings.hudX > 12 && fixture.settings.hudY > 12, "HUD corner button did not move the panel");
                context.takeScreenshot("invumusicplayer-en-hud-editor");
                context.clickScreenButton("invumusicplayer.hud_editor.done");
                context.waitForScreen(PlayerScreen.class);
                context.clickScreenButton("invumusicplayer.player.done");
                context.waitForScreen(null);
                context.takeScreenshot("invumusicplayer-en-world-custom-hud");

                press(context, "pause");
                context.waitFor(client -> !fixture.playing());
                check(!hasSignal(fixture.spectrum()),"Paused player still reports audible signal");
                press(context, "pause");
                context.waitFor(client -> fixture.playing());
                press(context, "next");
                context.waitFor(client -> fixture.current() != null && fixture.current().title().equals("Second")
                    && fixture.playing() && !fixture.loading());
                press(context, "previous");
                context.waitFor(client -> fixture.current() != null && fixture.current().title().equals("First")
                    && fixture.playing() && !fixture.loading());
                // Reload real language resources and navigate the same client in Russian.
                selectLanguage(context, "ru_ru");
                checkTranslation(context, "invumusicplayer.player.account", "Аккаунт");
                checkTranslation(context, "invumusicplayer.player.wave", "Моя волна");
                checkTranslation(context, "invumusicplayer.player.settings", "Настройки");
                context.getInput().pressKey(SDLScancode.SDL_SCANCODE_ESCAPE);context.waitForScreen(PauseScreen.class);
                context.takeScreenshot("invu-ru-escape-player");
                context.clickScreenButton("invumusicplayer.menu.settings");context.waitForScreen(SettingsScreen.class);
                context.takeScreenshot("invu-ru-escape-settings");
                context.clickScreenButton("invumusicplayer.settings.back");context.waitForScreen(PauseScreen.class);
                context.getInput().pressKey(SDLScancode.SDL_SCANCODE_ESCAPE);context.waitForScreen(null);
                openPlayerWithHotkey(context);
                context.takeScreenshot("invumusicplayer-ru-player-text-only");
                context.clickScreenButton("invumusicplayer.player.account");
                context.waitForScreen(AccountScreen.class);
                EditBox russianTokenInput = enterSyntheticToken(context);
                context.takeScreenshot("invumusicplayer-ru-account-disconnected");
                context.clickScreenButton("invumusicplayer.account.back");
                context.waitForScreen(PlayerScreen.class);
                check(context.computeOnClient(client -> russianTokenInput.getValue().isEmpty()),
                    "Russian account screen did not clear its token on close");
                context.clickScreenButton("invumusicplayer.player.settings");
                context.waitForScreen(SettingsScreen.class);
                context.clickScreenButton("invumusicplayer.tabs.appearance");
                clickToggle(context, "invumusicplayer.settings.covers", false);
                check(fixture.settings.covers, "Russian cover visibility setting did not change");
                context.takeScreenshot("invumusicplayer-ru-settings");
                context.clickScreenButton("invumusicplayer.settings.back");
                context.waitForScreen(PlayerScreen.class);
                context.clickScreenButton("invumusicplayer.player.hud");
                context.waitForScreen(HudEditor.class);
                context.takeScreenshot("invumusicplayer-ru-hud-editor");
                context.clickScreenButton("invumusicplayer.hud_editor.done");
                context.waitForScreen(PlayerScreen.class);
                context.clickScreenButton("invumusicplayer.player.done");
                context.waitForScreen(null);
            }
            context.waitForScreen(net.minecraft.client.gui.screens.TitleScreen.class);
            context.waitFor(client -> !fixture.playing());
            check(!fixture.queue().isEmpty(),"Leaving the world erased its queue");
            context.clickScreenButton("invumusicplayer");context.waitForScreen(PlayerScreen.class);
            context.takeScreenshot("invu-ru-player-after-world-exit");context.clickScreenButton("invumusicplayer.player.done");
        } finally {
            context.runOnClient(client -> {
                InvuMusicPlayerClient.player = original;
                fixture.close();
            });
            selectLanguage(context, originalLanguage);
        }
    }

    private static void checkPauseAndHiddenHud(ClientGameTestContext context,Player fixture) {
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_ESCAPE);context.waitForScreen(PauseScreen.class);
        context.runOnClient(client -> {
            var widgets=Screens.getWidgets(client.gui.screen());
            long open=widgets.stream().filter(w->w.getMessage().getString().equals(Component.translatable("invumusicplayer.menu.open").getString())).count();
            check(open==1,"Pause menu did not add exactly one player entry");
            for(var playerControl:widgets)if(playerControl instanceof PlayerMenuButton)for(var vanilla:widgets)if(!(vanilla instanceof PlayerMenuButton)) {
                boolean overlap=playerControl.getX()<vanilla.getX()+vanilla.getWidth()&&playerControl.getX()+playerControl.getWidth()>vanilla.getX()
                    &&playerControl.getY()<vanilla.getY()+vanilla.getHeight()&&playerControl.getY()+playerControl.getHeight()>vanilla.getY();
                check(!overlap,"Player control overlaps a vanilla pause button");
            }
        });
        context.takeScreenshot("invu-en-escape-player");
        context.clickScreenButton("invumusicplayer.player.pause");context.waitFor(client->!fixture.playing());
        context.waitForScreen(PauseScreen.class);context.clickScreenButton("invumusicplayer.player.play");context.waitFor(client->fixture.playing());
        context.clickScreenButton("invumusicplayer.menu.next");context.waitFor(client->fixture.current().title().equals("Second")&&fixture.playing());
        context.clickScreenButton("invumusicplayer.menu.previous");context.waitFor(client->fixture.current().title().equals("First")&&fixture.playing());
        context.clickScreenButton("invumusicplayer.menu.settings");context.waitForScreen(SettingsScreen.class);
        check(context.computeOnClient(client->client.gui.screen().isPauseScreen()),"Settings opened from Escape resumed the singleplayer world");
        context.takeScreenshot("invu-en-escape-settings");context.clickScreenButton("invumusicplayer.settings.back");context.waitForScreen(PauseScreen.class);
        context.clickScreenButton("invumusicplayer.menu.open");context.waitForScreen(PlayerScreen.class);
        context.clickScreenButton("invumusicplayer.player.done");context.waitForScreen(PauseScreen.class);
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_ESCAPE);context.waitForScreen(null);
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_F1);context.waitFor(client->client.gui.hud.isHidden());
        check(context.computeOnClient(client->PlayerHud.isVisible()),"F1 hid the music player with the option enabled");
        context.waitTicks(8);context.takeScreenshot("invu-en-f1-player-visible");
        context.runOnClient(client->fixture.settings.hudWhileHidden=false);
        check(context.computeOnClient(client->!PlayerHud.isVisible()),"The F1 visibility opt-out was ignored");
        context.waitTicks(8);context.takeScreenshot("invu-en-f1-player-hidden");
        context.runOnClient(client->fixture.settings.hudWhileHidden=true);
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_F1);context.waitFor(client->!client.gui.hud.isHidden());
    }

    private static void selectLanguage(ClientGameTestContext context, String code) {
        if (context.computeOnClient(client -> client.getLanguageManager().getSelected().equals(code))) return;
        CompletableFuture<Void> reload = context.computeOnClient(client -> {
            client.getLanguageManager().setSelected(code);
            client.options.languageCode = code;
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> reload.isDone(), 1200);
        // Joining only after completion on the test thread lets the render/client thread keep reloading.
        reload.join();
        context.waitFor(client -> client.gui.overlay() == null, 1200);
        check(context.computeOnClient(client -> client.getLanguageManager().getSelected().equals(code)),
            "Minecraft did not select language " + code);
    }

    private static void checkTranslation(ClientGameTestContext context, String key, String expected) {
        String actual = context.computeOnClient(client -> Component.translatable(key).getString());
        check(actual.equals(expected), "Unexpected translation for " + key + ": " + actual);
    }

    private static void clickToggle(ClientGameTestContext context, String key, boolean enabled) {
        String label = context.computeOnClient(client -> Component.translatable(key,
            Component.translatable(enabled ? "invumusicplayer.common.on" : "invumusicplayer.common.off")).getString());
        context.clickScreenButton(label);
    }

    private static EditBox enterSyntheticToken(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            EditBox input = client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("Account screen did not create its token field"));
            // This value is deliberately synthetic; no account/API operation is invoked.
            input.setValue("invumusicplayer-smoke-synthetic-token");
            return input;
        });
    }

    private static void openPlayerWithHotkey(ClientGameTestContext context) {
        press(context, "open");
        context.waitForScreen(PlayerScreen.class);
        check(context.computeOnClient(client -> !client.gui.screen().isPauseScreen()), "Player screen pauses gameplay");
    }

    private static void press(ClientGameTestContext context, String action) {
        KeyMapping mapping = context.computeOnClient(client -> Arrays.stream(client.options.keyMappings)
            .filter(key -> key.getName().equals("key.invumusicplayer." + action)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing key mapping: " + action)));
        context.getInput().pressKey(mapping);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static boolean hasSignal(float[] values) { for(float v:values)if(v>.1f)return true;return false; }
}
