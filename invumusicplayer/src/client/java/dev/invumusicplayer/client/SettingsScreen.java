package dev.invumusicplayer.client;

import dev.invumusicplayer.core.Settings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private final Screen parent; private final Settings s=InvuMusicPlayerClient.player.settings; private int tab;
    public SettingsScreen(Screen parent){super(Component.translatable("invumusicplayer.settings.title"));this.parent=parent;}
    private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(new ModernButton(x,y,w,20,Component.translatable(key),b->action.run()));}
    private void toggle(String key,int x,int y,boolean enabled,Runnable action){addRenderableWidget(new ModernButton(x,y,150,20,Component.translatable(key,UiText.toggle(enabled)),b->{action.run();s.normalize();InvuMusicPlayerClient.player.saveSettings();rebuildWidgets();}));}
    private void slider(String key,int x,int y,double initial,double min,double max,java.util.function.DoubleConsumer action){addRenderableWidget(new ValueSlider(x,y,150,key,initial,min,max,action));}
    @Override protected void init(){
        int x=width/2-155,y=62;String[] tabs={"audio","appearance","lyrics","wave"};
        for(int i=0;i<tabs.length;i++){int selected=i;button("invumusicplayer.tabs."+tabs[i],x+i*80,32,74,()->{tab=selected;rebuildWidgets();});}
        if(tab==0){
            slider("invumusicplayer.slider.volume",x,y,s.volume*100,0,100,v->InvuMusicPlayerClient.player.volume((float)v/100));
            toggle("invumusicplayer.settings.repeat",x+160,y,s.repeat,()->s.repeat=!s.repeat);
            button("invumusicplayer.settings.hotkeys",x,y+30,150,()->minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options)));
            button("invumusicplayer.settings.hud_position",x+160,y+30,150,()->minecraft.gui.setScreen(new HudEditor(this)));
        }else if(tab==1){
            toggle("invumusicplayer.settings.hud",x,y,s.hud,()->s.hud=!s.hud);
            toggle("invumusicplayer.settings.covers",x+160,y,s.covers,()->s.covers=!s.covers);
            toggle("invumusicplayer.settings.visualizer",x,y+25,s.visualizer,()->s.visualizer=!s.visualizer);
            slider("invumusicplayer.slider.opacity",x+160,y+25,s.opacity*100,20,100,v->s.opacity=(float)v/100);
            String[] names={"accent","background","text"};int[] colors={s.accent,s.background,s.textColor};
            for(int i=0;i<3;i++){final int row=i;for(int c=0;c<3;c++){final int shift=16-c*8;final int column=c;int xx=x+c*105;
                addRenderableWidget(new ValueSlider(xx,y+55+i*27,100,"invumusicplayer.color."+names[i]+"_"+new String[]{"r","g","b"}[c],(colors[i]>>shift)&255,0,255,v->{int value=(int)Math.round(v);if(row==0)s.accent=(s.accent&~(255<<shift))|(value<<shift);else if(row==1)s.background=(s.background&~(255<<shift))|(value<<shift);else s.textColor=(s.textColor&~(255<<shift))|(value<<shift);}));}}
        }else if(tab==2){
            toggle("invumusicplayer.settings.lyrics3d",x,y,s.lyrics3d,()->s.lyrics3d=!s.lyrics3d);
            toggle("invumusicplayer.settings.sprint_only",x+160,y,s.sprintOnly,()->s.sprintOnly=!s.sprintOnly);
            slider("invumusicplayer.slider.distance",x,y+30,s.lyricDistance*100,150,800,v->s.lyricDistance=(float)v/100);
            slider("invumusicplayer.slider.height",x+160,y+30,s.lyricHeight*100,-200,200,v->s.lyricHeight=(float)v/100);
            slider("invumusicplayer.slider.lyric_scale",x,y+60,s.lyricScale*10000,100,600,v->s.lyricScale=(float)v/10000);
            slider("invumusicplayer.slider.offset",x+160,y+60,s.lyricOffsetMillis,-10000,10000,v->s.lyricOffsetMillis=(int)v);
        }else{
            String[] moods={"all","fun","sad","calm","active"};for(int i=0;i<moods.length;i++){String mood=moods[i];button("invumusicplayer.mood."+mood,x+(i%3)*105,y+(i/3)*25,100,()->{s.waveMood=mood;InvuMusicPlayerClient.player.saveSettings();});}
            String[] modes={"default","favorite","popular","discover"};for(int i=0;i<modes.length;i++){String mode=modes[i];button("invumusicplayer.diversity."+mode,x+(i%2)*160,y+58+(i/2)*25,150,()->{s.waveDiversity=mode;InvuMusicPlayerClient.player.saveSettings();});}
            button("invumusicplayer.wave.apply",x,y+115,310,()->{InvuMusicPlayerClient.player.startWave();onClose();});
        }
        button("invumusicplayer.settings.back",x+80,height-30,150,this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xf00c1220);g.text(font,title.getString(),width/2-155,13,0xffedf2fb,false);super.extractRenderState(g,mx,my,delta);}
    @Override public void onClose(){s.normalize();InvuMusicPlayerClient.player.saveSettings();minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
