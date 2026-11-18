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
 private int stage,ticks,wait;private Player fixture;private Screen title;
 @Override public void onInitializeClient(){if(!Boolean.getBoolean("invu.smoke"))return;ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private void capture(Minecraft mc,String name)throws Exception{Path folder=FabricLoader.getInstance().getGameDir().resolve("screenshots");Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name+".png"));}}
 private void click(Minecraft mc,String key){var widget=Screens.getButtons(mc.screen).stream().filter(b->b.getMessage().equals(Component.translatable(key))).findFirst().orElseThrow();check(mc.screen.mouseClicked(widget.getX()+widget.getWidth()/2.0,widget.getY()+widget.getHeight()/2.0,0),"Click ignored: "+key);mc.screen.mouseReleased(widget.getX()+widget.getWidth()/2.0,widget.getY()+widget.getHeight()/2.0,0);}
 private void tick(Minecraft mc){
  try{if(++ticks>3600)throw new AssertionError("Client verification timed out at stage "+stage);if(mc.getOverlay()!=null)return;if(wait++<15)return;wait=0;
   switch(stage){
    case 0->{if(!(mc.screen instanceof TitleScreen)||InvuMusicPlayerClient.player==null)return;title=mc.screen;SmokePlayerFactory.verifyBundledCodecs();fixture=SmokePlayerFactory.create(FabricLoader.getInstance().getGameDir());InvuMusicPlayerClient.player.close();InvuMusicPlayerClient.player=fixture;fixture.refreshLocal();stage++;}
    case 1->{if(fixture.items().size()!=3)return;fixture.choose(fixture.items(),0);stage++;}
    case 2->{if(!fixture.playing()||!InvuMusicPlayerClient.cover.ready())return;mc.setScreen(title);stage++;}
    case 3->{capture(mc,"01-title-mini-player");check(MenuPlayerPanel.isVisible(mc.screen),"Title mini player hidden");mc.setScreen(new PlayerScreen());stage++;}
    case 4->{capture(mc,"02-main-player");click(mc,"invumusicplayer.player.pause");check(!fixture.playing(),"Pause failed");click(mc,"invumusicplayer.player.play");check(fixture.playing(),"Resume failed");var before=fixture.queue();click(mc,"invumusicplayer.player.shuffle");check(fixture.settings.shuffle&&!fixture.queue().equals(before),"Shuffle failed");click(mc,"invumusicplayer.player.shuffle");check(fixture.queue().equals(before),"Shuffle restore failed");click(mc,"invumusicplayer.player.library");stage++;}
    case 5->{check(mc.screen instanceof LibraryScreen,"Library missing");capture(mc,"03-library");click(mc,"invumusicplayer.player.done");mc.setScreen(new SettingsScreen(mc.screen));stage++;}
    case 6->{capture(mc,"04-settings");click(mc,"invumusicplayer.tabs.visibility");stage++;}
    case 7->{capture(mc,"05-mini-settings");check(Component.translatable("invumusicplayer.tabs.visibility").getString().equals("Mini player"),"English translation missing");mc.getLanguageManager().setSelected("ru_ru");mc.options.languageCode="ru_ru";mc.reloadResourcePacks();stage++;}
    case 8->{check(Component.translatable("invumusicplayer.tabs.visibility").getString().equals("Мини-плеер"),"Russian translation missing");capture(mc,"06-ru-mini-settings");mc.setScreen(new PlayerScreen());stage++;}
    case 9->{capture(mc,"07-ru-main-player");mc.setScreen(new SettingsScreen(mc.screen));stage++;}
    case 10->{capture(mc,"08-ru-settings");Files.writeString(FabricLoader.getInstance().getGameDir().resolve("smoke-success.txt"),"Minecraft 1.20.4 client: RU/EN, local audio metadata, artwork, playback controls, queue, shuffle and settings verified. / Проверен настоящий клиент Minecraft 1.20.4.\n");mc.stop();stage++;}
    default->{}
   }
  }catch(Throwable e){e.printStackTrace();try{Files.writeString(FabricLoader.getInstance().getGameDir().resolve("smoke-failure.txt"),e.toString());}catch(Exception ignored){}System.exit(1);}
 }
}
