package dev.invumusicplayer.client;

import dev.invumusicplayer.core.Track;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TrackMoodScreen extends Screen {
    private final Screen parent;private final Track track;
    public TrackMoodScreen(Screen parent,Track track){super(Component.translatable("invumusicplayer.library.assign_mood"));this.parent=parent;this.track=track;}
    @Override protected void init(){int x=width/2-155,y=height/2-20;String[] moods={"all","fun","sad","calm","active"};for(int i=0;i<moods.length;i++){String v=moods[i];var b=new ModernButton(x+i%3*105,y+i/3*25,100,20,Component.translatable(v.equals("all")?"invumusicplayer.library.untagged":"invumusicplayer.mood."+v),w->{InvuMusicPlayerClient.player.setTrackMood(track,v);onClose();});b.selected(()->InvuMusicPlayerClient.player.trackMood(track).equals(v));addRenderableWidget(b);}addRenderableWidget(new ModernButton(x+80,height-30,150,20,Component.translatable("invumusicplayer.settings.back"),v->onClose()));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){g.fill(0,0,width,height,0xf00c1220);g.text(font,title.getString(),width/2-155,25,0xffedf2fb,false);g.text(font,font.plainSubstrByWidth(track.display(),310),width/2-155,height/2-44,0xffbdc6d5,false);super.extractRenderState(g,mx,my,d);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return parent!=null&&parent.isPauseScreen();}
}
