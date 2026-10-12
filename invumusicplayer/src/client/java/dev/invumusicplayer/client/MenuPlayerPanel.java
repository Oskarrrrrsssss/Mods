package dev.invumusicplayer.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Album card in the title and pause menus, sized to the free space beside native controls. */
final class MenuPlayerPanel {
    private final int x=8,width,top,art,bodyHeight;
    private MenuPlayerPanel(int width,int height,boolean title){this.width=width;top=title?68:32;art=Math.max(0,Math.min(width-16,height-top-8-72-43));bodyHeight=art+43;}
    static void install(Screen screen,int w,int h){
        int first=Screens.getWidgets(screen).stream().filter(v->v instanceof Button&&!(v instanceof PlayerMenuButton)).mapToInt(v->v.getX()).min().orElse(w/2-102);
        var p=new MenuPlayerPanel(Math.min(228,Math.max(32,first-16)),h,screen instanceof net.minecraft.client.gui.screens.TitleScreen);
        p.add(screen,8,p.width,"invumusicplayer.menu.open",PlayerMenuButton.Icon.MUSIC,true,()->Minecraft.getInstance().gui.setScreen(new PlayerScreen()));
        int base=p.top+p.bodyHeight+4,part=(p.width-8)/3;
        p.row(screen,base,part,new String[]{"menu.previous","player.pause","menu.next"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.PREVIOUS,PlayerMenuButton.Icon.PAUSE,PlayerMenuButton.Icon.NEXT},new Runnable[]{()->InvuMusicPlayerClient.player.previous(),()->InvuMusicPlayerClient.player.togglePause(),()->InvuMusicPlayerClient.player.next()});
        p.row(screen,base+24,part,new String[]{"player.like","player.shuffle","player.dislike"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.LIKE,PlayerMenuButton.Icon.SHUFFLE,PlayerMenuButton.Icon.DISLIKE},new Runnable[]{()->InvuMusicPlayerClient.player.rate(true),()->InvuMusicPlayerClient.player.toggleShuffle(),()->InvuMusicPlayerClient.player.rate(false)});
        p.row(screen,base+48,part,new String[]{"menu.settings","wave.settings","player.library"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.SETTINGS,PlayerMenuButton.Icon.WAVE,PlayerMenuButton.Icon.LIBRARY},new Runnable[]{()->Minecraft.getInstance().gui.setScreen(new SettingsScreen(screen)),()->Minecraft.getInstance().gui.setScreen(new WaveSettingsScreen(screen)),()->Minecraft.getInstance().gui.setScreen(new LibraryScreen(screen))});
        ScreenEvents.afterExtract(screen).register((s,g,mx,my,delta)->p.draw(g));
    }
    private void row(Screen s,int y,int part,String[] keys,PlayerMenuButton.Icon[] icons,Runnable[] actions){for(int i=0;i<3;i++)addAt(s,x+i*(part+4),y,i==2?width-2*(part+4):part,"invumusicplayer."+keys[i],icons[i],false,actions[i]);}
    private void add(Screen s,int y,int w,String key,PlayerMenuButton.Icon icon,boolean label,Runnable action){addAt(s,x,y,w,key,icon,label,action);}
    private void addAt(Screen s,int x,int y,int w,String key,PlayerMenuButton.Icon icon,boolean label,Runnable action){var b=new PlayerMenuButton(x,y,w,Component.translatable(key),icon,label,v->action.run());b.setTooltip(Tooltip.create(Component.translatable(key)));Screens.getWidgets(s).add(b);}
    private void draw(GuiGraphicsExtractor g){
        var p=InvuMusicPlayerClient.player;var s=p.settings;var t=p.current();int color=PlayerMenuButton.themeColor();
        PlayerMenuButton.rounded(g,x,top,width,bodyHeight,0xff000000|color);
        boolean hasArt=art>0&&s.covers&&t!=null&&!t.youtube();int titleY=top+8;
        if(hasArt){InvuMusicPlayerClient.cover.draw(g,x+(width-art)/2,top+6,art,s.accent);titleY+=art+6;}
        var f=Minecraft.getInstance().font;
        center(g,t==null?UiText.text("invumusicplayer.player.choose_track"):t.title(),titleY,0xff000000|s.textColor);
        center(g,t==null?UiText.text("invumusicplayer.menu.empty"):t.artist(),titleY+12,0xad000000|s.textColor);
        int barY=top+bodyHeight-8;g.fill(x+8,barY,x+width-8,barY+2,0x408d8274);
        if(p.durationMillis()>0)g.fill(x+8,barY,x+8+(int)((width-16)*Math.min(1,(double)p.positionMillis()/p.durationMillis())),barY+2,0xffe8e2d6);
    }
    private void center(GuiGraphicsExtractor g,String text,int y,int color){var f=Minecraft.getInstance().font;String clipped=f.plainSubstrByWidth(text,Math.max(0,width-16));g.text(f,clipped,x+(width-f.width(clipped))/2,y,color,false);}
}
