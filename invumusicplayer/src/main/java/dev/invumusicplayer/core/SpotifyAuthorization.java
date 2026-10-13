package dev.invumusicplayer.core;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** PKCE on an IPv4 loopback listener; no password, client secret or credential in logs. */
public final class SpotifyAuthorization implements AutoCloseable {
    record Tokens(String access,String refresh,int expires) {}
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final HttpServer server;
    private final String verifier=random(),state=random(),clientId,redirect;
    private final URI tokenEndpoint;
    private final Consumer<Tokens> success;
    private final Consumer<Exception> failure;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"invumusicplayer-spotify-oauth");t.setDaemon(true);return t;});
    private boolean completed;
    public SpotifyAuthorization(String clientId,int port,String path,List<String> scopes,Consumer<Tokens> success,Consumer<Exception> failure)throws IOException {
        this(clientId,port,path,scopes,success,failure,URI.create("https://accounts.spotify.com/api/token"));
    }
    SpotifyAuthorization(String clientId,int port,String path,List<String> scopes,Consumer<Tokens> success,Consumer<Exception> failure,URI tokenEndpoint)throws IOException{
        this.clientId=clientId;this.success=success;this.failure=failure;this.tokenEndpoint=tokenEndpoint;
        server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),2);
        redirect="http://127.0.0.1:"+server.getAddress().getPort()+path;
        server.createContext(path,exchange->{
            String reply=Messages.get("invumusicplayer.spotify.browser_done");int code=200;
            try{
                if(!exchange.getRequestMethod().equals("GET")||!exchange.getRequestURI().getPath().equals(path))throw Messages.error("invumusicplayer.spotify.oauth_invalid");
                Map<String,String> query=parseQuery(exchange.getRequestURI().getRawQuery());
                if(!matchesState(query.get("state")))throw Messages.error("invumusicplayer.spotify.oauth_invalid");
                synchronized(this){if(completed)throw Messages.error("invumusicplayer.spotify.oauth_invalid");completed=true;}
                String authCode=query.get("code");
                if(query.containsKey("error")||authCode==null||authCode.length()>4096)throw Messages.error("invumusicplayer.spotify.oauth_denied");
                worker.execute(()->{try{Tokens tokens=tokenRequest(tokenEndpoint,Map.of("grant_type","authorization_code","code",authCode,"redirect_uri",redirect,"client_id",clientId,"code_verifier",verifier));success.accept(tokens);}catch(Exception e){failure.accept(e);}finally{close();}});
            }catch(Exception e){code=400;reply=Messages.get("invumusicplayer.spotify.oauth_invalid");if(matchesState(parseState(exchange.getRequestURI().getRawQuery()))) {failure.accept(e);worker.execute(this::close);}}
            byte[] bytes=reply.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(code,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"invumusicplayer-spotify-callback");t.setDaemon(true);return t;}));
        server.start();worker.schedule(()->{synchronized(this){if(completed)return;completed=true;}failure.accept(Messages.error("invumusicplayer.spotify.oauth_timeout"));close();},5,TimeUnit.MINUTES);
        this.scopes=List.copyOf(scopes);
    }
    private final List<String> scopes;
    public URI uri(){return URI.create("https://accounts.spotify.com/authorize?"+form(Map.of("client_id",clientId,"response_type","code","redirect_uri",redirect,"scope",String.join(" ",scopes),"state",state,"code_challenge_method","S256","code_challenge",challenge(verifier))));}
    String redirect(){return redirect;}
    private boolean matchesState(String value){return value!=null&&MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),value.getBytes(StandardCharsets.UTF_8));}
    private static String parseState(String query){try{return parseQuery(query).get("state");}catch(Exception e){return null;}}
    static Map<String,String> parseQuery(String query)throws IOException{
        if(query==null||query.length()>10000)throw Messages.error("invumusicplayer.spotify.oauth_invalid");
        Map<String,String> fields=new HashMap<>();try{for(String item:query.split("&")){String[] pair=item.split("=",2);String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8);String value=pair.length<2?"":URLDecoder.decode(pair[1],StandardCharsets.UTF_8);if(fields.putIfAbsent(key,value)!=null)throw new IllegalArgumentException();}return fields;}catch(RuntimeException e){throw Messages.error("invumusicplayer.spotify.oauth_invalid");}
    }
    static Tokens tokenRequest(URI endpoint,Map<String,String> values)throws IOException,InterruptedException{
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(25)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(values))).build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());byte[] bytes;try(var in=response.body()){bytes=YandexMusic.bounded(in,65536);}
        if(response.statusCode()!=200)throw Messages.error("invumusicplayer.spotify.oauth_failed");
        try{JsonObject obj=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();String access=TokenInput.normalize(obj.get("access_token").getAsString());String refresh=obj.has("refresh_token")?TokenInput.normalize(obj.get("refresh_token").getAsString()):"";int expiry=obj.has("expires_in")?obj.get("expires_in").getAsInt():3600;return new Tokens(access,refresh,Math.max(30,Math.min(86400,expiry)));}catch(RuntimeException e){throw Messages.error("invumusicplayer.spotify.oauth_failed");}
    }
    static String form(Map<String,String> fields){return fields.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(java.util.stream.Collectors.joining("&"));}
    static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    static String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static String challenge(String verifier){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException();}}
    @Override public synchronized void close(){completed=true;server.stop(0);worker.shutdownNow();if(server.getExecutor() instanceof ExecutorService pool)pool.shutdownNow();}
}
