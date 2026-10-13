package dev.invumusicplayer.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WaveSettingsScreen extends Screen {
    private final Screen parent;
    public WaveSettingsScreen(Screen parent){super(Component.translatable("invumusicplayer.wave.settings"));this.parent=parent;}
    @Override protected void init(){int x=width/2-155,y=56;var p=InvuMusicPlayerClient.player;var s=p.settings;
        String[] moods={"all","fun","sad","calm","active"};for(int i=0;i<moods.length;i++){String v=moods[i];choice(x+i%3*105,y+i/3*25,100,"invumusicplayer.mood."+v,()->s.waveMood.equals(v),()->s.waveMood=v);}
        String[] diversity={"default","favorite","popular","discover"};for(int i=0;i<diversity.length;i++){String v=diversity[i];choice(x+i%2*160,y+60+i/2*25,150,"invumusicplayer.diversity."+v,()->s.waveDiversity.equals(v),()->s.waveDiversity=v);}
        String[] language={"any","russian","not-russian"};for(int i=0;i<language.length;i++){String v=language[i];choice(x+i*105,y+115,100,"invumusicplayer.wave_language."+v,()->s.waveLanguage.equals(v),()->s.waveLanguage=v);}
        addRenderableWidget(new ModernButton(x,height-30,150,20,Component.translatable("invumusicplayer.wave.apply"),b->{s.waveCustom=true;p.saveSettings();if(!p.spotifySelected()&&p.connected()){p.startWave();onClose();}else minecraft.gui.setScreen(AccountScreen.open(this));}));
        addRenderableWidget(new ModernButton(x+160,height-30,150,20,Component.translatable("invumusicplayer.settings.back"),b->onClose()));
    }
    private void choice(int x,int y,int w,String key,java.util.function.BooleanSupplier selected,Runnable action){var b=new ModernButton(x,y,w,20,Component.translatable(key),v->{action.run();var p=InvuMusicPlayerClient.player;p.settings.waveCustom=true;p.saveSettings();});b.selected(selected);addRenderableWidget(b);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xf00c1220);g.text(font,title.getString(),width/2-155,20,0xffedf2fb,false);super.extractRenderState(g,mx,my,delta);}
    @Override public void onClose(){InvuMusicPlayerClient.player.saveSettings();minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return parent!=null&&parent.isPauseScreen();}
}
