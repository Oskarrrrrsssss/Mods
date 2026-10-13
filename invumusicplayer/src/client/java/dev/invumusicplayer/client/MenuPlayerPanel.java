package dev.invumusicplayer.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Independent menu geometry and native input coordinates, with scaled content. */
final class MenuPlayerPanel {
    record Layout(int x,int y,float scale){
        int atX(int value){return x+Math.round(value*scale);}int atY(int value){return y+Math.round(value*scale);}
        int width(){return Math.round(180*scale);}int height(){return Math.round(310*scale);}
    }
    static Layout layout(int w,int h,boolean title){
        var s=InvuMusicPlayerClient.player.settings;s.normalize();int defaultTop=title?68:8;
        float scale=Math.min(s.menuScale,Math.min((w-16)/180f,(h-(s.menuPositioned?16:defaultTop+20))/310f));scale=Math.max(.2f,scale);
        int pw=Math.round(180*scale),ph=Math.round(310*scale);
        int x=s.menuPositioned?8+Math.round(s.menuX*Math.max(0,w-pw-16)):8;
        int y=s.menuPositioned?8+Math.round(s.menuY*Math.max(0,h-ph-16)):defaultTop;
        return new Layout(Math.max(0,Math.min(x,w-pw)),Math.max(0,Math.min(y,h-ph)),scale);
    }
    static boolean isVisible(Screen screen){var s=InvuMusicPlayerClient.player.settings;return s.playersVisible&&(screen instanceof net.minecraft.client.gui.screens.TitleScreen?s.titlePlayer:screen instanceof net.minecraft.client.gui.screens.PauseScreen&&s.pausePlayer);}
    static void install(Screen screen,int w,int h){
        if(!isVisible(screen))return;
        Layout l=layout(w,h,screen instanceof net.minecraft.client.gui.screens.TitleScreen);
        row(screen,l,206,new String[]{"menu.previous","player.pause","menu.next"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.PREVIOUS,PlayerMenuButton.Icon.PAUSE,PlayerMenuButton.Icon.NEXT},new Runnable[]{()->InvuMusicPlayerClient.player.previous(),()->InvuMusicPlayerClient.player.togglePause(),()->InvuMusicPlayerClient.player.next()});
        row(screen,l,230,new String[]{"player.like","player.shuffle","player.dislike"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.LIKE,PlayerMenuButton.Icon.SHUFFLE,PlayerMenuButton.Icon.DISLIKE},new Runnable[]{()->InvuMusicPlayerClient.player.rate(true),()->InvuMusicPlayerClient.player.toggleShuffle(),()->InvuMusicPlayerClient.player.rate(false)});
        row(screen,l,254,new String[]{"menu.open","menu.settings","wave.settings","player.library","menu.position"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.MUSIC,PlayerMenuButton.Icon.SETTINGS,PlayerMenuButton.Icon.WAVE,PlayerMenuButton.Icon.LIBRARY,PlayerMenuButton.Icon.MOVE},new Runnable[]{()->Minecraft.getInstance().gui.setScreen(new PlayerScreen()),()->Minecraft.getInstance().gui.setScreen(new SettingsScreen(screen)),()->Minecraft.getInstance().gui.setScreen(new WaveSettingsScreen(screen)),()->Minecraft.getInstance().gui.setScreen(new LibraryScreen(screen)),()->Minecraft.getInstance().gui.setScreen(new MenuEditor(screen))});
        Screens.getWidgets(screen).add(new ValueSlider(l.x(),l.atY(290),l.width(),"invumusicplayer.slider.volume",InvuMusicPlayerClient.player.settings.volume*100,0,100,l.scale(),v->InvuMusicPlayerClient.player.volume((float)v/100)));
        ScreenEvents.afterExtract(screen).register((s,g,mx,my,delta)->draw(g,l));
    }
    private static void row(Screen s,Layout l,int y,String[] keys,PlayerMenuButton.Icon[] icons,Runnable[] actions){int part=180/keys.length;for(int i=0;i<keys.length;i++){
        int index=i;String key="invumusicplayer."+keys[i];var button=new PlayerMenuButton(l.atX(i*part),l.atY(y),Math.round((part-4)*l.scale()),Component.translatable(key),icons[i],false,l.scale(),b->actionsFor(actions,index));
        button.setTooltip(Tooltip.create(Component.translatable(key)));Screens.getWidgets(s).add(button);
    }}
    private static void actionsFor(Runnable[] actions,int i){actions[i].run();}
    static void draw(GuiGraphicsExtractor g,Layout l){
        var p=InvuMusicPlayerClient.player;var s=p.settings;var t=p.current();
        g.pose().pushMatrix();g.pose().translate((float)l.x(),(float)l.y());g.pose().scale(l.scale());
        PlayerMenuButton.rounded(g,0,0,180,202,0xff000000|PlayerMenuButton.themeColor());
        boolean art=s.covers&&t!=null&&!t.youtube();if(art)InvuMusicPlayerClient.cover.draw(g,8,6,164,s.accent);
        int ty=art?176:12;center(g,t==null?UiText.text("invumusicplayer.player.choose_track"):t.title(),ty,0xff000000|s.textColor);
        center(g,t==null?UiText.text("invumusicplayer.menu.empty"):t.artist(),ty+12,0xad000000|s.textColor);
        g.fill(8,196,172,198,0x408d8274);if(p.durationMillis()>0)g.fill(8,196,8+(int)(164*Math.min(1,(double)p.positionMillis()/p.durationMillis())),198,0xffe8e2d6);
        g.pose().popMatrix();
    }
    private static void center(GuiGraphicsExtractor g,String text,int y,int color){var f=Minecraft.getInstance().font;String clipped=f.plainSubstrByWidth(text,164);g.text(f,clipped,(180-f.width(clipped))/2,y,color,false);}
}
