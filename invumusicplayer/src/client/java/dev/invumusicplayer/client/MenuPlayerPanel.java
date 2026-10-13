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
    static int detailShift(){var s=InvuMusicPlayerClient.player.settings;return (s.showTime?0:-12)+(s.visualizer?Math.round(8*s.equalizerHeight)-8:-12);}
    static int baseHeight(){return 342+detailShift();}
    record Layout(int x,int y,float scale){
        int atX(int value){return x+Math.round(value*scale);}int atY(int value){return y+Math.round(value*scale);}
        int controlY(int base){return atY(base+detailShift());}
        int width(){return Math.round(180*scale);}int height(){return Math.round(baseHeight()*scale);}
    }
    static Layout layout(int w,int h,boolean title){
        var s=InvuMusicPlayerClient.player.settings;s.normalize();int defaultTop=title?68:8;
        float scale=Math.min(s.menuScale,Math.min((w-16)/180f,(h-(s.menuPositioned?16:defaultTop+20))/(float)baseHeight()));scale=Math.max(.2f,scale);
        int pw=Math.round(180*scale),ph=Math.round(baseHeight()*scale);
        int x=s.menuPositioned?8+Math.round(s.menuX*Math.max(0,w-pw-16)):8;
        int y=s.menuPositioned?8+Math.round(s.menuY*Math.max(0,h-ph-16)):defaultTop;
        return new Layout(Math.max(0,Math.min(x,w-pw)),Math.max(0,Math.min(y,h-ph)),scale);
    }
    static boolean isVisible(Screen screen){var s=InvuMusicPlayerClient.player.settings;return s.playersVisible&&(screen instanceof net.minecraft.client.gui.screens.TitleScreen?s.titlePlayer:screen instanceof net.minecraft.client.gui.screens.PauseScreen&&s.pausePlayer);}
    static void install(Screen screen,int w,int h){
        if(!isVisible(screen))return;
        Layout l=layout(w,h,screen instanceof net.minecraft.client.gui.screens.TitleScreen);
        row(screen,l,228,new String[]{"menu.previous","player.pause","menu.next"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.PREVIOUS,PlayerMenuButton.Icon.PAUSE,PlayerMenuButton.Icon.NEXT},new Runnable[]{()->InvuMusicPlayerClient.player.previous(),()->InvuMusicPlayerClient.player.togglePause(),()->InvuMusicPlayerClient.player.next()});
        row(screen,l,252,new String[]{"player.like","player.shuffle","player.dislike","player.time"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.LIKE,PlayerMenuButton.Icon.SHUFFLE,PlayerMenuButton.Icon.DISLIKE,PlayerMenuButton.Icon.CLOCK},new Runnable[]{()->InvuMusicPlayerClient.player.rate(true),()->InvuMusicPlayerClient.player.toggleShuffle(),()->InvuMusicPlayerClient.player.rate(false),()->{var p=InvuMusicPlayerClient.player;p.settings.showTime=!p.settings.showTime;p.saveSettings();Minecraft.getInstance().resizeGui();}});
        row(screen,l,276,new String[]{"menu.open","menu.settings","wave.settings","player.library","menu.position"},new PlayerMenuButton.Icon[]{PlayerMenuButton.Icon.MUSIC,PlayerMenuButton.Icon.SETTINGS,PlayerMenuButton.Icon.WAVE,PlayerMenuButton.Icon.LIBRARY,PlayerMenuButton.Icon.MOVE},new Runnable[]{()->Minecraft.getInstance().gui.setScreen(new PlayerScreen()),()->Minecraft.getInstance().gui.setScreen(new SettingsScreen(screen)),()->Minecraft.getInstance().gui.setScreen(new WaveSettingsScreen(screen)),()->Minecraft.getInstance().gui.setScreen(new LibraryScreen(screen)),()->Minecraft.getInstance().gui.setScreen(new MenuEditor(screen))});
        Screens.getWidgets(screen).add(new SeekSlider(l.x(),l.controlY(210),l.width(),l.scale(),false));
        Screens.getWidgets(screen).add(new ValueSlider(l.x(),l.controlY(314),l.width(),"invumusicplayer.slider.volume",InvuMusicPlayerClient.player.settings.volume*100,0,100,l.scale(),v->InvuMusicPlayerClient.player.volume((float)v/100)));
        ScreenEvents.afterExtract(screen).register((s,g,mx,my,delta)->draw(g,l));
    }
    private static void row(Screen s,Layout l,int y,String[] keys,PlayerMenuButton.Icon[] icons,Runnable[] actions){int part=180/keys.length;for(int i=0;i<keys.length;i++){
        int index=i;String key="invumusicplayer."+keys[i];var button=new PlayerMenuButton(l.atX(i*part),l.controlY(y),Math.round((part-4)*l.scale()),Component.translatable(key),icons[i],false,l.scale(),b->actionsFor(actions,index));
        button.setTooltip(Tooltip.create(Component.translatable(key)));Screens.getWidgets(s).add(button);
    }}
    private static void actionsFor(Runnable[] actions,int i){actions[i].run();}
    private static final AlbumBackdrop backdrop=new AlbumBackdrop();
    private static final AudioVisualizer visualizer=new AudioVisualizer();
    static void draw(GuiGraphicsExtractor g,Layout l){
        var p=InvuMusicPlayerClient.player;var s=p.settings;var t=p.current();
        g.pose().pushMatrix();g.pose().translate((float)l.x(),(float)l.y());g.pose().scale(l.scale());
        PlayerMenuButton.rounded(g,0,0,180,208+detailShift(),0xff000000|PlayerMenuButton.themeColor());
        boolean art=s.covers&&t!=null&&!t.youtube();if(art){g.enableScissor(0,0,180,208+detailShift());backdrop.draw(g,16,6,148);g.disableScissor();InvuMusicPlayerClient.cover.draw(g,16,6,148,s.accent);}
        int ty=art?160:12;center(g,t==null?UiText.text("invumusicplayer.player.choose_track"):t.title(),ty,0xff000000|s.textColor);
        center(g,t==null?UiText.text("invumusicplayer.menu.empty"):t.artist(),ty+12,0xad000000|s.textColor);
        if(s.showTime)center(g,PlayerHud.time(p.positionMillis())+" / "+PlayerHud.time(p.durationMillis()),184,0xad000000|s.textColor);
        if(s.visualizer)visualizer.draw(g,p.spectrum(),8,s.showTime?196:184,164,Math.round(8*s.equalizerHeight),PlayerMenuButton.themeAccent());
        g.pose().popMatrix();
    }
    private static void center(GuiGraphicsExtractor g,String text,int y,int color){var f=Minecraft.getInstance().font;String clipped=f.plainSubstrByWidth(text,164);g.text(f,clipped,(180-f.width(clipped))/2,y,color,false);}
}
