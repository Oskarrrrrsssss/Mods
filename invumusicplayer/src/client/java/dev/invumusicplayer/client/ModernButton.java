package dev.invumusicplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat controls keep native keyboard, narration and click behavior. */
public final class ModernButton extends Button {
    private java.util.function.BooleanSupplier selected=()->false;
    public ModernButton selected(java.util.function.BooleanSupplier value){selected=value;return this;}
    public boolean isSelected(){return selected.getAsBoolean();}
    public ModernButton(int x,int y,int w,int h,Component text,OnPress pressed) { super(x,y,w,h,text,pressed,DEFAULT_NARRATION); }
    @Override public void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
        int bg=!active?0xff1c2230:(isHoveredOrFocused()||isSelected())?0xff35425c:0xff253149;
        g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),bg);
        g.fill(getX()+3,getY()+getHeight()-2,getX()+getWidth()-3,getY()+getHeight(),active?0xff000000|InvuMusicPlayerClient.player.settings.accent:0xff364152);
        var font=Minecraft.getInstance().font;
        String text=font.plainSubstrByWidth(getMessage().getString(),getWidth()-8);
        g.text(font,text,getX()+(getWidth()-font.width(text))/2,getY()+(getHeight()-8)/2,active?0xffedf2fb:0xff7b879c,false);
    }
}
