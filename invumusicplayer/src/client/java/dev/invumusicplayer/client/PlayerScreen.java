package dev.invumusicplayer.client;

import dev.invumusicplayer.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class PlayerScreen extends Screen {
    private final Player player = InvuMusicPlayerClient.player;
    private final Screen parent=net.minecraft.client.Minecraft.getInstance().gui.screen();
    private List<Track> seen = List.of();
    private int page,pages=1;
    private int left, top, panelWidth, panelHeight, listWidth, rows;
    private Button pause, serviceStatus,likeButton,dislikeButton,waveButton;
    private final AlbumBackdrop backdrop=new AlbumBackdrop();
    private final AudioVisualizer visualizer = new AudioVisualizer();
    private final Map<String,CoverTexture> artwork = new HashMap<>();
    public PlayerScreen() { super(Component.translatable("invumusicplayer.player.title")); }
    @Override protected void init() {
        panelWidth = Math.min(620, width - 20); panelHeight = Math.min(420, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        listWidth = (panelWidth - 32) / 2; rows = Math.max(1, (panelHeight - 122) / 24);
        button(Component.translatable("invumusicplayer.library.search"),left+10,top+32,(listWidth-4)/2,()->minecraft.gui.setScreen(new LibraryScreen(this)));
        waveButton=button(Component.translatable("invumusicplayer.player.wave"),left+14+(listWidth-4)/2,top+32,(listWidth-4)/2-24,()->{if(player.connected())player.startWave();else minecraft.gui.setScreen(AccountScreen.open(this));});
        icon("invumusicplayer.wave.settings",left+listWidth-10,top+32,20,PlayerMenuButton.Icon.WAVE,()->minecraft.gui.setScreen(new WaveSettingsScreen(this)));
        button(Component.translatable("invumusicplayer.library.clear"),left+listWidth-54,top+58,64,player::clearQueue);
        seen=player.queue();pages=Math.max(1,(seen.size()+rows-1)/rows);page=Math.max(0,Math.min(page,pages-1));
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row; if (index >= seen.size()) break;
            Track t = seen.get(index); List<Track> selected = seen;
            Button entry = button(Component.literal(font.plainSubstrByWidth(t.title()+" · "+t.artist(), listWidth - 54)), left + 30, top + 84 + row * 24,
                listWidth - 44, () -> player.playQueued(index));
            ((ModernButton)entry).selected(()->player.current()!=null&&player.current().id().equals(t.id()));
            entry.setTooltip(Tooltip.create(Component.literal(t.display())));
            Button remove=button(Component.translatable("invumusicplayer.library.remove"),left+listWidth-10,top+84+row*24,20,()->player.removeQueued(index));remove.setTooltip(Tooltip.create(Component.translatable("invumusicplayer.queue.remove_hint")));
        }
        int bottom = top + panelHeight - 92;
        button(Component.literal("<"), left + 10, top+panelHeight-26, 24, () -> movePage(-1)).active=page>0;
        button(Component.literal(">"), left + 38, top+panelHeight-26, 24, () -> movePage(1)).active=page+1<pages;
        int right = left + listWidth + 22, rightWidth = panelWidth - listWidth - 32;
        int mediaWidth=Math.min(200,rightWidth),mediaX=right+(rightWidth-mediaWidth)/2;
        icon("invumusicplayer.menu.previous",mediaX,bottom,36,PlayerMenuButton.Icon.PREVIOUS,player::previous);
        pause=icon("invumusicplayer.player.pause",mediaX+44,bottom,mediaWidth-88,PlayerMenuButton.Icon.PAUSE,player::togglePause);
        icon("invumusicplayer.menu.next",mediaX+mediaWidth-36,bottom,36,PlayerMenuButton.Icon.NEXT,player::next);
        addRenderableWidget(new SeekSlider(right,top+panelHeight-116,rightWidth,1,true));
        int footer = top + panelHeight - 26;
        likeButton=icon("invumusicplayer.player.like",right,top+32,24,PlayerMenuButton.Icon.LIKE,()->player.rate(true));
        icon("invumusicplayer.player.shuffle",right+28,top+32,24,PlayerMenuButton.Icon.SHUFFLE,player::toggleShuffle);
        dislikeButton=icon("invumusicplayer.player.dislike",right+56,top+32,24,PlayerMenuButton.Icon.DISLIKE,()->player.rate(false));
        icon("invumusicplayer.player.library",right+112,top+32,Math.max(24,rightWidth-112),PlayerMenuButton.Icon.LIBRARY,()->minecraft.gui.setScreen(new LibraryScreen(this)));
        icon("invumusicplayer.player.album_background",right+84,top+32,24,PlayerMenuButton.Icon.BACKGROUND,()->{player.settings.albumBackground=!player.settings.albumBackground;player.saveSettings();});
        int volumeWidth=Math.min(220,rightWidth);
        addRenderableWidget(new ValueSlider(right+(rightWidth-volumeWidth)/2,top+panelHeight-58,volumeWidth,"invumusicplayer.slider.volume",player.settings.volume*100,0,100,.8f,v->player.volume((float)v/100)));
        serviceStatus = button(Component.empty(), left + 110, top + 6, panelWidth - 120, () -> {});
        updateServiceStatus();
        icon("invumusicplayer.player.account", left + 70, footer, 24,PlayerMenuButton.Icon.ACCOUNT, () -> minecraft.gui.setScreen(AccountScreen.open(this)));
        icon("invumusicplayer.player.settings", left + 98, footer, 24,PlayerMenuButton.Icon.SETTINGS, () -> minecraft.gui.setScreen(new SettingsScreen(this)));
        icon("invumusicplayer.player.hud", left + 126, footer, 24,PlayerMenuButton.Icon.MOVE, () -> minecraft.gui.setScreen(new HudEditor(this)));
        button(Component.translatable("invumusicplayer.player.done"), left + panelWidth - 74, footer, 64, this::onClose);
    }
    private Button icon(String key,int x,int y,int w,PlayerMenuButton.Icon icon,Runnable action){var b=new PlayerMenuButton(x,y,w,Component.translatable(key),icon,false,v->action.run());b.setTooltip(Tooltip.create(Component.translatable(key)));return addRenderableWidget(b);}
    private Button button(Component text, int x, int y, int w, Runnable action) {
        var b=new ModernButton(x,y,Math.max(20,w),20,text,v->action.run());if(text.equals(Component.translatable("invumusicplayer.player.local")))b.selected(()->player.listingSection().equals("local"));if(text.equals(Component.translatable("invumusicplayer.player.wave")))b.selected(()->player.listingSection().equals("wave"));if(text.equals(Component.translatable("invumusicplayer.player.search")))b.selected(()->player.listingSection().equals("search"));return addRenderableWidget(b);
    }
    @Override public void tick() {
        if (!seen.equals(player.queue())){page=Math.min(page,Math.max(0,(player.queue().size()+rows-1)/rows-1));rebuildWidgets();}
        updateServiceStatus();
        if(pause!=null)pause.setMessage(Component.translatable(player.playing()?"invumusicplayer.player.pause":"invumusicplayer.player.play"));
        if(waveButton!=null)waveButton.active=!player.spotifySelected();
        if(likeButton!=null){boolean enabled=player.current()!=null&&!player.current().youtube();likeButton.active=enabled;dislikeButton.active=enabled&&!player.current().spotify();
}
    }
    private void updateServiceStatus() {
        if (serviceStatus == null) return;
        String status = player.error().isBlank() ? player.status() : player.error();
        serviceStatus.setMessage(Component.literal(font.plainSubstrByWidth(status, serviceStatus.getWidth() - 12)));
        serviceStatus.setTooltip(Tooltip.create(Component.literal(status+"\n"+player.coverStatus()+"\n"+InvuMusicPlayerClient.cover.errorMessage())));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xa0000000|UiTheme.background(this));
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xff000000|UiTheme.background(this));
        g.fill(left, top, left + panelWidth, top + 2, 0xff000000 | UiTheme.accent());
        g.text(font, UiText.text("invumusicplayer.player.brand"), left + 10, top + 12, 0xff000000 | UiTheme.accent(), false);
        int right = left + listWidth + 22, rightWidth = panelWidth - listWidth - 32;
        Track track=player.current();int start=top+60,seekY=top+panelHeight-116;
        int eqHeight=player.settings.visualizer?Math.max(4,Math.min(Math.round(20*player.settings.equalizerHeight),Math.max(4,(panelHeight-210)/2))):0;
        int eqY=seekY-6-eqHeight;
        int artworkSize=Math.max(0,Math.min(Math.min(164,rightWidth-20),eqY-start-22));
        boolean art=player.settings.covers&&track!=null&&!track.youtube();
        int textY=art&&artworkSize>=20?start+artworkSize+2:start;
        if(art&&artworkSize>=20){int artX=right+(rightWidth-artworkSize)/2;g.enableScissor(right,top+56,right+rightWidth,seekY-2);backdrop.draw(g,artX,start,artworkSize);g.disableScissor();InvuMusicPlayerClient.cover.draw(g,artX,start,artworkSize,UiTheme.accent());}
        boolean compactArt=art&&artworkSize<20;if(compactArt){backdrop.draw(g,right+8,start,32);InvuMusicPlayerClient.cover.draw(g,right+8,start,32,UiTheme.accent());textY=start+5;}
        int textX=compactArt?right+48:right,textWidth=compactArt?rightWidth-56:rightWidth;
        centered(g,track==null?UiText.text("invumusicplayer.player.choose_track"):track.title(),textX,textY,textWidth,0xff000000|player.settings.textColor);
        centered(g,track==null?UiText.text("invumusicplayer.player.tagline"):track.artist(),textX,textY+10,textWidth,0xffaab6c2);
        if(player.settings.visualizer)visualizer.draw(g,player.spectrum(),right,eqY,rightWidth,eqHeight,UiTheme.accent());
        g.text(font,UiText.text("invumusicplayer.library.queue"),left+12,top+64,PlayerHud.TEXT,false);
        if (seen.isEmpty()) g.text(font, font.plainSubstrByWidth(UiText.text("invumusicplayer.queue.empty"), listWidth - 4), left + 12, top + 88, PlayerHud.MUTED, false);
        g.text(font, (page + 1) + " / " + Math.max(1, (seen.size() + rows - 1) / rows), left + 156, top + panelHeight - 20, PlayerHud.MUTED, false);
        for(int row=0;row<rows;row++){int index=page*rows+row;if(index>=seen.size())break;Track t=seen.get(index);if(player.settings.covers&&!t.youtube()){
            if(!artwork.containsKey(t.id())&&artwork.size()>=64){String old=artwork.keySet().iterator().next();artwork.remove(old).close();}
            CoverTexture texture=artwork.computeIfAbsent(t.id(),id->new CoverTexture());texture.update(player.coverFor(t));texture.draw(g,left+12,top+86+row*24,16,player.settings.accent);
        }}
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    private void centered(GuiGraphicsExtractor g,String text,int x,int y,int width,int color){String clipped=font.plainSubstrByWidth(text,width-12);g.text(font,clipped,x+(width-font.width(clipped))/2,y,color,false);}
    private void movePage(int delta){int next=Math.max(0,Math.min(page+delta,pages-1));if(next!=page){page=next;rebuildWidgets();}}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(vertical!=0&&mx>=left+10&&mx<left+10+listWidth&&my>=top+84&&my<top+84+rows*24){movePage(vertical>0?-1:1);return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    public int page(){return page;}public int pageCount(){return pages;}
    public long readyArtworkCount() {return seen.stream().skip((long)page*rows).limit(rows).map(track->artwork.get(track.id())).filter(Objects::nonNull).filter(CoverTexture::ready).count();}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){artwork.values().forEach(CoverTexture::close);artwork.clear();player.saveSettings();super.removed();}
    @Override public boolean isPauseScreen() { return parent!=null&&parent.isPauseScreen(); }
}
