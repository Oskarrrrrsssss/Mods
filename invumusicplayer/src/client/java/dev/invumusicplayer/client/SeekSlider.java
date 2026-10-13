package dev.invumusicplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag previews position; release commits once, provided the same playback request is still active. */
public final class SeekSlider extends AbstractSliderButton{
    private final float scale;private final boolean label;private boolean dragging;private long revision,duration;
    public SeekSlider(int x,int y,int width,float scale,boolean label){super(x,y,width,Math.max(7,Math.round(16*scale)),Component.empty(),0);this.scale=scale;this.label=label;active=InvuMusicPlayerClient.player.canSeek();updateMessage();}
    @Override protected void updateMessage(){if(InvuMusicPlayerClient.player==null)return;var p=InvuMusicPlayerClient.player;long total=dragging?duration:p.durationMillis();setMessage(Component.translatable("invumusicplayer.slider.seek",PlayerHud.time((long)(value*total)),PlayerHud.time(total)));setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));}
    @Override protected void applyValue(){updateMessage();if(!dragging){var p=InvuMusicPlayerClient.player;if(p.canSeek())p.seek((long)(value*p.durationMillis()));}}
    @Override public void onClick(MouseButtonEvent event,boolean doubleClick){var p=InvuMusicPlayerClient.player;dragging=true;revision=p.playbackRevision();duration=p.durationMillis();super.onClick(event,doubleClick);}
    @Override public void onRelease(MouseButtonEvent event){super.onRelease(event);if(!dragging)return;dragging=false;var p=InvuMusicPlayerClient.player;if(p.playbackRevision()==revision&&p.canSeek())p.seek((long)(value*duration));}
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){var p=InvuMusicPlayerClient.player;active=p.canSeek();if(dragging&&p.playbackRevision()!=revision)dragging=false;if(!dragging)value=p.durationMillis()>0?Math.max(0,Math.min(1,(double)p.positionMillis()/p.durationMillis())):0;updateMessage();
        g.pose().pushMatrix();g.pose().translate((float)getX(),(float)getY());g.pose().scale(scale);int w=Math.round(getWidth()/scale),bar=label?12:7;g.fill(0,0,w,16,0xff000000|PlayerMenuButton.themeColor());
        g.fill(4,bar,w-4,bar+2,0xff44516a);int at=4+(int)((w-8)*value);g.fill(4,bar,at,bar+2,0xff000000|PlayerMenuButton.themeAccent());g.fill(at-2,bar-2,at+2,bar+4,active?0xffe4ecff:0xff586270);
        if(label)g.text(Minecraft.getInstance().font,getMessage().getString(),4,0,0xffedf2fb,false);g.pose().popMatrix();
    }
    boolean dragging(){return dragging;}
}
