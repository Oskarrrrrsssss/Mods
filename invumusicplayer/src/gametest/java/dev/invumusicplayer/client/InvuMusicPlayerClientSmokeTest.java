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
            for (String action : new String[] {"open", "pause", "next", "previous", "wave", "like", "dislike", "shuffle"}) {
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
            context.waitFor(client -> fixture.items().size() == 3);
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
            context.waitFor(client->InvuMusicPlayerClient.cover.ready());
            context.takeScreenshot("invu-en-title-screen-entry");
            checkVisibility(context,fixture);
            context.runOnClient(client->{client.options.guiScale().set(2);client.resizeGui();});context.waitTicks(4);
            context.takeScreenshot("invu-en-title-card-large");
            context.runOnClient(client->{client.options.guiScale().set(3);client.resizeGui();});context.waitTicks(4);
            context.clickScreenButton("invumusicplayer.wave.settings");context.waitForScreen(WaveSettingsScreen.class);
            context.clickScreenButton("invumusicplayer.mood.sad");check(fixture.settings.waveMood.equals("sad"),"Direct wave mood setting failed");
            context.takeScreenshot("invu-en-wave-settings");context.clickScreenButton("invumusicplayer.settings.back");
                    context.clickScreenButton("invumusicplayer.menu.open");context.waitForScreen(PlayerScreen.class);
            context.clickScreenButton("invumusicplayer.player.settings");context.waitForScreen(SettingsScreen.class);
            context.takeScreenshot("invu-en-settings-without-world");context.clickScreenButton("invumusicplayer.settings.back");
            checkPlayerWheel(context,fixture);
            context.clickScreenButton("invumusicplayer.player.done");context.waitForScreen(net.minecraft.client.gui.screens.TitleScreen.class);
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                context.waitTicks(4);
                context.takeScreenshot("invumusicplayer-en-world-hud-and-equalizer");
                checkPauseAndHiddenHud(context,fixture);
                openPlayerWithHotkey(context);
                context.waitFor(client -> InvuMusicPlayerClient.cover.ready() && ((PlayerScreen)client.gui.screen()).readyArtworkCount() == 3);
                context.runOnClient(client->{var widgets=client.gui.screen().children();var volume=widgets.stream().filter(ValueSlider.class::isInstance).map(ValueSlider.class::cast).findFirst().orElseThrow();
                    for(var w:widgets)if(w instanceof net.minecraft.client.gui.components.Button button){int distance=button.getY()>=volume.getY()?button.getY()-(volume.getY()+volume.getHeight()):volume.getY()-(button.getY()+button.getHeight());check(distance>=10,"Full-player volume is adjacent to clickable buttons");}
                });
                context.takeScreenshot("invumusicplayer-en-player-with-cover");
                context.clickScreenButton("invumusicplayer.player.library");context.waitForScreen(LibraryScreen.class);
                context.waitFor(client->((LibraryScreen)client.gui.screen()).readyArtworkCount()==2);
                context.takeScreenshot("invu-en-queue-library");
                var all=context.computeOnClient(client->fixture.items());
                for(int index=0;index<all.size();index++){int item=index;context.runOnClient(client->fixture.choose(all,item));context.waitFor(client->fixture.playing()&&!fixture.loading());context.runOnClient(client->fixture.rate(true));context.waitFor(client->fixture.rating()==1);}
                context.runOnClient(client->fixture.choose(all,0));context.waitFor(client->fixture.playing()&&!fixture.loading());
                context.clickScreenButton("invumusicplayer.library.liked");context.waitFor(client->fixture.items().size()==3&&((LibraryScreen)client.gui.screen()).readyArtworkCount()==2);
                check(context.computeOnClient(client->((LibraryScreen)client.gui.screen()).pageCount())==2,"Liked tracks do not have two pages");
                checkSelected(context,"invumusicplayer.library.liked");
                context.clickScreenButton("invumusicplayer.library.next");context.waitFor(client->((LibraryScreen)client.gui.screen()).page()==1&&((LibraryScreen)client.gui.screen()).readyArtworkCount()==1);
                context.takeScreenshot("invu-en-liked-second-page");context.clickScreenButton("invumusicplayer.library.prev");
                check(context.computeOnClient(client->((LibraryScreen)client.gui.screen()).page())==0,"Liked previous-page control failed");
                context.clickScreenButton("invumusicplayer.library.play_all");context.waitFor(client->fixture.playing()&&fixture.current().title().equals("First"));
                check(fixture.queue().size()==2,"Play all liked did not use the entire liked list");
                context.runOnClient(client->fixture.savePlaylist("Smoke playlist"));context.clickScreenButton("invumusicplayer.library.playlists");
                context.clickScreenButton("invumusicplayer.library.delete");context.waitForScreen(DeletePlaylistScreen.class);
                context.takeScreenshot("invu-en-delete-local-playlist");context.clickScreenButton("invumusicplayer.library.cancel");context.waitForScreen(LibraryScreen.class);
                check(fixture.savedPlaylists().contains("Smoke playlist"),"Cancel deleted the playlist");
                context.clickScreenButton("invumusicplayer.library.delete");context.waitForScreen(DeletePlaylistScreen.class);context.clickScreenButton("invumusicplayer.library.confirm_delete");context.waitForScreen(LibraryScreen.class);
                check(!fixture.savedPlaylists().contains("Smoke playlist")&&fixture.playing(),"Delete did not remove only the playlist");
                context.clickScreenButton("invumusicplayer.player.done");
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
                context.clickScreenButton("invumusicplayer.tabs.visibility");checkSelected(context,"invumusicplayer.tabs.visibility");
                checkTranslation(context,"invumusicplayer.tabs.visibility","Видимость");context.takeScreenshot("invu-ru-player-visibility");
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
            context.waitFor(client -> fixture.playing());
            check(fixture.current().title().equals("First"),"World exit replaced the current track");
            context.takeScreenshot("invu-ru-title-playing");
            check(!fixture.queue().isEmpty(),"Leaving the world erased its queue");
            context.clickScreenButton("invumusicplayer.menu.open");context.waitForScreen(PlayerScreen.class);
            context.takeScreenshot("invu-ru-player-after-world-exit");context.clickScreenButton("invumusicplayer.player.done");
        } finally {
            context.runOnClient(client -> {
                InvuMusicPlayerClient.player = original;
                fixture.close();
            });
            selectLanguage(context, originalLanguage);
        }
    }

    private static void checkVisibility(ClientGameTestContext context,Player fixture) {
        context.runOnClient(client->{
            var widgets=Screens.getWidgets(client.gui.screen());
            check(widgets.stream().filter(PlayerMenuButton.class::isInstance).allMatch(w->w.getHeight()<=10),"Title menu still contains a standalone branded player button");
        });
        context.clickScreenButton("invumusicplayer.menu.settings");context.waitForScreen(SettingsScreen.class);
        context.clickScreenButton("invumusicplayer.tabs.visibility");checkSelected(context,"invumusicplayer.tabs.visibility");
        clickToggle(context,"invumusicplayer.visibility.game",true);
        clickToggle(context,"invumusicplayer.visibility.title",true);
        check(!fixture.settings.hud&&!fixture.settings.titlePlayer&&fixture.settings.pausePlayer,"Visibility controls are not independent");
        clickToggle(context,"invumusicplayer.visibility.all",false);
        check(!fixture.settings.playersVisible&&fixture.playing(),"Hiding all stopped the music");
        context.takeScreenshot("invu-en-player-visibility");
        context.clickScreenButton("invumusicplayer.settings.back");context.waitForScreen(net.minecraft.client.gui.screens.TitleScreen.class);
        context.runOnClient(client->{check(!MenuPlayerPanel.isVisible(client.gui.screen()),"Hidden title card still visible");check(Screens.getWidgets(client.gui.screen()).stream().noneMatch(w->w instanceof PlayerMenuButton||w instanceof ValueSlider),"Hidden title player still has clickable controls");});
        context.takeScreenshot("invu-en-title-all-players-hidden");
        context.clickScreenButton("menu.options");context.waitForScreen(net.minecraft.client.gui.screens.options.OptionsScreen.class);
        context.takeScreenshot("invu-en-hidden-player-recovery");
        context.clickScreenButton("invumusicplayer.menu.settings");context.waitForScreen(SettingsScreen.class);
        context.clickScreenButton("invumusicplayer.tabs.visibility");clickToggle(context,"invumusicplayer.visibility.all",true);
        check(fixture.settings.playersVisible&&!fixture.settings.titlePlayer&&!fixture.settings.hud&&fixture.settings.pausePlayer,"Hide-all overwrote individual visibility preferences");
        clickToggle(context,"invumusicplayer.visibility.game",false);clickToggle(context,"invumusicplayer.visibility.title",false);
        context.clickScreenButton("invumusicplayer.settings.back");context.waitForScreen(net.minecraft.client.gui.screens.options.OptionsScreen.class);
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_ESCAPE);context.waitForScreen(net.minecraft.client.gui.screens.TitleScreen.class);
        check(context.computeOnClient(client->MenuPlayerPanel.isVisible(client.gui.screen())),"Could not restore hidden title player without a world");
    }

    private static void checkPlayerWheel(ClientGameTestContext context,Player fixture) {
        var original=fixture.items();var first=fixture.current();long position=fixture.positionMillis();
        var extended=new java.util.ArrayList<>(original);
        for(var track:original)extended.add(new dev.invumusicplayer.core.Track("scroll:"+track.id(),"Scroll "+track.title(),track.artist(),track.durationMillis(),track.file(),track.coverFile(),track.coverUrl(),track.batchId()));
        context.runOnClient(client->{fixture.choose(extended,0);fixture.savePlaylist("Wheel fixture");fixture.openSavedPlaylist("Wheel fixture");});
        context.waitFor(client->fixture.items().size()==6&&((PlayerScreen)client.gui.screen()).pageCount()==2);
        context.runOnClient(client->{var screen=(PlayerScreen)client.gui.screen();check(screen.mouseScrolled(50,100,0,-1),"Player track list ignored mouse wheel");check(screen.page()==1,"Wheel did not advance player list");screen.mouseScrolled(50,100,0,-1);check(screen.page()==1,"Wheel exceeded last page");screen.mouseScrolled(screen.width-30,100,0,1);check(screen.page()==1,"Wheel outside list changed track page");});
        context.takeScreenshot("invu-en-player-wheel-second-page");
        context.runOnClient(client->{var screen=(PlayerScreen)client.gui.screen();screen.mouseScrolled(50,100,0,1);check(screen.page()==0,"Wheel did not return to first page");screen.mouseScrolled(50,100,0,1);check(screen.page()==0,"Wheel exceeded first page");check(fixture.current().id().equals(first.id())&&fixture.positionMillis()==position&&fixture.playing(),"Scrolling changed playback");fixture.deleteSavedPlaylist("Wheel fixture");fixture.choose(original,0);fixture.refreshLocal();});
        context.waitFor(client->fixture.items().size()==3&&fixture.playing());
    }

    private static void checkPauseAndHiddenHud(ClientGameTestContext context,Player fixture) {
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_ESCAPE);context.waitForScreen(PauseScreen.class);
        context.runOnClient(client -> {
            var widgets=Screens.getWidgets(client.gui.screen());
            long open=widgets.stream().filter(w->w.getMessage().getString().equals(Component.translatable("invumusicplayer.menu.open").getString())).count();
            check(open==1,"Pause menu did not add one icon opening the full player");
            check(widgets.stream().filter(PlayerMenuButton.class::isInstance).allMatch(w->w.getHeight()<=10),"Menu controls are not scaled to 50 percent");
            var volume=widgets.stream().filter(ValueSlider.class::isInstance).map(ValueSlider.class::cast).findFirst().orElseThrow(()->new AssertionError("Menu volume slider missing"));
            for(var control:widgets)if(control instanceof PlayerMenuButton)check(volume.getY()-(control.getY()+control.getHeight())>=7,"Menu volume is too close to clickable controls");
            check(PlayerHud.backgroundColor()==InvuMusicPlayerClient.cover.paletteColor(),"Gameplay HUD does not follow album color");
            for(var playerControl:widgets)if(playerControl instanceof PlayerMenuButton)for(var vanilla:widgets)if(!(vanilla instanceof PlayerMenuButton)) {
                boolean overlap=playerControl.getX()<vanilla.getX()+vanilla.getWidth()&&playerControl.getX()+playerControl.getWidth()>vanilla.getX()
                    &&playerControl.getY()<vanilla.getY()+vanilla.getHeight()&&playerControl.getY()+playerControl.getHeight()>vanilla.getY();
                check(!overlap,"Player control overlaps a vanilla pause button");
            }
        });
        context.clickScreenButton("invumusicplayer.player.like");context.waitFor(client->fixture.rating()==1);
        check(context.computeOnClient(client->Screens.getWidgets(client.gui.screen()).stream().anyMatch(w->w instanceof PlayerMenuButton b&&b.getMessage().getString().equals(Component.translatable("invumusicplayer.player.like").getString())&&b.isSelected())),"Like icon did not select");
        context.takeScreenshot("invu-en-escape-liked");
        context.clickScreenButton("invumusicplayer.player.like");context.waitFor(client->fixture.rating()==0);
        context.clickScreenButton("invumusicplayer.player.dislike");context.waitFor(client->fixture.rating()==-1);
        check(fixture.current().title().equals("First"),"Ordinary playlist dislike skipped the song");
        context.clickScreenButton("invumusicplayer.player.dislike");context.waitFor(client->fixture.rating()==0);
        context.clickScreenButton("invumusicplayer.player.shuffle");check(fixture.settings.shuffle,"Shuffle menu control failed");
        context.clickScreenButton("invumusicplayer.player.shuffle");check(!fixture.settings.shuffle,"Shuffle menu control did not toggle off");
        context.runOnClient(client->{
            var slider=Screens.getWidgets(client.gui.screen()).stream().filter(ValueSlider.class::isInstance).map(ValueSlider.class::cast).findFirst().orElseThrow();
            var click=new net.minecraft.client.input.MouseButtonEvent(slider.getX()+4+(slider.getWidth()-8)*.25,slider.getY()+slider.getHeight()/2.0,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
            check(slider.mouseClicked(click,false),"Menu volume slider did not receive click");slider.mouseReleased(click);
            check(Math.abs(fixture.settings.volume-.25f)<.02,"Menu volume slider did not change playback volume");
        });
        context.takeScreenshot("invu-en-escape-player");
        context.clickScreenButton("invumusicplayer.player.pause");context.waitFor(client->!fixture.playing());
        context.waitForScreen(PauseScreen.class);context.clickScreenButton("invumusicplayer.player.play");context.waitFor(client->fixture.playing());
        context.clickScreenButton("invumusicplayer.menu.next");context.waitFor(client->fixture.current().title().equals("Second")&&fixture.playing());
        context.clickScreenButton("invumusicplayer.menu.previous");context.waitFor(client->fixture.current().title().equals("First")&&fixture.playing());
        context.clickScreenButton("invumusicplayer.menu.settings");context.waitForScreen(SettingsScreen.class);
        check(context.computeOnClient(client->client.gui.screen().isPauseScreen()),"Settings opened from Escape resumed the singleplayer world");
        checkSelected(context,"invumusicplayer.tabs.audio");
        check(context.computeOnClient(client->client.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).noneMatch(b->b.getMessage().equals(Component.translatable("invumusicplayer.tabs.wave")))),"My Wave is still duplicated in general settings");
        check(context.computeOnClient(client->client.gui.screen().children().stream().noneMatch(ValueSlider.class::isInstance)),"Settings still contain volume");
        context.clickScreenButton("invumusicplayer.tabs.appearance");checkSelected(context,"invumusicplayer.tabs.appearance");
        context.clickScreenButton("invumusicplayer.tabs.audio");
        context.clickScreenButton("invumusicplayer.tabs.visibility");
        clickToggle(context,"invumusicplayer.visibility.pause",true);
        check(fixture.settings.hud&&fixture.settings.titlePlayer&&!fixture.settings.pausePlayer,"Escape visibility changed other players");
        context.clickScreenButton("invumusicplayer.settings.back");context.waitForScreen(PauseScreen.class);
        check(context.computeOnClient(client->!MenuPlayerPanel.isVisible(client.gui.screen())&&Screens.getWidgets(client.gui.screen()).stream().noneMatch(w->w instanceof PlayerMenuButton||w instanceof ValueSlider)),"Escape player was not fully hidden");
        context.takeScreenshot("invu-en-escape-player-hidden");
        context.clickScreenButton("menu.options");context.waitForScreen(net.minecraft.client.gui.screens.options.OptionsScreen.class);
        context.clickScreenButton("invumusicplayer.menu.settings");context.waitForScreen(SettingsScreen.class);
        context.clickScreenButton("invumusicplayer.tabs.visibility");clickToggle(context,"invumusicplayer.visibility.pause",false);
        context.clickScreenButton("invumusicplayer.settings.back");context.waitForScreen(net.minecraft.client.gui.screens.options.OptionsScreen.class);
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_ESCAPE);context.waitForScreen(PauseScreen.class);
        context.clickScreenButton("invumusicplayer.menu.settings");context.waitForScreen(SettingsScreen.class);
        context.takeScreenshot("invu-en-escape-settings");context.clickScreenButton("invumusicplayer.settings.back");context.waitForScreen(PauseScreen.class);
        context.clickScreenButton("invumusicplayer.menu.position");context.waitForScreen(MenuEditor.class);
        context.runOnClient(client->{var screen=client.gui.screen();var l=MenuPlayerPanel.layout(screen.width,screen.height,false);
            var click=new net.minecraft.client.input.MouseButtonEvent(l.x()+l.width()/2.0,l.y()+l.height()/2.0,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
            check(screen.mouseClicked(click,false),"Menu editor card did not accept dragging");var drag=new net.minecraft.client.input.MouseButtonEvent(click.x()+20,click.y()+15,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));screen.mouseDragged(drag,20,15);screen.mouseReleased(drag);
            check(fixture.settings.menuPositioned&&fixture.settings.menuX>0&&fixture.settings.menuY>0,"Menu card dragging did not change coordinates");});
        context.clickScreenButton("invumusicplayer.hud_editor.bottom_left");check(fixture.settings.menuPositioned&&fixture.settings.menuX==0&&fixture.settings.menuY==1,"Bottom-left menu corner failed");
        context.clickScreenButton("invumusicplayer.hud_editor.bottom_right");check(fixture.settings.menuPositioned&&fixture.settings.menuX==1&&fixture.settings.menuY==1,"Menu position did not change");
        context.clickScreenButton("invumusicplayer.hud_editor.scale_up");check(fixture.settings.menuScale>.5f,"Menu scaling did not change");
        context.takeScreenshot("invu-en-menu-position-editor");context.clickScreenButton("invumusicplayer.hud_editor.done");context.waitForScreen(PauseScreen.class);
        context.runOnClient(client->{var l=MenuPlayerPanel.layout(client.gui.screen().width,client.gui.screen().height,false);check(l.x()>100&&l.y()>8,"Custom menu position was not restored");});
        context.takeScreenshot("invu-en-escape-moved");
        context.clickScreenButton("invumusicplayer.menu.position");context.waitForScreen(MenuEditor.class);context.clickScreenButton("invumusicplayer.menu.reset");context.clickScreenButton("invumusicplayer.hud_editor.done");context.waitForScreen(PauseScreen.class);
        context.clickScreenButton("invumusicplayer.menu.open");context.waitForScreen(PlayerScreen.class);
        context.clickScreenButton("invumusicplayer.player.account");context.waitForScreen(AccountScreen.class);
        check(context.computeOnClient(client->client.gui.screen().isPauseScreen()),"Account navigation resumed the paused world");
        context.clickScreenButton("invumusicplayer.account.back");context.waitForScreen(PlayerScreen.class);
        context.clickScreenButton("invumusicplayer.player.done");context.waitForScreen(PauseScreen.class);
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_ESCAPE);context.waitForScreen(null);
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_F1);context.waitFor(client->client.gui.hud.isHidden());
        check(context.computeOnClient(client->PlayerHud.isVisible()),"F1 hid the music player with the option enabled");
        context.runOnClient(client->fixture.settings.playersVisible=false);
        check(context.computeOnClient(client->!PlayerHud.isVisible())&&fixture.playing(),"Hide-all does not hide gameplay HUD while retaining audio");
        context.runOnClient(client->{fixture.settings.playersVisible=true;fixture.settings.hud=false;});
        check(context.computeOnClient(client->!PlayerHud.isVisible()),"Individual in-game visibility switch was ignored");
        context.runOnClient(client->fixture.settings.hud=true);
        context.waitTicks(8);context.takeScreenshot("invu-en-f1-player-visible");
        context.runOnClient(client->fixture.settings.hudWhileHidden=false);
        check(context.computeOnClient(client->!PlayerHud.isVisible()),"The F1 visibility opt-out was ignored");
        context.waitTicks(8);context.takeScreenshot("invu-en-f1-player-hidden");
        context.runOnClient(client->fixture.settings.hudWhileHidden=true);
        context.getInput().pressKey(SDLScancode.SDL_SCANCODE_F1);context.waitFor(client->!client.gui.hud.isHidden());
    }

    private static void checkSelected(ClientGameTestContext context,String key){check(context.computeOnClient(client->client.gui.screen().children().stream().filter(ModernButton.class::isInstance).map(ModernButton.class::cast).anyMatch(b->b.getMessage().equals(Component.translatable(key))&&b.isSelected())),"Section is not highlighted: "+key);}
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
