package dev.musicraft.client;

import dev.musicraft.core.Player;
import dev.musicraft.core.SmokePlayerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

/** Runs against the actual client, UI, input registration and world rendering; ships in a separate test mod. */
@SuppressWarnings("UnstableApiUsage")
public final class MusicraftClientSmokeTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        SmokePlayerFactory.verifyBundledCodecs();
        Player original = context.computeOnClient(client -> {
            check(MusicraftClient.player != null, "Musicraft client entrypoint did not initialize the player");
            Set<String> names = Arrays.stream(client.options.keyMappings).map(KeyMapping::getName).collect(Collectors.toSet());
            for (String action : new String[] {"open", "lyrics", "pause", "next", "previous"}) {
                check(names.contains("key.musicraft." + action), "Missing registered key mapping: " + action);
            }
            return MusicraftClient.player;
        });
        Path fixtureDirectory = FabricLoader.getInstance().getGameDir();
        final Player fixture;
        try { fixture = SmokePlayerFactory.create(fixtureDirectory); }
        catch (IOException error) { throw new AssertionError("Cannot create synthetic local music fixture", error); }
        context.runOnClient(client -> MusicraftClient.player = fixture);
        try {
            context.waitFor(client -> fixture.items().size() == 2);
            context.runOnClient(client -> {
                fixture.settings.sprintOnly = false;
                fixture.settings.lyrics3d = true;
                fixture.settings.covers = true;
                fixture.choose(fixture.items(), 0);
            });
            context.waitFor(client -> fixture.playing() && !fixture.loading() && fixture.lyrics().lines().size() == 3
                && fixture.coverBytes().length > 0);
            check(fixture.current().title().equals("First"), "The local library did not load its first track");
            check(fixture.lyrics().textAt(500).equals("Musicraft local lyric"), "Local LRC timeline did not load");

            context.getInput().resizeWindow(1280, 720);
            context.runOnClient(client -> { client.options.guiScale().set(3); client.resizeGui(); });
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                context.waitTicks(4);
                context.takeScreenshot("musicraft-world-hud-and-lyrics");
                openPlayerWithHotkey(context);
                context.takeScreenshot("musicraft-player-with-cover");

                context.clickScreenButton("Аккаунт");
                context.waitForScreen(AccountScreen.class);
                context.takeScreenshot("musicraft-account-disconnected");
                context.clickScreenButton("Назад");
                context.waitForScreen(PlayerScreen.class);

                context.clickScreenButton("Настройки");
                context.waitForScreen(SettingsScreen.class);
                context.clickScreenButton("Обложки: вкл");
                check(!fixture.settings.covers, "Cover visibility setting did not change");
                context.takeScreenshot("musicraft-settings");
                context.clickScreenButton("Назад");
                context.waitForScreen(PlayerScreen.class);
                context.takeScreenshot("musicraft-player-text-only");

                context.clickScreenButton("HUD");
                context.waitForScreen(HudEditor.class);
                context.clickScreenButton("+ Масштаб");
                check(fixture.settings.hudScale > 1, "HUD scaling button did not change the scale");
                context.clickScreenButton("Справа снизу");
                check(fixture.settings.hudX > 12 && fixture.settings.hudY > 12, "HUD corner button did not move the panel");
                context.takeScreenshot("musicraft-hud-editor");
                context.clickScreenButton("Готово");
                context.waitForScreen(PlayerScreen.class);
                context.clickScreenButton("Готово");
                context.waitForScreen(null);
                context.takeScreenshot("musicraft-world-custom-hud");

                press(context, "pause");
                context.waitFor(client -> !fixture.playing());
                press(context, "pause");
                context.waitFor(client -> fixture.playing());
                press(context, "next");
                context.waitFor(client -> fixture.current() != null && fixture.current().title().equals("Second")
                    && fixture.playing() && !fixture.loading());
                press(context, "previous");
                context.waitFor(client -> fixture.current() != null && fixture.current().title().equals("First")
                    && fixture.playing() && !fixture.loading());
                press(context, "lyrics");
                check(!fixture.settings.lyrics3d, "Lyrics hotkey did not disable 3D subtitles");
                context.takeScreenshot("musicraft-world-lyrics-disabled");
                press(context, "lyrics");
                check(fixture.settings.lyrics3d, "Lyrics hotkey did not enable 3D subtitles");
                context.takeScreenshot("musicraft-world-lyrics-enabled");
            }
        } finally {
            context.runOnClient(client -> {
                MusicraftClient.player = original;
                fixture.close();
            });
        }
    }

    private static void openPlayerWithHotkey(ClientGameTestContext context) {
        press(context, "open");
        context.waitForScreen(PlayerScreen.class);
        check(context.computeOnClient(client -> !client.gui.screen().isPauseScreen()), "Player screen pauses gameplay");
    }

    private static void press(ClientGameTestContext context, String action) {
        KeyMapping mapping = context.computeOnClient(client -> Arrays.stream(client.options.keyMappings)
            .filter(key -> key.getName().equals("key.musicraft." + action)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing key mapping: " + action)));
        context.getInput().pressKey(mapping);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
