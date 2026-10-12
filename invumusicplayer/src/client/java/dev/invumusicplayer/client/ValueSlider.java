package dev.invumusicplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;

public final class ValueSlider extends AbstractSliderButton {
    private final String key;
    private final double min,max;
    private final DoubleConsumer changed;
    public ValueSlider(int x,int y,int width,String key,double initial,double min,double max,DoubleConsumer changed) {
        super(x,y,width,20,Component.empty(),(initial-min)/(max-min));
        this.key=key;this.min=min;this.max=max;this.changed=changed;updateMessage();
    }
    @Override protected void updateMessage() { if(key!=null)setMessage(Component.translatable(key,Math.round(min+value*(max-min)))); }
    @Override protected void applyValue() { if(changed!=null)changed.accept(min+value*(max-min)); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        int x=getX(),y=getY(),w=getWidth();g.fill(x,y,x+w,y+getHeight(),0xff1c2638);
        g.fill(x+4,y+getHeight()-4,x+w-4,y+getHeight()-2,0xff44516a);
        int handle=x+4+(int)((w-8)*value);g.fill(x+4,y+getHeight()-4,handle,y+getHeight()-2,0xff000000|InvuMusicPlayerClient.player.settings.accent);
        g.fill(handle-2,y+getHeight()-7,handle+2,y+getHeight(),0xffe4ecff);
        g.text(Minecraft.getInstance().font,getMessage().getString(),x+6,y+4,0xffedf2fb,false);
    }
}
