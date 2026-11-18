package dev.invumusicplayer.client;

import dev.invumusicplayer.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;

public final class PortSmoke implements ClientModInitializer {
 private int stage,ticks,wait;private Player fixture;private Screen title;private long normalDraws,hiddenDraws;
 @Override public void onInitializeClient(){if(!Boolean.getBoolean("invu.smoke"))return;ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private void capture(Minecraft mc,String name)throws Exception{Path folder=FabricLoader.getInstance().getGameDir().resolve("screenshots");Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name+".png"));}}
 private void click(Minecraft mc,String key){var widget=Screens.getButtons(mc.screen).stream().filter(b->b.getMessage().equals(Component.translatable(key))).findFirst().orElseThrow();check(mc.screen.mouseClicked(widget.getX()+widget.getWidth()/2.0,widget.getY()+widget.getHeight()/2.0,0),"Click ignored: "+key);mc.screen.mouseReleased(widget.getX()+widget.getWidth()/2.0,widget.getY()+widget.getHeight()/2.0,0);}
 private void tick(Minecraft mc){
  try{if(++ticks>3600)throw new AssertionError("Client verification timed out at stage "+stage);if(mc.getOverlay()!=null)return;if(wait++<15)return;wait=0;
   switch(stage){
    case 0->{if(!(mc.screen instanceof TitleScreen)||InvuMusicPlayerClient.player==null)return;if(!mc.getLanguageManager().getSelected().equals("en_us")){mc.getLanguageManager().setSelected("en_us");mc.options.languageCode="en_us";mc.reloadResourcePacks();return;}title=mc.screen;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.enableVsync().set(false);SmokePlayerFactory.verifyBundledCodecs();fixture=SmokePlayerFactory.create(FabricLoader.getInstance().getGameDir());InvuMusicPlayerClient.player.close();InvuMusicPlayerClient.player=fixture;fixture.refreshLocal();stage++;}
    case 1->{if(fixture.items().size()!=3)return;fixture.choose(fixture.items(),0);stage++;}
    case 2->{if(!fixture.playing()||!InvuMusicPlayerClient.cover.ready())return;mc.setScreen(title);stage++;}
    case 3->{capture(mc,"01-title-mini-player");check(MenuPlayerPanel.isVisible(mc.screen),"Title mini player hidden");mc.setScreen(new PlayerScreen());stage++;}
    case 4->{capture(mc,"02-main-player");click(mc,"invumusicplayer.player.pause");check(!fixture.playing(),"Pause failed");click(mc,"invumusicplayer.player.play");check(fixture.playing(),"Resume failed");var before=fixture.queue();click(mc,"invumusicplayer.player.shuffle");check(fixture.settings.shuffle&&!fixture.queue().equals(before),"Shuffle failed");click(mc,"invumusicplayer.player.shuffle");check(fixture.queue().equals(before),"Shuffle restore failed");click(mc,"invumusicplayer.player.library");stage++;}
    case 5->{check(mc.screen instanceof LibraryScreen,"Library missing");capture(mc,"03-library");click(mc,"invumusicplayer.player.done");mc.setScreen(new SettingsScreen(mc.screen));stage++;}
    case 6->{capture(mc,"04-settings");click(mc,"invumusicplayer.tabs.visibility");stage++;}
    case 7->{capture(mc,"05-mini-settings");check(Component.translatable("invumusicplayer.tabs.visibility").getString().equals("Mini player"),"English translation missing");mc.getLanguageManager().setSelected("ru_ru");mc.options.languageCode="ru_ru";mc.reloadResourcePacks();stage++;}
    case 8->{check(Component.translatable("invumusicplayer.tabs.visibility").getString().equals("Мини-плеер"),"Russian translation missing");capture(mc,"06-ru-mini-settings");mc.setScreen(new PlayerScreen());stage++;}
    case 9->{capture(mc,"07-ru-main-player");mc.setScreen(new SettingsScreen(mc.screen));stage++;}
    case 10->{capture(mc,"08-ru-settings");
     mc.createWorldOpenFlows().createFreshLevel("invu-port-smoke",new net.minecraft.world.level.LevelSettings("Invu Port Smoke",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),net.minecraft.world.level.levelgen.WorldOptions.defaultWithRandomSeed(),net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions,title);stage++;}
    case 11->{if(mc.level==null||mc.screen!=null||PlayerHud.drawCount()==0)return;capture(mc,"09-world-hud");normalDraws=PlayerHud.drawCount();check(normalDraws>0,"World HUD never rendered");mc.options.hideGui=true;stage++;}
    case 12->{check(PlayerHud.drawCount()>normalDraws,"F1 hid the player geometry");capture(mc,"10-world-f1-visible");fixture.settings.hudWhileHidden=false;hiddenDraws=PlayerHud.drawCount();stage++;}
    case 13->{check(PlayerHud.drawCount()==hiddenDraws,"F1 opt-out still drew player");capture(mc,"11-world-f1-hidden");fixture.settings.hudWhileHidden=true;mc.options.hideGui=false;mc.setScreen(new PauseScreen(true));stage++;}
    case 14->{capture(mc,"12-pause-mini-player");check(MenuPlayerPanel.isVisible(mc.screen),"Pause mini player missing");click(mc,"invumusicplayer.player.pause");check(!fixture.playing(),"Mini pause failed");click(mc,"invumusicplayer.player.play");check(fixture.playing(),"Mini resume failed");click(mc,"invumusicplayer.player.time");check(!fixture.settings.showTime,"Mini clock failed");click(mc,"invumusicplayer.player.time");check(fixture.settings.showTime,"Mini clock restore failed");net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(org.lwjgl.glfw.GLFW.GLFW_KEY_M,0));stage++;}
    case 15->{if(!(mc.screen instanceof PlayerScreen))return;capture(mc,"13-hotkey-player");var slider=mc.screen.children().stream().filter(SeekSlider.class::isInstance).map(SeekSlider.class::cast).findFirst().orElseThrow();double x=slider.getX()+4+(slider.getWidth()-8)*.75,y=slider.getY()+slider.getHeight()/2.0;long revision=fixture.playbackRevision();slider.onClick(x,y);check(fixture.playbackRevision()==revision,"Seek restarted before release");slider.onRelease(x,y);check(fixture.playbackRevision()==revision+1,"Seek did not commit once");stage++;}
    case 16->{if(fixture.loading())return;check(fixture.positionMillis()==2250&&fixture.playing(),"Seek position/pause state failed");capture(mc,"14-seek-position");mc.level.disconnect();mc.clearLevel();mc.setScreen(title);stage++;}
    case 17->{check(mc.level==null&&fixture.playing(),"Leaving world stopped playback");capture(mc,"15-title-after-world");Files.writeString(FabricLoader.getInstance().getGameDir().resolve("smoke-success.txt"),"Minecraft 1.20.4 client: RU/EN, local audio metadata, artwork, playback controls, queue, shuffle and settings verified. / Проверен настоящий клиент Minecraft 1.20.4.\n");mc.stop();stage++;}
    default->{}
   }
  }catch(Throwable e){e.printStackTrace();try{Files.writeString(FabricLoader.getInstance().getGameDir().resolve("smoke-failure.txt"),e.toString());}catch(Exception ignored){}System.exit(1);}
 }
}
