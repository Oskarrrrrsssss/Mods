package dev.invumusicplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Rounded entry and transport controls with native focus, input and narration. */
final class PlayerMenuButton extends Button {
    enum Icon { MUSIC, SETTINGS, PREVIOUS, PAUSE, NEXT }
    private final Icon icon;
    private final boolean label;
    PlayerMenuButton(int x, int y, int width, Component message, Icon icon, boolean label, OnPress press) {
        super(x, y, width, 20, message, button->{
            press.onPress(button);
            if(icon==Icon.PAUSE)refreshPlaybackMessage(button);
        }, DEFAULT_NARRATION); this.icon=icon; this.label=label;
    }
    static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) {
        if(w<6||h<6){g.fill(x,y,x+w,y+h,color);return;}
        g.fill(x+3,y,x+w-3,y+1,color);g.fill(x+1,y+1,x+w-1,y+3,color);
        g.fill(x,y+3,x+w,y+h-3,color);
        g.fill(x+1,y+h-3,x+w-1,y+h-1,color);g.fill(x+3,y+h-1,x+w-3,y+h,color);
    }
    @Override public void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
        var player=InvuMusicPlayerClient.player;int accent=player.settings.accent;
        boolean highlighted=isHoveredOrFocused();
        rounded(g,getX(),getY()+1,getWidth(),getHeight(),0x48000000);
        rounded(g,getX(),getY(),getWidth(),getHeight(),highlighted?0xff35435a:0xff1e293b);
        if(highlighted)g.fill(getX()+7,getY()+getHeight()-2,getX()+getWidth()-7,getY()+getHeight()-1,0xff000000|accent);
        int ix=label?getX()+7:getX()+(getWidth()-10)/2,iy=getY()+5,c=0xff000000|accent;
        if(icon==Icon.PAUSE)refreshPlaybackMessage(this);
        switch(icon) {
            case MUSIC -> {g.fill(ix+2,iy,ix+4,iy+8,c);g.fill(ix+4,iy,ix+9,iy+2,c);g.fill(ix,iy+7,ix+4,iy+10,c);}
            case SETTINGS -> {g.fill(ix+3,iy,ix+7,iy+10,c);g.fill(ix,iy+3,ix+10,iy+7,c);g.fill(ix+1,iy+1,ix+9,iy+9,c);g.fill(ix+3,iy+3,ix+7,iy+7,highlighted?0xff35435a:0xff1e293b);}
            case PAUSE -> {if(player.playing()){g.fill(ix+1,iy,ix+4,iy+10,c);g.fill(ix+6,iy,ix+9,iy+10,c);}else triangle(g,ix+2,iy,c,false);}
            case PREVIOUS -> {g.fill(ix,iy,ix+2,iy+10,c);triangle(g,ix+3,iy,c,true);}
            case NEXT -> {triangle(g,ix,iy,c,false);g.fill(ix+8,iy,ix+10,iy+10,c);}
        }
        if(label){var font=Minecraft.getInstance().font;g.text(font,font.plainSubstrByWidth(getMessage().getString(),Math.max(0,getWidth()-26)),getX()+21,getY()+6,0xff000000|player.settings.textColor,false);}
    }
    private static void refreshPlaybackMessage(Button button) {
        Component message=Component.translatable(InvuMusicPlayerClient.player.playing()?"invumusicplayer.player.pause":"invumusicplayer.player.play");
        if(!button.getMessage().equals(message)){button.setMessage(message);button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(message));}
    }
    private static void triangle(GuiGraphicsExtractor g,int x,int y,int c,boolean reverse) {
        for(int i=0;i<5;i++){int at=reverse?x+4-i:x+i;g.fill(at,y+i,at+1,y+10-i,c);}
    }
}
