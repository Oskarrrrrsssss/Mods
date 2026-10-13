package dev.invumusicplayer.client;

import dev.invumusicplayer.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class LibraryScreen extends Screen {
    private final Screen parent;private final Player player=InvuMusicPlayerClient.player;
    private int tab,page,pages=1;private List<?> seen=List.of();private Set<String> seenSaved=Set.of();
    private EditBox link,name,search;private String linkValue="",nameValue="",query="";private boolean localOnly;private ModernButton status;
    private final Map<String,CoverTexture> artwork=new HashMap<>();
    public LibraryScreen(Screen parent){super(Component.translatable("invumusicplayer.player.library"));this.parent=parent;localOnly=!player.connected()&&player.listingSection().equals("local");}
    private int listTop(){return tab==0?88:62;}
    private int rows(){return Math.max(1,(height-198-(tab==0?26:0))/24+1);}
    private ModernButton button(String key,int x,int y,int w,Runnable action){return addRenderableWidget(new ModernButton(x,y,w,20,Component.translatable(key),b->action.run()));}
    @Override protected void init(){
        int x=width/2-155,y=62;String[] tabs={"search","liked","playlists","tracks"};
        for(int i=0;i<4;i++){int index=i;button("invumusicplayer.library."+tabs[i],x+i*80,32,74,()->{tab=index;page=0;if(index==1)player.showLiked();if(index==2)player.loadPlaylists();rebuildWidgets();}).selected(()->tab==index);}
        if(tab==0){search=new EditBox(font,x,62,180,20,Component.translatable("invumusicplayer.player.search_hint"));search.setHint(Component.translatable("invumusicplayer.player.search_hint"));search.setMaxLength(256);search.setValue(query);search.setResponder(v->query=v);addRenderableWidget(search);
            button("invumusicplayer.player.search",x+186,62,54,()->{page=0;if(localOnly)player.searchLocal(query);else if(player.connected())player.search(query);else minecraft.gui.setScreen(AccountScreen.open(this));});
            button("invumusicplayer.library.local",x+246,62,64,()->{localOnly=!localOnly;if(localOnly)player.searchLocal(query);rebuildWidgets();}).selected(()->localOnly);
        }
        y=listTop();List<Track> tracks=tab!=2?player.items():List.of();seen=tab==2?player.playlists():tracks;seenSaved=player.savedPlaylists();
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>(),deletions=new ArrayList<>();
        if(tab==2){
            for(String title:new TreeSet<>(seenSaved)){labels.add(title);actions.add(()->player.openSavedPlaylist(title));deletions.add(()->minecraft.gui.setScreen(new DeletePlaylistScreen(this,title,false,()->player.deleteSavedPlaylist(title))));}
            for(MusicPlaylist p:player.playlists()){labels.add(p.title());actions.add(()->player.openPlaylist(p));deletions.add(()->minecraft.gui.setScreen(new DeletePlaylistScreen(this,p.title(),p.spotify()?"invumusicplayer.library.delete_spotify":"invumusicplayer.library.delete_remote",()->player.deletePlaylist(p))));}
        }
        pages=Math.max(1,((tab==2?labels.size():tracks.size())+rows()-1)/rows());page=Math.max(0,Math.min(page,pages-1));
        for(int row=0;row<rows();row++){int index=page*rows()+row;
            if(tab==2){if(index>=labels.size())break;Runnable action=actions.get(index);addRenderableWidget(new ModernButton(x,y+row*24,282,20,Component.literal(font.plainSubstrByWidth(labels.get(index),266)),b->{action.run();tab=3;page=0;rebuildWidgets();}));icon("invumusicplayer.library.delete",x+286,y+row*24,PlayerMenuButton.Icon.TRASH,deletions.get(index));}
            else {if(index>=tracks.size())break;Track track=tracks.get(index);
                var entry=new ModernButton(x+22,y+row*24,260,20,Component.literal(font.plainSubstrByWidth(track.display(),244)),b->{player.choose(tracks,index);});
                entry.selected(()->player.current()!=null&&player.current().id().equals(track.id()));entry.setTooltip(Tooltip.create(Component.literal(track.display())));addRenderableWidget(entry);
                button("invumusicplayer.library.add",x+286,y+row*24,24,()->player.enqueue(track)).setTooltip(Tooltip.create(Component.translatable("invumusicplayer.queue.add_hint")));
            }
        }
        button("invumusicplayer.library.prev",x,height-128,40,()->movePage(-1)).active=page>0;
        button("invumusicplayer.library.next",x+46,height-128,40,()->movePage(1)).active=page+1<pages;
        if(tab==1)button("invumusicplayer.library.play_all",x+160,height-128,150,()->player.choose(tracks,0)).active=!tracks.isEmpty();
        status=addRenderableWidget(new ModernButton(x,height-104,310,16,Component.empty(),b->{}));status.active=false;
        link=new EditBox(font,x,height-80,222,20,Component.translatable("invumusicplayer.library.link"));link.setHint(Component.translatable("invumusicplayer.library.link"));link.setMaxLength(2048);link.setValue(linkValue);link.setResponder(v->linkValue=v);addRenderableWidget(link);
        button("invumusicplayer.library.import",x+228,height-80,82,()->{player.importLink(link.getValue());tab=3;page=0;rebuildWidgets();});
        name=new EditBox(font,x,height-54,150,20,Component.translatable("invumusicplayer.library.name"));name.setHint(Component.translatable("invumusicplayer.library.name"));name.setMaxLength(80);name.setValue(nameValue);name.setResponder(v->nameValue=v);addRenderableWidget(name);
        button("invumusicplayer.library.save",x+156,height-54,154,()->{player.savePlaylist(name.getValue());tab=2;page=0;rebuildWidgets();});
        button("invumusicplayer.player.done",x+80,height-28,150,this::onClose);
    }
    private void movePage(int direction){page=Math.max(0,Math.min(page+direction,pages-1));rebuildWidgets();}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(my>=listTop()&&my<height-136&&mx>=width/2-155&&mx<=width/2+155&&vertical!=0){movePage(vertical>0?-1:1);return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public void tick(){super.tick();List<?> current=tab!=2?player.items():player.playlists();if(!seen.equals(current)||!seenSaved.equals(player.savedPlaylists()))rebuildWidgets();if(status!=null){String text=player.error().isBlank()?player.status():player.error();status.setMessage(Component.literal(font.plainSubstrByWidth(text,298)));status.setTooltip(Tooltip.create(Component.literal(text)));}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){g.fill(0,0,width,height,0xf0000000|UiTheme.background(this));int x=width/2-155;g.text(font,title.getString()+" / "+UiText.text("invumusicplayer.library."+new String[]{"search","liked","playlists","tracks"}[tab]),x,13,0xffedf2fb,false);
        g.text(font,(page+1)+" / "+pages,x+96,height-122,0xffbdc6d5,false);super.extractRenderState(g,mx,my,d);
        if(tab!=2&&player.settings.covers)for(int row=0;row<rows();row++){int index=page*rows()+row;if(index>=seen.size())break;Track t=(Track)seen.get(index);if(t.youtube())continue;
            if(!artwork.containsKey(t.id())&&artwork.size()>=64){String old=artwork.keySet().iterator().next();artwork.remove(old).close();}
            CoverTexture texture=artwork.computeIfAbsent(t.id(),id->new CoverTexture());texture.update(player.coverFor(t));texture.draw(g,x,listTop()+1+row*24,18,player.settings.accent);
        }
    }
    private void icon(String key,int x,int y,PlayerMenuButton.Icon icon,Runnable action){var b=new PlayerMenuButton(x,y,24,Component.translatable(key),icon,false,w->action.run());b.setTooltip(Tooltip.create(Component.translatable(key)));addRenderableWidget(b);}
    public int page(){return page;}public int pageCount(){return pages;}
    public long readyArtworkCount(){if(tab==2||!player.settings.covers)return 0;return seen.stream().skip((long)page*rows()).limit(rows()).filter(Track.class::isInstance).map(Track.class::cast).map(t->artwork.get(t.id())).filter(Objects::nonNull).filter(CoverTexture::ready).count();}
    @Override public void removed(){artwork.values().forEach(CoverTexture::close);artwork.clear();super.removed();}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return parent!=null&&parent.isPauseScreen();}
}
