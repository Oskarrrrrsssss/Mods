package dev.invumusicplayer.core;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Official Web API for metadata/library only. Full audio is handled by NativeSpotifyPlayback. */
public final class SpotifyMusic implements AutoCloseable {
    interface Transport { Response send(String method,String path,String bearer,String body)throws Exception; }
    record Response(int status,String body,String retryAfter){}
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final List<String> SCOPES=List.of("user-read-private","user-library-read","user-library-modify","playlist-read-private","playlist-read-collaborative","playlist-modify-private","playlist-modify-public");
    private final Settings settings;private final TokenStore credentials;private final Transport transport;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"invumusicplayer-spotify-account");t.setDaemon(true);return t;});
    private volatile String access="",refresh="",owner="";private volatile long expiry,retryUntil;private long generation;private volatile boolean connecting,closed,verified;
    private final Object tokenLock=new Object();
    private volatile Messages.Text status=Messages.text("invumusicplayer.account.disconnected"),error=Messages.EMPTY;
    private SpotifyAuthorization flow;private volatile URI loginUri;
    public SpotifyMusic(Path game,Settings settings){this(settings,new TokenStore(game.resolve("config/invumusicplayer-spotify-auth.dat")),SpotifyMusic::sendHttp);worker.submit(this::restore);}
    SpotifyMusic(Settings settings,TokenStore credentials,Transport transport){this.settings=settings;this.credentials=credentials;this.transport=transport;}
    private void restore(){long request; synchronized(this){if(closed||settings.spotifyClientId.isBlank())return;request=generation;}
        try{String saved=credentials.load();if(saved.isBlank())return;JsonObject data=JsonParser.parseString(new String(Base64.getDecoder().decode(saved),StandardCharsets.UTF_8)).getAsJsonObject();
            synchronized(this){if(closed||generation!=request||!data.get("client_id").getAsString().equals(settings.spotifyClientId))return;refresh=TokenInput.normalize(data.get("refresh").getAsString());}
            refreshAccess();String user=profile();synchronized(this){if(closed||generation!=request)return;owner=user;verified=true;status=Messages.text("invumusicplayer.spotify.connected");}
        }catch(Exception e){synchronized(this){if(closed||generation!=request)return;access="";verified=false;error=safe(e);status=Messages.text("invumusicplayer.account.reconnect");}}
    }
    public synchronized void login(String clientId){
        if(closed)return;if(!clientId.matches("[a-fA-F0-9]{32}")){error=Messages.text("invumusicplayer.spotify.client_invalid");return;}
        cancelFlow();long request=++generation;connecting=true;error=Messages.EMPTY;status=Messages.text("invumusicplayer.spotify.browser_wait");if(!settings.spotifyClientId.equals(clientId)){access=refresh=owner="";verified=false;}settings.spotifyClientId=clientId;
        try{flow=new SpotifyAuthorization(clientId,43827,"/callback",SCOPES,tokens->{try{synchronized(this){if(closed||generation!=request)return;apply(tokens);verified=false;}String user=profile();synchronized(this){if(closed||generation!=request)return;owner=user;saveRefresh();verified=true;status=Messages.text("invumusicplayer.spotify.connected");}}catch(Exception e){synchronized(this){if(!closed&&generation==request){access="";verified=false;error=safe(e);}}}finally{synchronized(this){if(generation==request)connecting=false;}}},e->{synchronized(this){if(generation==request&&!closed){error=safe(e);connecting=false;}}});loginUri=flow.uri();}catch(IOException e){connecting=false;error=Messages.text("invumusicplayer.spotify.callback_busy");}
    }
    public synchronized URI takeLoginUri(){URI uri=loginUri;loginUri=null;return uri;}
    public boolean connected(){return verified&&!access.isBlank();}
    public String accountId(){return owner;}public boolean connecting(){return connecting;}public String status(){return status.getString();}public String error(){return error.getString();}
    public synchronized void disconnect(){++generation;cancelFlow();access=refresh=owner="";verified=false;connecting=false;error=Messages.EMPTY;status=Messages.text("invumusicplayer.account.signed_out");try{credentials.clear();}catch(Exception e){error=safe(e);}}
    private void cancelFlow(){if(flow!=null){flow.close();flow=null;}loginUri=null;}
    private void apply(SpotifyAuthorization.Tokens t){access=t.access();if(!t.refresh().isBlank())refresh=t.refresh();expiry=System.nanoTime()+TimeUnit.SECONDS.toNanos(t.expires()-10);}
    private void saveRefresh()throws IOException{if(refresh.isBlank())throw Messages.error("invumusicplayer.spotify.oauth_failed");JsonObject data=new JsonObject();data.addProperty("client_id",settings.spotifyClientId);data.addProperty("refresh",refresh);credentials.save(Base64.getEncoder().encodeToString(data.toString().getBytes(StandardCharsets.UTF_8)));}
    private void refreshAccess()throws Exception{synchronized(tokenLock){String refreshToken,clientId;long request; synchronized(this){if(closed||refresh.isBlank())throw Messages.error("invumusicplayer.spotify.login_required");refreshToken=refresh;clientId=settings.spotifyClientId;request=generation;}
        var tokens=SpotifyAuthorization.tokenRequest(URI.create("https://accounts.spotify.com/api/token"),Map.of("grant_type","refresh_token","refresh_token",refreshToken,"client_id",clientId));
        synchronized(this){if(closed||request!=generation)throw Messages.error("invumusicplayer.spotify.login_required");apply(tokens);saveRefresh();}
    }}
    private String profile()throws Exception{JsonObject me=request("GET","/me",null).getAsJsonObject();String user=str(me,"id","");if(user.isBlank())throw Messages.error("invumusicplayer.spotify.response_invalid");if(me.has("product")&&!str(me,"product","").equals("premium"))throw Messages.error("invumusicplayer.spotify.premium_required");return user;}
    private JsonElement request(String method,String path,JsonElement body)throws Exception{
        if(closed)throw Messages.error("invumusicplayer.spotify.login_required");if(System.nanoTime()<retryUntil)throw Messages.error("invumusicplayer.spotify.rate_limit");
        if(access.isBlank())throw Messages.error("invumusicplayer.spotify.login_required");if(System.nanoTime()>=expiry)refreshAccess();
        Response response=transport.send(method,path,access,body==null?"":body.toString());
        if(response.status()==401){refreshAccess();response=transport.send(method,path,access,body==null?"":body.toString());}
        if(response.status()==429){long seconds=60;try{seconds=Long.parseLong(response.retryAfter());}catch(Exception ignored){}retryUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.max(1,Math.min(86400,seconds)));throw Messages.error("invumusicplayer.spotify.rate_limit");}
        if(response.status()==403)throw Messages.error("invumusicplayer.spotify.forbidden");
        if(response.status()==401)throw Messages.error("invumusicplayer.spotify.login_required");
        if(response.status()<200||response.status()>=300)throw Messages.error("invumusicplayer.spotify.http_error",response.status());
        if(response.status()==204||response.body().isBlank())return JsonNull.INSTANCE;
        try{return JsonParser.parseString(response.body());}catch(RuntimeException e){throw Messages.error("invumusicplayer.spotify.response_invalid");}
    }
    private static Response sendHttp(String method,String path,String bearer,String body)throws Exception{
        if(!path.startsWith("/")||path.startsWith("//")||path.contains("\r")||path.contains("\n"))throw Messages.error("invumusicplayer.spotify.response_invalid");
        var builder=HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1"+path)).timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+TokenInput.normalize(bearer));
        if(!body.isBlank())builder.header("Content-Type","application/json");
        var response=HTTP.send(builder.method(method,body.isBlank()?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;try(var in=response.body()){bytes=YandexMusic.bounded(in,4_000_000);}return new Response(response.statusCode(),new String(bytes,StandardCharsets.UTF_8),response.headers().firstValue("Retry-After").orElse("60"));
    }
    public List<Track> search(String query)throws Exception{JsonObject data=request("GET","/search?type=track&limit=10&q="+SpotifyAuthorization.enc(query),null).getAsJsonObject();return trackPage(data.getAsJsonObject("tracks"),false);}
    public List<Track> liked()throws Exception{return pagedTracks("/me/tracks?limit=50",true);}
    public List<MusicPlaylist> playlists()throws Exception{List<MusicPlaylist> result=new ArrayList<>();String path="/me/playlists?limit=50";for(int page=0;path!=null&&page<200;page++){
        JsonObject data=request("GET",path,null).getAsJsonObject();for(JsonElement e:array(data,"items")){if(!e.isJsonObject())continue;JsonObject p=e.getAsJsonObject();String id=str(p,"id","");if(validId(id)){String user=p.has("owner")&&p.get("owner").isJsonObject()?str(p.getAsJsonObject("owner"),"id",""):"";result.add(new MusicPlaylist(user,id,str(p,"name",""),"spotify"));}}path=next(data);
    }return List.copyOf(result);}
    public List<Track> playlist(String id)throws Exception{return pagedTracks("/playlists/"+checkedId(id)+"/items?limit=50",true);}
    private List<Track> pagedTracks(String initial,boolean wrapped)throws Exception{List<Track> result=new ArrayList<>();String path=initial;for(int page=0;path!=null&&page<200&&result.size()<10000;page++){
        JsonObject data=request("GET",path,null).getAsJsonObject();result.addAll(trackPage(data,wrapped));path=next(data);
    }return List.copyOf(result.subList(0,Math.min(10000,result.size())));}
    private static List<Track> trackPage(JsonObject data,boolean wrapped){List<Track> result=new ArrayList<>();if(data==null)return result;for(JsonElement e:array(data,"items")){if(!e.isJsonObject())continue;JsonObject item=e.getAsJsonObject();if(wrapped){JsonElement child=item.has("item")?item.get("item"):item.get("track");if(child==null||!child.isJsonObject())continue;item=child.getAsJsonObject();}Track t=track(item);if(t!=null)result.add(t);}return List.copyOf(result);}
    static Track track(JsonObject obj){String id=str(obj,"id","");if(!validId(id)||str(obj,"type","track").equals("episode")||bool(obj,"is_local",false)||obj.has("is_playable")&&!bool(obj,"is_playable",true))return null;
        List<String> artists=new ArrayList<>();for(JsonElement a:array(obj,"artists"))if(a.isJsonObject())artists.add(str(a.getAsJsonObject(),"name",""));
        String cover="";if(obj.has("album")&&obj.get("album").isJsonObject()){var images=array(obj.getAsJsonObject("album"),"images");if(!images.isEmpty()&&images.get(0).isJsonObject())cover=str(images.get(0).getAsJsonObject(),"url","");}
        return new Track("spotify:track:"+id,str(obj,"name",""),String.join(", ",artists),number(obj,"duration_ms",0),null,null,cover,"");
    }
    public Track trackById(String id)throws Exception{return track(request("GET","/tracks/"+checkedId(id),null).getAsJsonObject());}
    public boolean saved(Track track)throws Exception{var data=request("GET","/me/library/contains?uris="+SpotifyAuthorization.enc(track.id()),null).getAsJsonArray();return !data.isEmpty()&&data.get(0).getAsBoolean();}
    public void like(Track track,boolean remove)throws Exception{request(remove?"DELETE":"PUT","/me/library?uris="+SpotifyAuthorization.enc(track.id()),null);}
    public void removePlaylist(MusicPlaylist playlist)throws Exception{request("DELETE","/me/library?uris="+SpotifyAuthorization.enc("spotify:playlist:"+checkedId(playlist.kind())),null);}
    public List<Track> importLink(String link)throws Exception{URI uri=URI.create(link);if(!"https".equals(uri.getScheme())||!"open.spotify.com".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443)throw Messages.error("invumusicplayer.error.playlist_link");String[] parts=uri.getPath().replaceFirst("^/intl-[a-z-]+/","/").split("/");if(parts.length!=3)throw Messages.error("invumusicplayer.error.playlist_link");return switch(parts[1]){case "playlist"->playlist(parts[2]);case "track"->List.of(Objects.requireNonNull(trackById(parts[2])));default->throw Messages.error("invumusicplayer.error.playlist_link");};}
    public byte[] cover(Track track)throws Exception{if(track.coverUrl().isBlank())return new byte[0];URI uri=URI.create(track.coverUrl());validateCover(uri);var response=HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());try(var in=response.body()){if(response.statusCode()!=200)throw Messages.error("invumusicplayer.spotify.http_error",response.statusCode());return YandexMusic.bounded(in,8_000_000);}}
    static void validateCover(URI uri)throws IOException{String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||!(host.equals("scdn.co")||host.endsWith(".scdn.co"))||uri.getUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443)throw Messages.error("invumusicplayer.spotify.response_invalid");}
    private static String next(JsonObject obj)throws IOException{String next=str(obj,"next","");if(next.isBlank())return null;try{URI uri=URI.create(next);if(!"https".equals(uri.getScheme())||!"api.spotify.com".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443||!uri.getPath().startsWith("/v1/"))throw new IllegalArgumentException();return uri.getRawPath().substring(3)+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());}catch(RuntimeException e){throw Messages.error("invumusicplayer.spotify.response_invalid");}}
    static String str(JsonObject o,String key,String fallback){var value=o.get(key);try{return value==null||value.isJsonNull()?fallback:value.getAsString();}catch(RuntimeException e){return fallback;}}
    static boolean bool(JsonObject o,String key,boolean fallback){try{return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsBoolean():fallback;}catch(RuntimeException e){return fallback;}}
    static long number(JsonObject o,String key,long fallback){try{return o.has(key)&&!o.get(key).isJsonNull()?Math.max(0,o.get(key).getAsLong()):fallback;}catch(RuntimeException e){return fallback;}}
    static JsonArray array(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray();}
    static boolean validId(String id){return id.matches("[A-Za-z0-9]{22}");}
    private static String checkedId(String id)throws IOException{if(!validId(id))throw Messages.error("invumusicplayer.spotify.response_invalid");return id;}
    static Messages.Text safe(Exception e){return e instanceof Messages.Failure f?f.text():Messages.text("invumusicplayer.spotify.network_error");}
    synchronized void testAccess(String token){access=token;verified=true;expiry=Long.MAX_VALUE;}
    @Override public synchronized void close(){closed=true;++generation;cancelFlow();access=refresh="";worker.shutdownNow();}
}
