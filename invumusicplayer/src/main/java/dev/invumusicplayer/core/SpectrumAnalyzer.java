package dev.invumusicplayer.core;

/** 2048-point FFT with logarithmic bands and time-based attack/release. PCM is measured before output gain. */
final class SpectrumAnalyzer {
    static final int BANDS = 24, SIZE = 2048, HOP = SIZE / 2;
    private final double[] window = new double[SIZE], samples = new double[SIZE], real = new double[SIZE], imaginary = new double[SIZE];
    private final float[] envelope = new float[BANDS];
    private int count;
    SpectrumAnalyzer() { for(int i=0;i<SIZE;i++)window[i]=.5-.5*Math.cos(2*Math.PI*i/(SIZE-1)); }
    float[] accept(byte[] pcm,int length,int channels,float rate) {
        int stride=channels*2;
        for(int i=0;i+stride<=length;i+=stride) {
            double value=0;for(int c=0;c<channels;c++){int at=i+c*2;value+=(short)((pcm[at]&255)|(pcm[at+1]<<8))/32768.0;}
            samples[count++]=value/channels;
            if(count==SIZE){transform(rate);System.arraycopy(samples,HOP,samples,0,HOP);count=HOP;}
        }
        return envelope.clone();
    }
    private void transform(float rate) {
        for(int i=0;i<SIZE;i++){int reversed=Integer.reverse(i) >>> (32-11);real[reversed]=samples[i]*window[i];imaginary[reversed]=0;}
        for(int size=2;size<=SIZE;size<<=1) {
            double wr=Math.cos(-2*Math.PI/size),wi=Math.sin(-2*Math.PI/size);int half=size/2;
            for(int start=0;start<SIZE;start+=size){double re=1,im=0;
                for(int j=0;j<half;j++){int a=start+j,b=a+half;double tr=re*real[b]-im*imaginary[b],ti=re*imaginary[b]+im*real[b];
                    real[b]=real[a]-tr;imaginary[b]=imaginary[a]-ti;real[a]+=tr;imaginary[a]+=ti;
                    double next=re*wr-im*wi;im=re*wi+im*wr;re=next;}
            }
        }
        double maximum=Math.min(16000,rate*.48),ratio=maximum/40;
        for(int band=0;band<BANDS;band++) {
            double low=40*Math.pow(ratio,band/(double)BANDS),high=40*Math.pow(ratio,(band+1)/(double)BANDS);
            int first=Math.max(1,(int)Math.floor(low*SIZE/rate)),last=Math.min(SIZE/2,(int)Math.ceil(high*SIZE/rate));
            double amplitude=0;for(int bin=first;bin<=last;bin++)amplitude=Math.max(amplitude,Math.hypot(real[bin],imaginary[bin])*4/SIZE);
            float target=(float)Math.max(0,Math.min(1,(20*Math.log10(Math.max(amplitude,1e-9))+60)/60));
            double seconds=HOP/rate,blend=1-Math.exp(-seconds/(target>envelope[band]?.045:.24));
            envelope[band]+=(target-envelope[band])*blend;
        }
    }
}
