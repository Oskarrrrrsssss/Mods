package dev.invumusicplayer.client;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.net.URI;

/** Catalogue OAuth and native playback authorization are separate; neither uses a password field. */
public final class SpotifyAccountScreen extends Screen{
    private final Screen parent;private EditBox clientId;private ModernButton login,nativeLogin,disconnect;
    public SpotifyAccountScreen(Screen parent){super(Component.translatable("invumusicplayer.spotify.account_title"));this.parent=parent;}
    @Override protected void init(){int x=width/2-155;var p=InvuMusicPlayerClient.player;
        var yandex=addRenderableWidget(new ModernButton(x,28,151,20,Component.translatable("invumusicplayer.service.yandex"),b->{p.setMusicService("yandex");minecraft.gui.setScreen(new AccountScreen(parent));}));
        var spotify=addRenderableWidget(new ModernButton(x+159,28,151,20,Component.translatable("invumusicplayer.service.spotify"),b->{}));spotify.selected(()->true);
        clientId=new EditBox(font,x,92,310,20,Component.translatable("invumusicplayer.spotify.client_id"));clientId.setMaxLength(32);clientId.setValue(p.settings.spotifyClientId);addRenderableWidget(clientId);
        login=addRenderableWidget(new ModernButton(x,120,151,20,Component.translatable("invumusicplayer.spotify.login_catalogue"),b->{p.spotify.login(clientId.getValue().strip());p.saveSettings();}));
        disconnect=addRenderableWidget(new ModernButton(x+159,120,151,20,Component.translatable("invumusicplayer.account.disconnect"),b->p.disconnectSpotify()));
        nativeLogin=addRenderableWidget(new ModernButton(x,146,310,20,Component.translatable("invumusicplayer.spotify.login_native"),b->p.spotifyAudio.login()));
        addRenderableWidget(new ModernButton(x,172,310,20,Component.translatable("invumusicplayer.spotify.help"),b->Blaze3D.openUri(URI.create("https://github.com/Oskarrrrrsssss/Mods/blob/codex/musicraft-playback/invumusicplayer/README.md#spotify"))));
        addRenderableWidget(new ModernButton(x,height-28,310,20,Component.translatable("invumusicplayer.account.back"),b->onClose()));update();
    }
    private void update(){if(login==null)return;var p=InvuMusicPlayerClient.player;login.active=!p.spotify.connecting()&&!clientId.getValue().isBlank();login.setMessage(Component.translatable(p.spotify.connecting()?"invumusicplayer.spotify.browser_wait":"invumusicplayer.spotify.login_catalogue"));nativeLogin.active=p.spotify.connected()&&!p.spotifyAudio.connecting();disconnect.active=p.spotify.connected()||p.spotify.connecting()||p.spotifyAudio.authorized()||p.spotifyAudio.connecting();
        nativeLogin.setMessage(Component.translatable(p.spotifyAudio.connecting()?"invumusicplayer.spotify.browser_wait":p.spotifyAudio.authorized()?"invumusicplayer.spotify.reconnect_native":"invumusicplayer.spotify.login_native"));
        nativeLogin.setTooltip(Tooltip.create(Component.literal(p.spotifyAudio.authError().isBlank()?p.spotifyAudio.authStatus():p.spotifyAudio.authError())));
        login.setTooltip(Tooltip.create(Component.literal(p.spotify.error().isBlank()?p.spotify.status():p.spotify.error())));
    }
    @Override public void tick(){super.tick();update();var p=InvuMusicPlayerClient.player;for(URI uri:new URI[]{p.spotify.takeLoginUri(),p.spotifyAudio.takeLoginUri()})if(uri!=null&&uri.getScheme().equals("https")&&uri.getHost().equals("accounts.spotify.com"))Blaze3D.openUri(uri);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xf00c1220);int x=width/2-155;g.text(font,title.getString(),x,10,PlayerHud.TEXT,false);
        g.text(font,font.plainSubstrByWidth(UiText.text("invumusicplayer.spotify.premium_hint"),310),x,54,PlayerHud.MUTED,false);
        g.text(font,font.plainSubstrByWidth(UiText.text("invumusicplayer.spotify.native_hint"),310),x,66,PlayerHud.MUTED,false);
        g.text(font,UiText.text("invumusicplayer.spotify.client_id"),x,80,PlayerHud.TEXT,false);
        var p=InvuMusicPlayerClient.player;String error=p.spotifyAudio.authError().isBlank()?p.spotify.error():p.spotifyAudio.authError();String status=error.isBlank()?p.spotify.status()+" · "+p.spotifyAudio.authStatus():error;
        g.text(font,font.plainSubstrByWidth(status,310),x,198,error.isBlank()?PlayerHud.MUTED:0xfff28b82,false);super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){InvuMusicPlayerClient.player.saveSettings();minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return parent!=null&&parent.isPauseScreen();}
}
