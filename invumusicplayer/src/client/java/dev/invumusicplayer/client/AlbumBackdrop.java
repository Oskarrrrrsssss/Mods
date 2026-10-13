package dev.invumusicplayer.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A bounded, low-opacity halo moves slowly behind artwork and rests while playback is paused. */
final class AlbumBackdrop {
    private long previous;private double time;
    void draw(GuiGraphicsExtractor g,int x,int y,int size){
        var p=InvuMusicPlayerClient.player;long now=System.nanoTime();if(previous!=0&&p.playing())time+=Math.min(.1,(now-previous)/1e9);previous=now;
        if(!p.settings.artworkAnimation||!p.settings.albumBackground||!p.settings.menuCoverColors||!InvuMusicPlayerClient.cover.ready()||size<16)return;
        int accent=InvuMusicPlayerClient.cover.paletteAccent(),secondary=UiTheme.mix(accent,InvuMusicPlayerClient.cover.paletteColor(),.5f);
        blob(g,x+size*.35+Math.sin(time*.55)*size*.12,y+size*.5+Math.cos(time*.4)*size*.08,size*.63,accent);
        blob(g,x+size*.7+Math.cos(time*.45)*size*.1,y+size*.55+Math.sin(time*.35)*size*.1,size*.53,secondary);
    }
    private static void blob(GuiGraphicsExtractor g,double cx,double cy,double radius,int color){
        for(int ring=4;ring>=1;ring--){double r=radius*(.5+ring*.16);int alpha=ring==4?5:7;for(int row=0;row<12;row++){double a=-1+row/6.0,b=-1+(row+1)/6.0,m=(a+b)/2;double half=r*Math.sqrt(Math.max(0,1-m*m));g.fill((int)Math.floor(cx-half),(int)Math.floor(cy+a*r),(int)Math.ceil(cx+half),(int)Math.ceil(cy+b*r),(alpha<<24)|color);}}
    }
}
