package dev.invumusicplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Rounded entry and transport controls with native focus, input and narration. */
final class PlayerMenuButton extends Button {
    enum Icon { MUSIC, SETTINGS, PREVIOUS, PAUSE, NEXT, LIKE, DISLIKE, SHUFFLE, WAVE, LIBRARY, MOVE, TRASH, ACCOUNT, CLOCK, BACKGROUND }
    private final Icon icon;
    private final boolean label;private final float scale;
    PlayerMenuButton(int x, int y, int width, Component message, Icon icon, boolean label, OnPress press) {
        this(x,y,width,message,icon,label,1,press);
    }
    PlayerMenuButton(int x,int y,int width,Component message,Icon icon,boolean label,float scale,OnPress press){
        super(x, y, width, Math.max(7,Math.round(20*scale)), message, button->{
            press.onPress(button);
            if(icon==Icon.PAUSE)refreshPlaybackMessage(button);
        }, DEFAULT_NARRATION); this.icon=icon; this.label=label;this.scale=scale;
    }
    static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) {
        if(w<6||h<6){g.fill(x,y,x+w,y+h,color);return;}
        g.fill(x+3,y,x+w-3,y+1,color);g.fill(x+1,y+1,x+w-1,y+3,color);
        g.fill(x,y+3,x+w,y+h-3,color);
        g.fill(x+1,y+h-3,x+w-1,y+h-1,color);g.fill(x+3,y+h-1,x+w-3,y+h,color);
    }
    @Override public void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
        var player=InvuMusicPlayerClient.player;int accent=UiTheme.accent();
        int px=0,py=0,pw=Math.round(getWidth()/scale),ph=20;
        g.pose().pushMatrix();g.pose().translate((float)getX(),(float)getY());g.pose().scale(scale);
        boolean highlighted=isHoveredOrFocused(), selected=isSelected();
        active=switch(icon){case WAVE->!player.spotifySelected();case LIKE->player.current()!=null&&!player.current().youtube();case DISLIKE->player.current()!=null&&!player.current().youtube()&&!player.current().spotify();default->true;};
        int base=UiTheme.control();int background=0xff000000|tint(base,highlighted||selected?32:16);
        rounded(g,px,py+1,pw,ph,0x48000000);
        rounded(g,px,py,pw,ph,background);
        if(highlighted)g.fill(px+7,py+ph-2,px+pw-7,py+ph-1,0xff000000|accent);
        int ix=label?px+7:px+(pw-10)/2,iy=py+5,c=active?(selected||isHoveredOrFocused()?0xff000000|accent:0xffc9d2de):0xff586270;
        if(icon==Icon.PAUSE)refreshPlaybackMessage(this);
        switch(icon) {
            case LIKE -> {for(int row=0;row<9;row++){int inset=row==0?1:row<3?0:row-3;int end=10-inset;if(row<3){g.fill(ix+inset,iy+row,ix+4,iy+row+1,c);g.fill(ix+6,iy+row,ix+end,iy+row+1,c);}else if(end>inset){g.fill(ix+inset,iy+row,ix+end,iy+row+1,c);}}if(!selected){g.fill(ix+1,iy+2,ix+4,iy+4,background);g.fill(ix+6,iy+2,ix+9,iy+4,background);g.fill(ix+2,iy+4,ix+8,iy+5,background);g.fill(ix+3,iy+5,ix+7,iy+6,background);g.fill(ix+4,iy+6,ix+6,iy+7,background);}}
            case DISLIKE -> {g.fill(ix,iy,ix+2,iy+6,c);g.fill(ix+3,iy,ix+9,iy+6,c);g.fill(ix+4,iy+6,ix+7,iy+10,c);if(!selected)g.fill(ix+4,iy+1,ix+8,iy+5,background);}
            case BACKGROUND -> {g.fill(ix,iy,ix+10,iy+1,c);g.fill(ix,iy+9,ix+10,iy+10,c);g.fill(ix,iy,ix+1,iy+10,c);g.fill(ix+9,iy,ix+10,iy+10,c);g.fill(ix+2,iy+2,ix+5,iy+8,c);g.fill(ix+6,iy+4,ix+8,iy+8,c);}
            case CLOCK -> {g.fill(ix+2,iy,ix+8,iy+1,c);g.fill(ix+2,iy+9,ix+8,iy+10,c);g.fill(ix,iy+2,ix+1,iy+8,c);g.fill(ix+9,iy+2,ix+10,iy+8,c);for(int[] pos:new int[][]{{1,1},{8,1},{1,8},{8,8}})g.fill(ix+pos[0],iy+pos[1],ix+pos[0]+1,iy+pos[1]+1,c);g.fill(ix+4,iy+2,ix+5,iy+6,c);g.fill(ix+4,iy+5,ix+8,iy+6,c);}
            case SHUFFLE -> {for(int i=0;i<7;i++){g.fill(ix+i,iy+i+1,ix+i+2,iy+i+2,c);g.fill(ix+i,iy+8-i,ix+i+2,iy+9-i,c);}g.fill(ix+7,iy,ix+10,iy+3,c);g.fill(ix+7,iy+7,ix+10,iy+10,c);}
            case WAVE -> {for(int i=0;i<5;i++){int h=new int[]{3,7,10,7,3}[i];g.fill(ix+i*2,iy+(10-h)/2,ix+i*2+1,iy+(10+h)/2,c);}}
            case LIBRARY -> {g.fill(ix,iy,ix+1,iy+10,c);g.fill(ix+4,iy+1,ix+5,iy+10,c);g.fill(ix+9,iy,ix+10,iy+10,c);g.fill(ix,iy,ix+4,iy+1,c);g.fill(ix+5,iy,ix+10,iy+1,c);g.fill(ix,iy+9,ix+10,iy+10,c);for(int row:new int[]{3,6}){g.fill(ix+2,iy+row,ix+3,iy+row+1,c);g.fill(ix+6,iy+row,ix+8,iy+row+1,c);}}
            case MOVE -> {g.fill(ix+4,iy,ix+6,iy+10,c);g.fill(ix,iy+4,ix+10,iy+6,c);g.fill(ix+3,iy+1,ix+7,iy+2,c);g.fill(ix+3,iy+8,ix+7,iy+9,c);g.fill(ix+1,iy+3,ix+2,iy+7,c);g.fill(ix+8,iy+3,ix+9,iy+7,c);}
            case TRASH -> {g.fill(ix,iy+1,ix+10,iy+3,c);g.fill(ix+3,iy,ix+7,iy+1,c);g.fill(ix+2,iy+4,ix+8,iy+10,c);g.fill(ix+3,iy+5,ix+4,iy+9,background);g.fill(ix+6,iy+5,ix+7,iy+9,background);}
            case ACCOUNT -> {g.fill(ix+3,iy,ix+7,iy+4,c);g.fill(ix+1,iy+6,ix+9,iy+10,c);g.fill(ix+2,iy+5,ix+8,iy+6,c);}
            case MUSIC -> {g.fill(ix+2,iy,ix+4,iy+8,c);g.fill(ix+4,iy,ix+9,iy+2,c);g.fill(ix,iy+7,ix+4,iy+10,c);}
            case SETTINGS -> {g.fill(ix+3,iy,ix+7,iy+10,c);g.fill(ix,iy+3,ix+10,iy+7,c);g.fill(ix+1,iy+1,ix+9,iy+9,c);g.fill(ix+3,iy+3,ix+7,iy+7,background);}
            case PAUSE -> {if(player.playing()){g.fill(ix+1,iy,ix+4,iy+10,c);g.fill(ix+6,iy,ix+9,iy+10,c);}else triangle(g,ix+2,iy,c,false);}
            case PREVIOUS -> {g.fill(ix,iy,ix+2,iy+10,c);triangle(g,ix+3,iy,c,true);}
            case NEXT -> {triangle(g,ix,iy,c,false);g.fill(ix+8,iy,ix+10,iy+10,c);}
        }
        if(icon==Icon.SHUFFLE){setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("invumusicplayer.player.shuffle_state",UiText.toggle(player.settings.shuffle))));if(selected){g.fill(pw-7,3,pw-3,4,0xff000000|accent);g.fill(pw-6,2,pw-4,5,0xff000000|accent);}}
        if(icon==Icon.BACKGROUND)setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("invumusicplayer.settings.album_background",UiText.toggle(player.settings.albumBackground))));
        if(icon==Icon.CLOCK)setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("invumusicplayer.settings.time",UiText.toggle(player.settings.showTime))));
        if(label){var font=Minecraft.getInstance().font;g.text(font,font.plainSubstrByWidth(getMessage().getString(),Math.max(0,pw-26)),px+21,py+6,0xff000000|player.settings.textColor,false);}
        g.pose().popMatrix();
    }
    private static int tint(int color,int amount){return (Math.min(255,((color>>16)&255)+amount)<<16)|(Math.min(255,((color>>8)&255)+amount)<<8)|Math.min(255,(color&255)+amount);}
    boolean isSelected(){var p=InvuMusicPlayerClient.player;return icon==Icon.LIKE?p.rating()==1:icon==Icon.DISLIKE?p.rating()==-1:icon==Icon.BACKGROUND?p.settings.albumBackground:icon==Icon.CLOCK?p.settings.showTime:icon==Icon.SHUFFLE&&p.settings.shuffle;}
    static int themeColor(){var p=InvuMusicPlayerClient.player;return p.settings.albumBackground&&p.settings.menuCoverColors&&p.settings.covers&&p.current()!=null&&!p.current().youtube()&&InvuMusicPlayerClient.cover.ready()?InvuMusicPlayerClient.cover.paletteColor():p.settings.background;}
    static int themeAccent(){var p=InvuMusicPlayerClient.player;return p.settings.menuCoverColors&&p.settings.covers&&p.current()!=null&&!p.current().youtube()&&InvuMusicPlayerClient.cover.ready()?InvuMusicPlayerClient.cover.paletteAccent():p.settings.accent;}
    private static void refreshPlaybackMessage(Button button) {
        Component message=Component.translatable(InvuMusicPlayerClient.player.playing()?"invumusicplayer.player.pause":"invumusicplayer.player.play");
        if(!button.getMessage().equals(message)){button.setMessage(message);button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(message));}
    }
    private static void triangle(GuiGraphicsExtractor g,int x,int y,int c,boolean reverse) {
        for(int i=0;i<5;i++){int at=reverse?x+4-i:x+i;g.fill(at,y+i,at+1,y+10-i,c);}
    }
}
