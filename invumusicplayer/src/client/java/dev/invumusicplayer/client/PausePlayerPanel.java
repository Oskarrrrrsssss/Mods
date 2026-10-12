package dev.invumusicplayer.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The pause menu keeps its own fixed top-left player, independent of the movable gameplay HUD. */
final class PausePlayerPanel {
    private final int x=8,y=8,width;
    private final AudioVisualizer visualizer=new AudioVisualizer();
    private PausePlayerPanel(int width){this.width=width;}
    static void install(Screen screen,int scaledWidth) {
        int firstMenuX=Screens.getWidgets(screen).stream().filter(w->w instanceof net.minecraft.client.gui.components.Button)
            .mapToInt(w->w.getX()).min().orElse(scaledWidth/2-102);
        PausePlayerPanel panel=new PausePlayerPanel(Math.min(228,Math.max(32,firstMenuX-16)));
        panel.button(screen,0,panel.width,"invumusicplayer.menu.open",PlayerMenuButton.Icon.MUSIC,true,b->Minecraft.getInstance().gui.setScreen(new PlayerScreen()));
        int usable=panel.width-8,part=usable/3;
        panel.button(screen,84,part,"invumusicplayer.menu.previous",PlayerMenuButton.Icon.PREVIOUS,false,b->InvuMusicPlayerClient.player.previous());
        panel.add(screen,panel.x+part+4,panel.y+84,part,"invumusicplayer.player.pause",PlayerMenuButton.Icon.PAUSE,false,b->InvuMusicPlayerClient.player.togglePause());
        panel.add(screen,panel.x+2*(part+4),panel.y+84,panel.width-2*(part+4),"invumusicplayer.menu.next",PlayerMenuButton.Icon.NEXT,false,b->InvuMusicPlayerClient.player.next());
        panel.button(screen,110,panel.width,"invumusicplayer.menu.settings",PlayerMenuButton.Icon.SETTINGS,true,b->Minecraft.getInstance().gui.setScreen(new SettingsScreen(screen)));
        ScreenEvents.afterExtract(screen).register((s,g,mx,my,delta)->panel.draw(g));
    }
    private void button(Screen screen,int offset,int w,String key,PlayerMenuButton.Icon icon,boolean label,net.minecraft.client.gui.components.Button.OnPress action){add(screen,x,y+offset,w,key,icon,label,action);}
    private void add(Screen screen,int bx,int by,int w,String key,PlayerMenuButton.Icon icon,boolean label,net.minecraft.client.gui.components.Button.OnPress action){
        var button=new PlayerMenuButton(bx,by,w,Component.translatable(key),icon,label,action);
        button.setTooltip(Tooltip.create(Component.translatable(key.equals("invumusicplayer.menu.settings")?"invumusicplayer.menu.settings_tooltip":key)));
        Screens.getWidgets(screen).add(button);
    }
    private void draw(GuiGraphicsExtractor g) {
        var player=InvuMusicPlayerClient.player;var s=player.settings;var track=player.current();
        int top=y+24,height=54;
        PlayerMenuButton.rounded(g,x,top+1,width,height,0x48000000);
        PlayerMenuButton.rounded(g,x,top,width,height,((int)(s.opacity*255)<<24)|s.background);
        int textX=x+8;
        if(width>=156&&s.covers&&track!=null&&!track.youtube()){InvuMusicPlayerClient.cover.draw(g,x+7,top+8,36,s.accent);textX=x+50;}
        var font=Minecraft.getInstance().font;int textWidth=Math.max(0,width-(textX-x)-7);
        g.text(font,font.plainSubstrByWidth(track==null?UiText.text("invumusicplayer.player.choose_track"):track.title(),textWidth),textX,top+8,0xff000000|s.textColor,false);
        g.text(font,font.plainSubstrByWidth(track==null?UiText.text("invumusicplayer.menu.empty"):track.artist(),textWidth),textX,top+20,0xa8000000|s.textColor,false);
        if(s.visualizer)visualizer.draw(g,player.spectrum(),textX,top+32,textWidth,15,s.accent);
        else g.text(font,font.plainSubstrByWidth(PlayerHud.time(player.positionMillis())+" / "+PlayerHud.time(player.durationMillis()),textWidth),textX,top+36,0xa8000000|s.textColor,false);
    }
}
