package dev.invumusicplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Drag previews position; release commits once, provided the same playback request is still active. */
public final class SeekSlider extends AbstractSliderButton{
    private final float scale;private final boolean label;private boolean dragging;private long revision,duration;
    public SeekSlider(int x,int y,int width,float scale,boolean label){super(x,y,width,Math.max(7,Math.round(16*scale)),Component.empty(),0);this.scale=scale;this.label=label;active=InvuMusicPlayerClient.player.canSeek();updateMessage();}
    @Override protected void updateMessage(){if(InvuMusicPlayerClient.player==null)return;var p=InvuMusicPlayerClient.player;long total=dragging?duration:p.durationMillis();setMessage(p.settings.showTime?Component.translatable("invumusicplayer.slider.seek",PlayerHud.time((long)(value*total)),PlayerHud.time(total)):Component.translatable("invumusicplayer.slider.seek_hint"));setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));}
    @Override protected void applyValue(){updateMessage();if(!dragging){var p=InvuMusicPlayerClient.player;if(p.canSeek())p.seek((long)(value*p.durationMillis()));}}
    @Override public void onClick(double mouseX,double mouseY){var p=InvuMusicPlayerClient.player;dragging=true;revision=p.playbackRevision();duration=p.durationMillis();super.onClick(mouseX,mouseY);}
    @Override public void onRelease(double mouseX,double mouseY){super.onRelease(mouseX,mouseY);if(!dragging)return;dragging=false;var p=InvuMusicPlayerClient.player;if(p.playbackRevision()==revision&&p.canSeek())p.seek((long)(value*duration));}
    @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta){var p=InvuMusicPlayerClient.player;active=p.canSeek();if(dragging&&p.playbackRevision()!=revision)dragging=false;if(!dragging)value=p.durationMillis()>0?Math.max(0,Math.min(1,(double)p.positionMillis()/p.durationMillis())):0;updateMessage();
        g.pose().pushPose();g.pose().translate(getX(),getY(),0);g.pose().scale(scale,scale,1);int w=Math.round(getWidth()/scale),bar=label&&p.settings.showTime?12:7;g.fill(0,0,w,16,0xff000000|UiTheme.control());
        g.fill(4,bar,w-4,bar+2,0xff44516a);int at=4+(int)((w-8)*value);g.fill(4,bar,at,bar+2,0xff000000|UiTheme.accent());g.fill(at-2,bar-2,at+2,bar+4,active?0xffe4ecff:0xff586270);
        if(label&&p.settings.showTime)g.drawString(Minecraft.getInstance().font,getMessage().getString(),4,0,0xffedf2fb,false);g.pose().popPose();
    }
    boolean dragging(){return dragging;}
}
