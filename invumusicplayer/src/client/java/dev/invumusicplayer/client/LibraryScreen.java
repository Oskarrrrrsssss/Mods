package dev.invumusicplayer.client;

import dev.invumusicplayer.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class LibraryScreen extends Screen {
    private final Screen parent;private final Player player=InvuMusicPlayerClient.player;private int tab,page;private List<?> seen=List.of();private EditBox link,name;
    private String linkValue="", nameValue="";
    private ModernButton status;private final Map<String,CoverTexture> artwork=new HashMap<>();
    private Set<String> seenSaved=Set.of();
    public LibraryScreen(Screen parent){super(Component.translatable("invumusicplayer.player.library"));this.parent=parent;}
    private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(new ModernButton(x,y,w,20,Component.translatable(key),b->action.run()));}
    @Override protected void init(){
        int x=width/2-155,y=tab==1?86:62;String[] tabs={"queue","liked","playlists","tracks"};for(int i=0;i<4;i++){int t=i;button("invumusicplayer.library."+tabs[i],x+i*80,32,74,()->{tab=t;page=0;if(t==1)player.showLiked();if(t==2)player.loadPlaylists();rebuildWidgets();});}
        List<Track> tracks=tab==0?player.queue():tab!=2?player.items():List.of();seen=tab==2?player.playlists():tracks;
        seenSaved=player.savedPlaylists();
        if(tab==1)addRenderableWidget(new ModernButton(x,60,310,20,Component.translatable("invumusicplayer.library.mood_filter",Component.translatable("invumusicplayer.mood."+player.likedMood())),v->{String[] moods={"all","fun","sad","calm","active"};int i=Arrays.asList(moods).indexOf(player.likedMood());player.filterLikedMood(moods[(i+1)%moods.length]);page=0;rebuildWidgets();}));
        int rows=Math.max(1,(height-(tab==1?200:176))/24);int pages=Math.max(1,(seen.size()+rows-1)/rows);page=Math.max(0,Math.min(page,pages-1));
        if(tab!=2)for(int row=0;row<rows;row++){int index=page*rows+row;if(index>=tracks.size())break;Track track=tracks.get(index);
            addRenderableWidget(new ModernButton(x+22,y+row*24,tab==1?232:260,20,Component.literal(font.plainSubstrByWidth(track.title()+" · "+track.artist(),tab==1?216:244)),b->{if(tab==0)player.playQueued(index);else player.choose(tracks,index);}));
            if(tab==1){icon("invumusicplayer.library.assign_mood",x+258,y+row*24,PlayerMenuButton.Icon.MOOD,()->minecraft.gui.setScreen(new TrackMoodScreen(this,track)));}
            if(tab==0){int item=index;button("invumusicplayer.library.remove",x+286,y+row*24,24,()->{player.removeQueued(item);rebuildWidgets();});}
            else button("invumusicplayer.library.add",x+286,y+row*24,24,()->player.enqueue(track));
        }
        if(tab==2){List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>(),deletions=new ArrayList<>();
            for(String title:new TreeSet<>(player.savedPlaylists())){labels.add(title);actions.add(()->player.openSavedPlaylist(title));deletions.add(()->minecraft.gui.setScreen(new DeletePlaylistScreen(this,title,false,()->player.deleteSavedPlaylist(title))));}
            for(YandexMusic.Playlist p:player.playlists()){labels.add(p.title());actions.add(()->player.openPlaylist(p.owner(),p.kind()));deletions.add(()->minecraft.gui.setScreen(new DeletePlaylistScreen(this,p.title(),true,()->player.deletePlaylist(p))));}
            page=Math.max(0,Math.min(page,Math.max(0,(labels.size()-1)/rows)));
            for(int row=0;row<rows;row++){int index=page*rows+row;if(index>=labels.size())break;Runnable action=actions.get(index);addRenderableWidget(new ModernButton(x,y+row*24,282,20,Component.literal(font.plainSubstrByWidth(labels.get(index),266)),b->{action.run();tab=3;rebuildWidgets();}));Runnable deletion=deletions.get(index);icon("invumusicplayer.library.delete",x+286,y+row*24,PlayerMenuButton.Icon.TRASH,deletion);}
        }
        status=addRenderableWidget(new ModernButton(x,height-108,310,18,Component.empty(),b->{}));
        link=new EditBox(font,x,height-84,222,20,Component.translatable("invumusicplayer.library.link"));link.setHint(Component.translatable("invumusicplayer.library.link"));link.setMaxLength(2048);link.setValue(linkValue);link.setResponder(v->linkValue=v);addRenderableWidget(link);button("invumusicplayer.library.import",x+228,height-84,82,()->{player.importLink(link.getValue());tab=3;rebuildWidgets();});
        name=new EditBox(font,x,height-58,150,20,Component.translatable("invumusicplayer.library.name"));name.setHint(Component.translatable("invumusicplayer.library.name"));name.setMaxLength(80);name.setValue(nameValue);name.setResponder(v->nameValue=v);addRenderableWidget(name);button("invumusicplayer.library.save",x+156,height-58,154,()->{player.savePlaylist(name.getValue());tab=2;rebuildWidgets();});
        button("invumusicplayer.library.prev",x,height-30,35,()->{page--;rebuildWidgets();});button("invumusicplayer.library.next",x+40,height-30,35,()->{page++;rebuildWidgets();});button("invumusicplayer.library.clear",x+80,height-30,70,()->{player.clearQueue();rebuildWidgets();});button("invumusicplayer.player.done",x+160,height-30,150,this::onClose);
    }
    @Override public void tick(){super.tick();List<?> current=tab==0?player.queue():tab!=2?player.items():player.playlists();if(!seen.equals(current)||!seenSaved.equals(player.savedPlaylists()))rebuildWidgets();if(status!=null){String text=player.error().isBlank()?player.status():player.error();status.setMessage(Component.literal(font.plainSubstrByWidth(text,298)));status.setTooltip(Tooltip.create(Component.literal(text)));}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xf00c1220);g.text(font,title.getString(),width/2-155,13,0xffedf2fb,false);super.extractRenderState(g,mx,my,delta);
        int x=width/2-155,y=tab==1?86:62,rows=Math.max(1,(height-(tab==1?200:176))/24);
        if(tab==1)for(var widget:children())if(widget instanceof ModernButton v&&v.getY()==60)v.setMessage(Component.translatable("invumusicplayer.library.mood_filter",Component.translatable("invumusicplayer.mood."+player.likedMood())));
        if(tab!=2&&player.settings.covers)for(int row=0;row<rows;row++){int index=page*rows+row;if(index>=seen.size())break;Track t=(Track)seen.get(index);if(t.youtube())continue;if(!artwork.containsKey(t.id())&&artwork.size()>=64){String old=artwork.keySet().iterator().next();artwork.remove(old).close();}CoverTexture texture=artwork.computeIfAbsent(t.id(),id->new CoverTexture());texture.update(player.coverFor(t));texture.draw(g,x,y+row*24+1,18,player.settings.accent);}
    }
    private void icon(String key,int x,int y,PlayerMenuButton.Icon icon,Runnable action){var v=new PlayerMenuButton(x,y,24,Component.translatable(key),icon,false,w->action.run());v.setTooltip(Tooltip.create(Component.translatable(key)));addRenderableWidget(v);}
    public long readyArtworkCount(){return artwork.values().stream().filter(CoverTexture::ready).count();}
    @Override public void removed(){artwork.values().forEach(CoverTexture::close);artwork.clear();super.removed();}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return parent!=null&&parent.isPauseScreen();}
}
