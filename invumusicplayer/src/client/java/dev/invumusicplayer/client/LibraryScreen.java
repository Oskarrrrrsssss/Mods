package dev.invumusicplayer.client;

import dev.invumusicplayer.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class LibraryScreen extends Screen {
    private final Screen parent;private final Player player=InvuMusicPlayerClient.player;private int tab,page;private List<?> seen=List.of();private EditBox link,name;
    public LibraryScreen(Screen parent){super(Component.translatable("invumusicplayer.player.library"));this.parent=parent;}
    private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(new ModernButton(x,y,w,20,Component.translatable(key),b->action.run()));}
    @Override protected void init(){
        int x=width/2-155,y=62;String[] tabs={"queue","liked","playlists","tracks"};for(int i=0;i<4;i++){int t=i;button("invumusicplayer.library."+tabs[i],x+i*80,32,74,()->{tab=t;page=0;if(t==1)player.showLiked();if(t==2)player.loadPlaylists();rebuildWidgets();});}
        List<Track> tracks=tab==0?player.queue():tab!=2?player.items():List.of();seen=tab==2?player.playlists():tracks;
        int rows=Math.max(1,(height-150)/24);int pages=Math.max(1,(seen.size()+rows-1)/rows);page=Math.max(0,Math.min(page,pages-1));
        if(tab!=2)for(int row=0;row<rows;row++){int index=page*rows+row;if(index>=tracks.size())break;Track track=tracks.get(index);
            addRenderableWidget(new ModernButton(x,y+row*24,282,20,Component.literal(font.plainSubstrByWidth(track.title()+" · "+track.artist(),266)),b->player.choose(tracks,index)));
            if(tab==0){int item=index;button("invumusicplayer.library.remove",x+286,y+row*24,24,()->{player.removeQueued(item);rebuildWidgets();});}
            else button("invumusicplayer.library.add",x+286,y+row*24,24,()->player.enqueue(track));
        }
        if(tab==2){List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
            for(String title:new TreeSet<>(player.savedPlaylists())){labels.add(title);actions.add(()->player.openSavedPlaylist(title));}
            for(YandexMusic.Playlist p:player.playlists()){labels.add(p.title());actions.add(()->player.openPlaylist(p.owner(),p.kind()));}
            page=Math.max(0,Math.min(page,Math.max(0,(labels.size()-1)/rows)));
            for(int row=0;row<rows;row++){int index=page*rows+row;if(index>=labels.size())break;Runnable action=actions.get(index);addRenderableWidget(new ModernButton(x,y+row*24,310,20,Component.literal(labels.get(index)),b->{action.run();tab=3;rebuildWidgets();}));}
        }
        link=new EditBox(font,x,height-84,222,20,Component.translatable("invumusicplayer.library.link"));link.setHint(Component.translatable("invumusicplayer.library.link"));link.setMaxLength(2048);addRenderableWidget(link);button("invumusicplayer.library.import",x+228,height-84,82,()->{player.importLink(link.getValue());tab=3;rebuildWidgets();});
        name=new EditBox(font,x,height-58,150,20,Component.translatable("invumusicplayer.library.name"));name.setHint(Component.translatable("invumusicplayer.library.name"));name.setMaxLength(80);addRenderableWidget(name);button("invumusicplayer.library.save",x+156,height-58,154,()->{player.savePlaylist(name.getValue());tab=2;rebuildWidgets();});
        button("invumusicplayer.library.prev",x,height-30,35,()->{page--;rebuildWidgets();});button("invumusicplayer.library.next",x+40,height-30,35,()->{page++;rebuildWidgets();});button("invumusicplayer.library.clear",x+80,height-30,70,()->{player.clearQueue();rebuildWidgets();});button("invumusicplayer.player.done",x+160,height-30,150,this::onClose);
    }
    @Override public void tick(){super.tick();List<?> current=tab==0?player.queue():tab!=2?player.items():player.playlists();if(!seen.equals(current))rebuildWidgets();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xf00c1220);g.text(font,title.getString(),width/2-155,13,0xffedf2fb,false);super.extractRenderState(g,mx,my,delta);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
