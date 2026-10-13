package dev.invumusicplayer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sound.sampled.SourceDataLine;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Uses the actual platform executable and production JavaSound bridge without an account or speaker. */
final class SpotifyNativeTest{
    @TempDir Path temp;
    @Test void bundledNativeEngineFeedsPcmToModAndSpectrumIsIndependentOfVolume()throws Exception{
        assumeTrue(Boolean.getBoolean("invu.spotify.native.check"),"Native decoder is built in CI / Декодер собирается в CI");
        float[] reference=null;
        for(float volume:new float[]{1f,.1f,0f}){
            ByteArrayOutputStream written=new ByteArrayOutputStream();AtomicLong bytes=new AtomicLong();AtomicBoolean open=new AtomicBoolean();AtomicReference<Exception> failure=new AtomicReference<>();AtomicInteger ready=new AtomicInteger();CountDownLatch ended=new CountDownLatch(1);
            SourceDataLine line=(SourceDataLine)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SourceDataLine.class},(proxy,method,args)->switch(method.getName()){
                case "open"->{open.set(true);yield null;}case "close"->{open.set(false);yield null;}case "isOpen"->open.get();
                case "write"->{written.write((byte[])args[0],(Integer)args[1],(Integer)args[2]);bytes.addAndGet((Integer)args[2]);yield args[2];}
                case "getMicrosecondPosition"->bytes.get()*1_000_000/176400;
                case "start","stop","flush","drain"->null;default->method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:method.getReturnType()==long.class?0L:null;
            });
            try(NativeSpotifyPlayback player=new NativeSpotifyPlayback(temp,new TokenStore(temp.resolve("unused-auth")),format->line)){
                player.verifyInstallation();player.volume(volume);player.testStream(ready::incrementAndGet,ended::countDown,e->{failure.set(e);ended.countDown();});assertTrue(ended.await(15,TimeUnit.SECONDS));assertNull(failure.get());assertEquals(1,ready.get());assertEquals(176400,written.size());assertEquals(1000,player.positionMillis());
                float[] spectrum=player.spectrum();if(reference==null){reference=spectrum;float max=0;for(float v:spectrum)max=Math.max(max,v);assertTrue(max>.2f);}else assertArrayEquals(reference,spectrum,.00001f);
                boolean nonzero=false;for(byte b:written.toByteArray())if(b!=0)nonzero=true;assertEquals(volume>0,nonzero);
            }
        }
    }
}
