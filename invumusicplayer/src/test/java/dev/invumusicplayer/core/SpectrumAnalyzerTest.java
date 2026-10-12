package dev.invumusicplayer.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SpectrumAnalyzerTest {
    private static byte[] tone(double frequency,float gain,int channels,int sampleRate,int frames) {
        byte[] pcm=new byte[frames*channels*2];
        for(int i=0;i<frames;i++){short sample=(short)(Math.sin(2*Math.PI*frequency*i/sampleRate)*32767*gain);
            for(int c=0;c<channels;c++){int at=(i*channels+c)*2;pcm[at]=(byte)sample;pcm[at+1]=(byte)(sample>>8);}}
        return pcm;
    }
    @Test void silenceHasNoInventedRhythm() {
        float[] levels=new SpectrumAnalyzer().accept(new byte[44100*4],44100*4,2,44100);
        assertArrayEquals(new float[24],levels);
    }
    @Test void LowAndHighTonesOccupyDifferentBandsAndStereoDecodes() {
        float[] low=new SpectrumAnalyzer().accept(tone(220,1,2,44100,22050),22050*4,2,44100);
        float[] high=new SpectrumAnalyzer().accept(tone(8000,1,1,44100,22050),22050*2,1,44100);
        assertTrue(maxIndex(low)<maxIndex(high)-8);assertTrue(low[maxIndex(low)]>.7);assertTrue(high[maxIndex(high)]>.7);
    }
    @Test void envelopeDecaysOnSilenceAndVolumeChangesMeasuredAmplitude() {
        SpectrumAnalyzer analyzer=new SpectrumAnalyzer();
        float[] loud=analyzer.accept(tone(1000,1,1,48000,24000),48000,1,48000);
        float[] quiet=new SpectrumAnalyzer().accept(tone(1000,.01f,1,48000,24000),48000,1,48000);
        assertTrue(loud[maxIndex(loud)]>quiet[maxIndex(quiet)]+.4);
        float[] after=analyzer.accept(new byte[48000*2],48000*2,1,48000);
        assertTrue(after[maxIndex(after)]<.03);
    }
    private static int maxIndex(float[] levels){int best=0;for(int i=1;i<levels.length;i++)if(levels[i]>levels[best])best=i;return best;}
}
