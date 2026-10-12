package dev.invumusicplayer.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Smooth rendering follows the measured spectrum. Pauses and silence decay to a quiet baseline. */
final class AudioVisualizer {
    private final float[] bars = new float[24], peaks = new float[24];
    private long previous;
    void draw(GuiGraphicsExtractor g,float[] spectrum,int x,int y,int width,int height,int color) {
        long now=System.nanoTime();double dt=previous==0?1.0/60:Math.min(.1,(now-previous)/1e9);previous=now;
        for(int i=0;i<bars.length;i++) {
            float target=i<spectrum.length&&Float.isFinite(spectrum[i])?Math.max(0,Math.min(1,spectrum[i])):0;
            double blend=1-Math.exp(-dt/(target>bars[i]?.055:.20));bars[i]+=(target-bars[i])*blend;
            peaks[i]=Math.max(bars[i],peaks[i]-(float)(dt*.75));
            int left=x+i*width/bars.length,right=x+(i+1)*width/bars.length-2;
            int h=Math.max(1,Math.round(bars[i]*(height-2)));right=Math.max(left+1,right);
            for(int row=0;row<h;row++){int alpha=150+105*row/Math.max(1,h);g.fill(left,y+height-row-1,right,y+height-row,(alpha<<24)|color);}
            int peak=Math.round(peaks[i]*(height-2));if(peak>h+1)g.fill(left,y+height-peak-1,right,y+height-peak,0x75000000|color);
        }
    }
}
