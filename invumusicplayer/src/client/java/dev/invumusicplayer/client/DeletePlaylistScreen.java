package dev.invumusicplayer.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DeletePlaylistScreen extends Screen {
    private final Screen parent;private final String name;private final boolean remote;private final Runnable delete;
    public DeletePlaylistScreen(Screen parent,String name,boolean remote,Runnable delete){super(Component.translatable("invumusicplayer.library.delete"));this.parent=parent;this.name=name;this.remote=remote;this.delete=delete;}
    @Override protected void init(){int x=width/2-155,y=height/2+25;addRenderableWidget(new ModernButton(x,y,150,20,Component.translatable("invumusicplayer.library.confirm_delete"),v->{delete.run();onClose();}));addRenderableWidget(new ModernButton(x+160,y,150,20,Component.translatable("invumusicplayer.library.cancel"),v->onClose()));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){g.fill(0,0,width,height,0xf00c1220);int x=width/2-155,y=height/2-35;g.text(font,title.getString(),x,y,0xffedf2fb,false);g.text(font,font.plainSubstrByWidth(name,310),x,y+18,0xffbdc6d5,false);g.text(font,UiText.text(remote?"invumusicplayer.library.delete_remote":"invumusicplayer.library.delete_local"),x,y+36,0xffe1b37d,false);super.extractRenderState(g,mx,my,d);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return parent!=null&&parent.isPauseScreen();}
}
