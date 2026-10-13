package dev.invumusicplayer.core;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.sound.sampled.*;

/** Native decoder feeds PCM into this mod's JavaSound output. One process per track isolates stale loads. */
public final class NativeSpotifyPlayback implements Player.Playback {
    private final Path game;private final TokenStore credentials;private final AudioEngine.LineFactory lines;
    private final ExecutorService workers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"invumusicplayer-spotify-native");t.setDaemon(true);return t;});
    private volatile State active;private volatile float gain=.7f;private volatile JsonObject credential;
    private volatile boolean connecting,closed;private volatile Messages.Text authStatus=Messages.text("invumusicplayer.spotify.native_missing"),authError=Messages.EMPTY;
    private volatile URI loginUri;private SpotifyAuthorization flow;private long authGeneration;private Path executable;
    private static final class State{
        Process process;volatile SourceDataLine line;volatile boolean loaded,paused,ended,cancelled;volatile long offset,position;
        final SpectrumAnalyzer analyzer=new SpectrumAnalyzer();volatile float[] spectrum=new float[24];
        final Runnable ready,endedCallback;final Consumer<Exception> failed;
        State(boolean paused,long offset,Runnable ready,Runnable ended,Consumer<Exception> failed){this.paused=paused;this.offset=this.position=offset;this.ready=ready;endedCallback=ended;this.failed=failed;}
    }
    public NativeSpotifyPlayback(Path game){this(game,new TokenStore(game.resolve("config/invumusicplayer-spotify-playback.dat")),AudioSystem::getSourceDataLine);}
    NativeSpotifyPlayback(Path game,TokenStore credentials,AudioEngine.LineFactory lines){this.game=game;this.credentials=credentials;this.lines=lines;}
    public synchronized boolean authorized(){return credential!=null||Files.isRegularFile(game.resolve("config/invumusicplayer-spotify-playback.dat"));}
    public boolean connecting(){return connecting;}public String authStatus(){return !connecting&&authStatus.key().equals("invumusicplayer.spotify.native_missing")&&authorized()?Messages.get("invumusicplayer.spotify.native_stored"):authStatus.getString();}public String authError(){return authError.getString();}
    public synchronized URI takeLoginUri(){URI uri=loginUri;loginUri=null;return uri;}
    public synchronized void login(){if(closed)return;long generation=++authGeneration;if(flow!=null)flow.close();connecting=true;authError=Messages.EMPTY;authStatus=Messages.text("invumusicplayer.spotify.browser_wait");
        workers.submit(()->{try{Path exe=prepare();JsonObject identity=runSmall(exe,"--identity",null);if(identity.get("protocol").getAsInt()!=1)throw Messages.error("invumusicplayer.spotify.native_protocol");String clientId=identity.get("client_id").getAsString();
            synchronized(this){if(closed||generation!=authGeneration)return;flow=new SpotifyAuthorization(clientId,8898,"/login",List.of("streaming"),tokens->workers.submit(()->authenticate(exe,tokens.access(),generation)),e->{synchronized(this){if(generation==authGeneration&&!closed){authError=SpotifyMusic.safe(e);connecting=false;}}});loginUri=flow.uri();}
        }catch(Exception e){synchronized(this){if(generation==authGeneration&&!closed){authError=SpotifyMusic.safe(e);connecting=false;}}}});
    }
    private void authenticate(Path exe,String access,long generation){try{JsonObject command=new JsonObject();command.addProperty("mode","authenticate");command.addProperty("access_token",access);JsonObject response=runSmall(exe,null,command);if(!SpotifyMusic.str(response,"event","").equals("authenticated"))throw Messages.error("invumusicplayer.spotify.native_authorization");JsonObject saved=response.getAsJsonObject("credentials");validateCredentials(saved);
        synchronized(this){if(closed||generation!=authGeneration)return;credentials.save(Base64.getEncoder().encodeToString(saved.toString().getBytes(StandardCharsets.UTF_8)));credential=saved;authStatus=Messages.text("invumusicplayer.spotify.native_ready");authError=Messages.EMPTY;connecting=false;}
    }catch(Exception e){synchronized(this){if(generation==authGeneration&&!closed){authError=SpotifyMusic.safe(e);connecting=false;}}}}
    public synchronized void disconnect(){++authGeneration;if(flow!=null){flow.close();flow=null;}credential=null;loginUri=null;connecting=false;stop();authStatus=Messages.text("invumusicplayer.spotify.native_missing");try{credentials.clear();}catch(Exception e){authError=SpotifyMusic.safe(e);}}
    private synchronized JsonObject loadCredential()throws IOException{if(credential!=null)return credential;String stored=credentials.load();if(stored.isBlank())throw Messages.error("invumusicplayer.spotify.native_login_required");try{JsonObject value=JsonParser.parseString(new String(Base64.getDecoder().decode(stored),StandardCharsets.UTF_8)).getAsJsonObject();validateCredentials(value);credential=value;return value;}catch(RuntimeException e){throw Messages.error("invumusicplayer.spotify.native_authorization");}}
    static void validateCredentials(JsonObject value)throws IOException{try{if(value==null||!value.has("auth_data")||!value.has("auth_type")||!value.has("username")||value.get("username").isJsonNull()||value.get("username").getAsString().isBlank()||value.get("auth_type").getAsInt()!=1||value.get("auth_data").getAsString().length()>2000||Base64.getDecoder().decode(value.get("auth_data").getAsString()).length==0)throw new IllegalArgumentException();}catch(RuntimeException e){throw Messages.error("invumusicplayer.spotify.native_authorization");}}
    public void playTrack(Track track,long seek,boolean paused,Runnable ready,Runnable ended,Consumer<Exception> failed){State state=new State(paused,Math.max(0,seek),ready,ended,failed);synchronized(this){stop();if(closed)return;active=state;}
        workers.submit(()->{try{JsonObject command=new JsonObject();command.addProperty("mode","play");command.add("credentials",loadCredential());command.addProperty("uri",track.id());command.addProperty("position",Math.max(0,seek));command.addProperty("paused",paused);start(state,prepare(),command,null);}catch(Exception e){fail(state,e);}});
    }
    private void start(State state,Path exe,JsonObject command,String argument)throws Exception{Process process=new ProcessBuilder(argument==null?List.of(exe.toString()):List.of(exe.toString(),argument)).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        synchronized(this){if(active!=state||state.cancelled){process.destroyForcibly();return;}state.process=process;}
        if(command!=null)send(process,command);
        try(var in=process.getInputStream()){String line;while((line=readLine(in,65536))!=null){if(active!=state||state.cancelled)break;JsonObject event=JsonParser.parseString(line).getAsJsonObject();accept(state,event);}}
        finally{process.destroyForcibly();}
        if(active==state&&!state.cancelled&&!state.ended)throw Messages.error("invumusicplayer.spotify.native_stopped");
    }
    private void accept(State state,JsonObject event)throws Exception{if(active!=state||state.cancelled)return;switch(SpotifyMusic.str(event,"event","")){
        case "playing","paused"->{boolean first=!state.loaded;state.paused=SpotifyMusic.str(event,"event","").equals("paused");if(first)openOutput(state);state.paused=SpotifyMusic.str(event,"event","").equals("paused");if(first)state.offset=SpotifyMusic.number(event,"position",state.offset);if(state.line!=null){if(state.paused)state.line.stop();else state.line.start();}if(first)state.ready.run();}
        case "pcm"->{byte[] bytes=Base64.getDecoder().decode(event.get("data").getAsString());if(bytes.length>32768||bytes.length%4!=0)throw Messages.error("invumusicplayer.spotify.native_protocol");if(state.paused)return;if(!state.loaded){openOutput(state);state.ready.run();}state.spectrum=state.analyzer.accept(bytes,bytes.length,2,44100);AudioEngine.applyVolume(bytes,bytes.length,gain);int written=0;while(written<bytes.length&&active==state&&!state.cancelled&&!state.paused){int count=state.line.write(bytes,written,bytes.length-written);if(count<=0)throw Messages.error("invumusicplayer.error.audio_disconnected");written+=count;}state.position=state.offset+state.line.getMicrosecondPosition()/1000;}
        case "position"->{} // JavaSound measures samples actually heard, rather than decoder read-ahead.
        case "ended"->{if(state.line!=null)state.line.drain();if(active==state&&!state.cancelled){state.ended=true;state.endedCallback.run();}}
        case "error"->{throw Messages.error(switch(SpotifyMusic.str(event,"code","")){case "authorization"->"invumusicplayer.spotify.native_authorization";case "unavailable"->"invumusicplayer.spotify.track_unavailable";case "protocol"->"invumusicplayer.spotify.native_protocol";default->"invumusicplayer.spotify.network_error";});}
        default->throw Messages.error("invumusicplayer.spotify.native_protocol");
    }}
    private void openOutput(State state)throws Exception{SourceDataLine line=lines.create(new AudioFormat(44100,16,2,true,false));line.open(new AudioFormat(44100,16,2,true,false),16384);if(active!=state||state.cancelled){line.close();throw Messages.error("invumusicplayer.spotify.native_stopped");}state.line=line;state.loaded=true;if(!state.paused)line.start();}
    private void fail(State state,Exception error){if(active!=state||state.cancelled)return;state.ended=true;state.loaded=false;if(state.line!=null){state.line.stop();state.line.close();}state.failed.accept(error instanceof Messages.Failure?error:Messages.error("invumusicplayer.spotify.native_stopped"));}
    @Override public void play(AudioEngine.Input input,long seek,boolean paused,Runnable ready,Runnable ended,Consumer<Exception> failed){failed.accept(Messages.error("invumusicplayer.spotify.native_protocol"));}
    @Override public boolean playing(){State s=active;return s!=null&&s.loaded&&!s.paused&&!s.ended&&!s.cancelled;}
    @Override public boolean loaded(){State s=active;return s!=null&&s.loaded&&!s.cancelled;}
    @Override public long positionMillis(){State s=active;return s==null?0:s.line==null?s.position:s.offset+s.line.getMicrosecondPosition()/1000;}
    @Override public long playedMillis(){State s=active;return s==null||s.line==null?0:s.line.getMicrosecondPosition()/1000;}
    @Override public long durationMillis(){return 0;}
    @Override public float[] spectrum(){State s=active;return s==null?new float[24]:s.spectrum.clone();}
    @Override public void volume(float gain){this.gain=Math.max(0,Math.min(1,gain));}
    @Override public synchronized void togglePause(){State s=active;if(s==null||!s.loaded||s.ended)return;s.paused=!s.paused;try{if(s.paused){s.line.stop();s.line.flush();}else s.line.start();JsonObject command=new JsonObject();command.addProperty("cmd",s.paused?"pause":"resume");send(s.process,command);}catch(Exception e){fail(s,e);}}
    @Override public synchronized void stop(){State s=active;active=null;if(s==null)return;s.cancelled=true;if(s.process!=null)s.process.destroyForcibly();if(s.line!=null){s.line.stop();s.line.flush();s.line.close();}}
    private synchronized Path prepare()throws Exception{if(executable!=null)return executable;String platform=System.getProperty("os.name").startsWith("Windows")?"windows-x64":System.getProperty("os.name").startsWith("Linux")?"linux-x64":"";if(platform.isBlank()||!System.getProperty("os.arch").matches("amd64|x86_64"))throw Messages.error("invumusicplayer.spotify.native_unsupported");String name="invumusicplayer-spotify"+(platform.startsWith("windows")?".exe":"");String resource="/spotify-native/"+platform+"/";String hash;
        try(var in=getClass().getResourceAsStream(resource+"sha256.txt")){if(in==null)throw Messages.error("invumusicplayer.spotify.native_missing_binary");hash=new String(in.readNBytes(100),StandardCharsets.US_ASCII).strip();}
        Path target=game.resolve("tools/invumusicplayer/spotify").resolve(name);Files.createDirectories(target.getParent());
        if(!Files.isRegularFile(target)||!hash(target).equals(hash)){try(var in=getClass().getResourceAsStream(resource+name)){if(in==null)throw Messages.error("invumusicplayer.spotify.native_missing_binary");Path tmp=Files.createTempFile(target.getParent(),"spotify-",".tmp");try{Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING);if(!hash(tmp).equals(hash))throw Messages.error("invumusicplayer.spotify.native_hash");Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}}}
        if(!platform.startsWith("windows")&&!target.toFile().setExecutable(true,false))throw Messages.error("invumusicplayer.spotify.native_missing_binary");executable=target;return target;
    }
    static String hash(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] buffer=new byte[65536];int count;long total=0;while((count=in.read(buffer))>=0){total+=count;if(total>128L*1024*1024)throw new IOException();digest.update(buffer,0,count);}}return HexFormat.of().formatHex(digest.digest());}
    private static JsonObject runSmall(Path exe,String argument,JsonObject command)throws Exception{Process process=new ProcessBuilder(argument==null?List.of(exe.toString()):List.of(exe.toString(),argument)).redirectError(ProcessBuilder.Redirect.DISCARD).start();ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"invumusicplayer-spotify-process-timeout");t.setDaemon(true);return t;});timer.schedule(process::destroyForcibly,75,TimeUnit.SECONDS);
        try{if(command!=null)send(process,command);String line=readLine(process.getInputStream(),16384);if(line==null)throw Messages.error("invumusicplayer.spotify.native_stopped");JsonObject result=JsonParser.parseString(line).getAsJsonObject();if(SpotifyMusic.str(result,"event","").equals("error"))throw Messages.error("invumusicplayer.spotify.native_authorization");return result;}finally{process.destroyForcibly();timer.shutdownNow();}
    }
    private static void send(Process process,JsonObject command)throws IOException{if(process==null||!process.isAlive())throw Messages.error("invumusicplayer.spotify.native_stopped");synchronized(process){var out=process.getOutputStream();out.write((command+"\n").getBytes(StandardCharsets.UTF_8));out.flush();}}
    static String readLine(InputStream input,int limit)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();int value;while((value=input.read())>=0){if(value=='\n')return bytes.toString(StandardCharsets.UTF_8);if(bytes.size()>=limit)throw Messages.error("invumusicplayer.spotify.native_protocol");bytes.write(value);}return bytes.size()==0?null:bytes.toString(StandardCharsets.UTF_8);}
    public void verifyInstallation()throws Exception{JsonObject identity=runSmall(prepare(),"--identity",null);if(identity.get("protocol").getAsInt()!=1)throw Messages.error("invumusicplayer.spotify.native_protocol");}
    void testExecutable(Path exe){executable=exe;}
    void testStream(Runnable ready,Runnable ended,Consumer<Exception> failed){State state=new State(false,0,ready,ended,failed);synchronized(this){stop();active=state;}workers.submit(()->{try{start(state,prepare(),null,"--self-test");}catch(Exception e){fail(state,e);}});}
    @Override public synchronized void close(){closed=true;++authGeneration;if(flow!=null)flow.close();stop();workers.shutdownNow();}
}
