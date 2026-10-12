package dev.invumusicplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;

public final class ValueSlider extends AbstractSliderButton {
    private final String key;
    private final double min,max;
    private final DoubleConsumer changed;private final float scale;
    public ValueSlider(int x,int y,int width,String key,double initial,double min,double max,DoubleConsumer changed) {
        this(x,y,width,key,initial,min,max,1,changed);
    }
    public ValueSlider(int x,int y,int width,String key,double initial,double min,double max,float scale,DoubleConsumer changed){
        super(x,y,width,Math.max(8,Math.round(20*scale)),Component.empty(),(initial-min)/(max-min));
        this.key=key;this.min=min;this.max=max;this.changed=changed;this.scale=scale;updateMessage();
    }
    @Override protected void updateMessage() { if(key!=null)setMessage(Component.translatable(key,Math.round(min+value*(max-min)))); }
    @Override protected void applyValue() { if(changed!=null)changed.accept(min+value*(max-min)); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        if(key.equals("invumusicplayer.slider.volume")){value=InvuMusicPlayerClient.player.settings.volume;updateMessage();}
        g.pose().pushMatrix();g.pose().translate((float)getX(),(float)getY());g.pose().scale(scale);
        int x=0,y=0,w=Math.round(getWidth()/scale),h=20;g.fill(x,y,x+w,y+h,0xff000000|PlayerMenuButton.themeColor());
        g.fill(x+4,y+h-4,x+w-4,y+h-2,0xff44516a);
        int handle=x+4+(int)((w-8)*value);g.fill(x+4,y+h-4,handle,y+h-2,0xff000000|PlayerMenuButton.themeAccent());
        g.fill(handle-2,y+h-7,handle+2,y+h,0xffe4ecff);
        g.text(Minecraft.getInstance().font,getMessage().getString(),x+6,y+4,0xffedf2fb,false);
        g.pose().popMatrix();
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){boolean result=super.mouseReleased(event);if(key.equals("invumusicplayer.slider.volume"))InvuMusicPlayerClient.player.saveSettings();return result;}
}
