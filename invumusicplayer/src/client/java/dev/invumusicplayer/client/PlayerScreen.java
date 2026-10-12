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
    private int page;
    private String query = "";
    private EditBox search;
    private int left, top, panelWidth, panelHeight, listWidth, rows;
    private Button pause, serviceStatus,likeButton,dislikeButton;
    private final AudioVisualizer visualizer = new AudioVisualizer();
    private final Map<String,CoverTexture> artwork = new HashMap<>();
    public PlayerScreen() { super(Component.translatable("invumusicplayer.player.title")); }
    @Override protected void init() {
        panelWidth = Math.min(620, width - 20); panelHeight = Math.min(360, height - 20);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        listWidth = (panelWidth - 32) / 2; rows = Math.max(1, (panelHeight - 138) / 24);
        button(Component.translatable("invumusicplayer.player.local"), left + 10, top + 32, (listWidth - 4) / 2, () -> { page = 0; player.refreshLocal(); });
        button(Component.translatable("invumusicplayer.player.wave"), left + 14 + (listWidth - 4) / 2, top + 32, (listWidth - 4) / 2, () -> { if (player.connected()) player.startWave(); else minecraft.gui.setScreen(new AccountScreen(this)); });
        search = new EditBox(font, left + 10, top + 58, listWidth - 44, 20, Component.translatable("invumusicplayer.player.search_hint"));
        search.setBordered(false);search.setHint(Component.translatable("invumusicplayer.player.search_hint"));search.setMaxLength(256); search.setValue(query); search.setResponder(value -> query = value); addRenderableWidget(search);
        button(Component.translatable("invumusicplayer.player.search"), left + listWidth - 30, top + 58, 40, () -> { page = 0; if (player.connected()) player.search(query); else minecraft.gui.setScreen(new AccountScreen(this)); });
        seen = player.items(); int pages = Math.max(1, (seen.size() + rows - 1) / rows); page = Math.max(0, Math.min(page, pages - 1));
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row; if (index >= seen.size()) break;
            Track t = seen.get(index); List<Track> selected = seen;
            Button entry = button(Component.literal(font.plainSubstrByWidth(t.title()+" · "+t.artist(), listWidth - 54)), left + 30, top + 84 + row * 24,
                listWidth - 44, () -> player.choose(selected, index));
            entry.setTooltip(Tooltip.create(Component.literal(t.display())));
            button(Component.literal("+"),left+listWidth-10,top+84+row*24,20,()->player.enqueue(t));
        }
        int bottom = top + panelHeight - 54;
        button(Component.literal("<"), left + 10, bottom, 24, () -> { page--; rebuildWidgets(); });
        button(Component.literal(">"), left + listWidth - 14, bottom, 24, () -> { page++; rebuildWidgets(); });
        int right = left + listWidth + 22, rightWidth = panelWidth - listWidth - 32;
        button(Component.literal("<<"), right, bottom - 4, 36, player::previous);
        pause = button(Component.translatable(player.playing() ? "invumusicplayer.player.pause" : "invumusicplayer.player.play"), right + 40, bottom - 4, rightWidth - 80, player::togglePause);
        button(Component.literal(">>"), right + rightWidth - 36, bottom - 4, 36, player::next);
        int seekY=top+panelHeight-100;int third=(rightWidth-8)/3;
        button(Component.translatable("invumusicplayer.player.seek_back"),right,seekY,third,()->player.seek(player.positionMillis()-10000));
        button(Component.translatable("invumusicplayer.player.stop"),right+third+4,seekY,third,player::suspend);
        button(Component.translatable("invumusicplayer.player.seek_forward"),right+2*(third+4),seekY,third,()->player.seek(player.positionMillis()+10000));
        int footer = top + panelHeight - 26;
        likeButton=button(Component.translatable("invumusicplayer.player.like"), right, top+32, 44, ()->player.rate(true));
        dislikeButton=button(Component.translatable("invumusicplayer.player.dislike"), right+48, top+32, 44, ()->player.rate(false));
        button(Component.translatable("invumusicplayer.player.library"), right+96, top+32, Math.max(50,rightWidth-96), ()->minecraft.gui.setScreen(new LibraryScreen(this)));
        addRenderableWidget(new ValueSlider(right,top+panelHeight-78,rightWidth,"invumusicplayer.slider.volume",player.settings.volume*100,0,100,v->player.volume((float)v/100)));
        serviceStatus = button(Component.empty(), left + 110, top + 6, panelWidth - 120, () -> {});
        updateServiceStatus();
        button(Component.translatable("invumusicplayer.player.account"), left + 10, footer, 74, () -> minecraft.gui.setScreen(new AccountScreen(this)));
        button(Component.translatable("invumusicplayer.player.settings"), left + 88, footer, 84, () -> minecraft.gui.setScreen(new SettingsScreen(this)));
        button(Component.translatable("invumusicplayer.player.hud"), left + 176, footer, 44, () -> minecraft.gui.setScreen(new HudEditor(this)));
        button(Component.translatable("invumusicplayer.player.done"), left + panelWidth - 74, footer, 64, this::onClose);
    }
    private Button button(Component text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new ModernButton(x,y,Math.max(20,w),20,text,b->action.run()));
    }
    @Override public void tick() {
        if (seen != player.items()) rebuildWidgets();
        updateServiceStatus();
        if(likeButton!=null){boolean enabled=player.current()!=null&&!player.current().youtube();likeButton.active=dislikeButton.active=enabled;
            ((ModernButton)likeButton).selected(()->player.rating()==1);((ModernButton)dislikeButton).selected(()->player.rating()==-1);}
        if (pause != null) pause.setMessage(Component.translatable(player.loading() ? "invumusicplayer.player.loading" : player.playing() ? "invumusicplayer.player.pause" : "invumusicplayer.player.play"));
    }
    private void updateServiceStatus() {
        if (serviceStatus == null) return;
        String status = player.error().isBlank() ? player.status() : player.error();
        serviceStatus.setMessage(Component.literal(font.plainSubstrByWidth(status, serviceStatus.getWidth() - 12)));
        serviceStatus.setTooltip(Tooltip.create(Component.literal(status+"\n"+player.coverStatus()+"\n"+InvuMusicPlayerClient.cover.errorMessage())));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x9a080d0a);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xff0c1220);
        g.fill(left, top, left + panelWidth, top + 2, 0xff000000 | player.settings.accent);
        g.text(font, UiText.text("invumusicplayer.player.brand"), left + 10, top + 12, 0xff000000 | player.settings.accent, false);
        int right = left + listWidth + 22, rightWidth = panelWidth - listWidth - 32;
        int y = top + 62;
        if (player.settings.covers && panelHeight >= 300 && player.current() != null && !player.current().youtube()) {
            int size = panelHeight < 270 ? 42 : 54;
            InvuMusicPlayerClient.cover.draw(g, right, y, size, player.settings.accent); y += size + 8;
        }
        Track track = player.current();
        g.text(font, font.plainSubstrByWidth(track == null ? UiText.text("invumusicplayer.player.choose_track") : track.title(), rightWidth), right, y, PlayerHud.TEXT, false);
        g.text(font, font.plainSubstrByWidth(track == null ? UiText.text("invumusicplayer.player.tagline") : track.artist(), rightWidth), right, y + 14, PlayerHud.MUTED, false);
        if(player.settings.visualizer)visualizer.draw(g,player.spectrum(),right,y+31,rightWidth,20,player.settings.accent);
        else {
        g.text(font, PlayerHud.time(player.positionMillis()) + " / " + PlayerHud.time(player.durationMillis()), right, y + 32, PlayerHud.MUTED, false);
        int barY = y + 47; g.fill(right, barY, right + rightWidth, barY + 3, 0xff35463b);
        if (player.durationMillis() > 0) g.fill(right, barY, right + (int) (rightWidth * Math.min(1, (double) player.positionMillis() / player.durationMillis())), barY + 3, 0xff000000 | player.settings.accent);
        }
        if (seen.isEmpty()) g.text(font, font.plainSubstrByWidth(UiText.text("invumusicplayer.player.empty_library"), listWidth - 4), left + 12, top + 88, PlayerHud.MUTED, false);
        g.text(font, (page + 1) + " / " + Math.max(1, (seen.size() + rows - 1) / rows), left + 44, top + panelHeight - 46, PlayerHud.MUTED, false);
        for(int row=0;row<rows;row++){int index=page*rows+row;if(index>=seen.size())break;Track t=seen.get(index);if(player.settings.covers&&!t.youtube()){
            if(!artwork.containsKey(t.id())&&artwork.size()>=64){String old=artwork.keySet().iterator().next();artwork.remove(old).close();}
            CoverTexture texture=artwork.computeIfAbsent(t.id(),id->new CoverTexture());texture.update(player.coverFor(t));texture.draw(g,left+12,top+86+row*24,16,player.settings.accent);
        }}
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    public long readyArtworkCount() { return artwork.values().stream().filter(CoverTexture::ready).count(); }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){artwork.values().forEach(CoverTexture::close);artwork.clear();player.saveSettings();super.removed();}
    @Override public boolean isPauseScreen() { return false; }
}
