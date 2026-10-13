package dev.invumusicplayer.core;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

final class SpotifyTest{
    @TempDir Path temp;
    private static final String ID="0123456789abcdefghijkl";
    private static final String CLIENT="0123456789abcdef0123456789abcdef";
    private static String track(String id){return "{\"id\":\""+id+"\",\"name\":\"Song\",\"type\":\"track\",\"artists\":[{\"name\":\"Artist\"}],\"duration_ms\":123456,\"album\":{\"images\":[{\"url\":\"https://i.scdn.co/image/synthetic\"}]}}";}
    private TokenStore store(){return new TokenStore(temp.resolve("auth.dat"),new TokenStore.Protection(){public byte[] protect(byte[] data){return data.clone();}public byte[] unprotect(byte[] data){return data.clone();}});}
    private SpotifyMusic api(SpotifyMusic.Transport transport){SpotifyMusic api=new SpotifyMusic(new Settings(),store(),transport);api.testAccess("synthetic-spotify-access");return api;}
    @Test void pkceMatchesThePublishedS256TestVector(){assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",SpotifyAuthorization.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));}
    @Test void callbackStateCannotBeDuplicatedOrInjected(){assertThrows(IOException.class,()->SpotifyAuthorization.parseQuery("state=a&state=b&code=c"));assertThrows(IOException.class,()->SpotifyAuthorization.parseQuery("state=%zz"));}
    @Test void loopbackCallbackRejectsWrongStateThenExchangesTheValidCode()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("invu.spotify.oauth.check"),"Loopback socket test runs in CI / Локальный сервер проверяется в CI");
        HttpServer tokenServer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),1);AtomicReference<Map<String,String>> form=new AtomicReference<>();
        tokenServer.createContext("/token",exchange->{form.set(SpotifyAuthorization.parseQuery(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));byte[] body="{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});tokenServer.start();
        CountDownLatch done=new CountDownLatch(1);AtomicReference<SpotifyAuthorization.Tokens> tokens=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();
        try(SpotifyAuthorization auth=new SpotifyAuthorization(CLIENT,0,"/callback",List.of("user-library-read"),value->{tokens.set(value);done.countDown();},error::set,URI.create("http://127.0.0.1:"+tokenServer.getAddress().getPort()+"/token"))){
            var fields=SpotifyAuthorization.parseQuery(auth.uri().getRawQuery());assertEquals("S256",fields.get("code_challenge_method"));assertEquals(CLIENT,fields.get("client_id"));assertFalse(fields.containsKey("client_secret"));
            HttpClient client=HttpClient.newHttpClient();assertEquals(400,client.send(HttpRequest.newBuilder(URI.create(auth.redirect()+"?state=wrong&code=synthetic-code")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());assertNull(tokens.get());assertNull(error.get());
            assertEquals(200,client.send(HttpRequest.newBuilder(URI.create(auth.redirect()+"?state="+fields.get("state")+"&code=synthetic-code")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());assertTrue(done.await(4,TimeUnit.SECONDS));assertNull(error.get());assertEquals("synthetic-refresh",tokens.get().refresh());assertEquals(fields.get("code_challenge"),SpotifyAuthorization.challenge(form.get().get("code_verifier")));assertEquals(auth.redirect(),form.get().get("redirect_uri"));
        }finally{tokenServer.stop(0);}
    }
    @Test void searchRespectsTheNewLimitAndParsesMetadataWithoutRemovedFields()throws Exception{
        try(SpotifyMusic api=api((method,path,bearer,body)->{assertEquals("GET",method);assertTrue(path.contains("limit=10"));assertTrue(path.contains("q=hello+world"));assertEquals("synthetic-spotify-access",bearer);return new SpotifyMusic.Response(200,"{\"tracks\":{\"items\":["+track(ID)+"],\"next\":null}}","");})){
            Track result=api.search("hello world").getFirst();assertTrue(result.spotify());assertEquals("spotify:track:"+ID,result.id());assertEquals("Song",result.title());assertEquals("Artist",result.artist());assertEquals(123456,result.durationMillis());assertTrue(result.coverUrl().startsWith("https://i.scdn.co/"));
        }
    }
    @Test void likedPaginationReadsEveryPageAndSkipsUnavailableAndNullTracks()throws Exception{
        AtomicInteger calls=new AtomicInteger();try(SpotifyMusic api=api((m,p,t,b)->new SpotifyMusic.Response(200,calls.getAndIncrement()==0?"{\"items\":[{\"track\":"+track(ID)+"},{\"track\":null}],\"next\":\"https://api.spotify.com/v1/me/tracks?limit=50&offset=50\"}":"{\"items\":[{\"item\":"+track("1123456789abcdefghijkl")+"}],\"next\":null}",""))){assertEquals(2,api.liked().size());assertEquals(2,calls.get());}
    }
    @Test void paginationCannotSendTheBearerToAnExternalOrigin()throws Exception{
        AtomicInteger calls=new AtomicInteger();try(SpotifyMusic api=api((m,p,t,b)->{calls.incrementAndGet();return new SpotifyMusic.Response(200,"{\"items\":[],\"next\":\"https://evil.test/v1/me/tracks\"}","");})){assertThrows(IOException.class,api::liked);assertEquals(1,calls.get());}
    }
    @Test void playlistUsesItemsEndpointAndReadsBothCurrentAndLegacyTrackWrappers()throws Exception{
        try(SpotifyMusic api=api((m,p,t,b)->{assertTrue(p.startsWith("/playlists/"+ID+"/items?"));return new SpotifyMusic.Response(200,"{\"items\":[{\"item\":"+track(ID)+"},{\"track\":"+track("1123456789abcdefghijkl")+"}],\"next\":null}","");})){assertEquals(2,api.playlist(ID).size());}
    }
    @Test void likeAndUnlikeUseGenericLibraryUrisWithoutDeprecatedTrackWrites()throws Exception{
        List<String> calls=new ArrayList<>();try(SpotifyMusic api=api((m,p,t,b)->{calls.add(m+" "+p);return new SpotifyMusic.Response(200,"","");})){Track track=SpotifyMusic.track(JsonParser.parseString(track(ID)).getAsJsonObject());api.like(track,false);api.like(track,true);assertEquals(List.of("PUT /me/library?uris=spotify%3Atrack%3A"+ID,"DELETE /me/library?uris=spotify%3Atrack%3A"+ID),calls);}
    }
    @Test void rateLimitPreventsImmediateAutomaticRetryAndHidesResponseData()throws Exception{
        AtomicInteger calls=new AtomicInteger();try(SpotifyMusic api=api((m,p,t,b)->{calls.incrementAndGet();return new SpotifyMusic.Response(429,"private synthetic token", "120");})){IOException error=assertThrows(IOException.class,()->api.search("test"));assertFalse(error.toString().contains("private"));assertThrows(IOException.class,()->api.search("test"));assertEquals(1,calls.get());}
    }
    @Test void forbiddenErrorsNeverExposeBodyOrCredential()throws Exception{
        try(SpotifyMusic api=api((m,p,t,b)->new SpotifyMusic.Response(403,"synthetic-secret-body", ""))){IOException error=assertThrows(IOException.class,api::liked);assertEquals(Messages.get("invumusicplayer.spotify.forbidden"),error.getMessage());assertFalse(error.toString().contains("synthetic-secret"));assertNull(error.getCause());}
    }
    @Test void missingPlaybackAuthorizationRetainsSelectionAndNeverUsesYandexAudio()throws Exception{
        AtomicInteger audioCalls=new AtomicInteger();Player.RemoteProvider remote=new Player.RemoteProvider(){public void checkAccount(String s){}public List<Track> search(String s){return List.of();}public List<Track> wave(String s){return List.of();}public void feedback(String s,Track t,long m){}public byte[] audio(Track t){audioCalls.incrementAndGet();return new byte[0];}public byte[] cover(Track t){return new byte[0];}};
        try(Player p=new Player(temp,remote,new AudioEngine())){Track first=new Track("spotify:track:"+ID,"First","Artist",1000,null,null,"","");Track second=new Track("spotify:track:1123456789abcdefghijkl","Second","Artist",1000,null,null,"","");p.choose(List.of(first,second),0);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(p.loading()&&System.nanoTime()<deadline)Thread.sleep(5);assertFalse(p.loading());assertEquals(first,p.current());assertEquals(List.of(second),p.queue());assertFalse(p.playing());assertEquals(0,audioCalls.get());assertFalse(p.error().isBlank());}
    }
    private TokenStore savedSpotifyStore()throws Exception{var store=store();store.save(Base64.getEncoder().encodeToString(("{\"client_id\":\""+CLIENT+"\",\"refresh\":\"synthetic-refresh-old\"}").getBytes(StandardCharsets.UTF_8)));return store;}
    @Test void savedLoginRefreshesRotatingCredentialsAndRetries401OnlyOnce()throws Exception{
        TokenStore saved=savedSpotifyStore();Settings settings=new Settings();settings.spotifyClientId=CLIENT;AtomicInteger exchanges=new AtomicInteger(),requests=new AtomicInteger();
        try(SpotifyMusic api=new SpotifyMusic(settings,saved,(m,p,t,b)->{int n=requests.incrementAndGet();if(p.equals("/me"))return new SpotifyMusic.Response(200,"{\"id\":\"synthetic-user\",\"product\":\"premium\"}","");if(n==2){assertEquals("access-1",t);return new SpotifyMusic.Response(401,"","");}assertEquals("access-2",t);return new SpotifyMusic.Response(200,"{\"items\":[],\"next\":null}","");},values->{int n=exchanges.incrementAndGet();assertEquals(CLIENT,values.get("client_id"));assertEquals("refresh_token",values.get("grant_type"));assertEquals(n==1?"synthetic-refresh-old":"refresh-1",values.get("refresh_token"));return new SpotifyAuthorization.Tokens("access-"+n,"refresh-"+n,3600);})){
            api.restore();assertTrue(api.connected());assertEquals("synthetic-user",api.accountId());assertTrue(api.liked().isEmpty());assertEquals(2,exchanges.get());assertEquals(3,requests.get());assertTrue(new String(Base64.getDecoder().decode(saved.load()),StandardCharsets.UTF_8).contains("refresh-2"));api.disconnect();assertFalse(api.connected());assertEquals("",saved.load());
        }
    }
    @Test void disconnectDoesNotWaitForRefreshAndLateResponseCannotRestoreCredentials()throws Exception{
        TokenStore saved=savedSpotifyStore();Settings settings=new Settings();settings.spotifyClientId=CLIENT;CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger profiles=new AtomicInteger();
        try(SpotifyMusic api=new SpotifyMusic(settings,saved,(m,p,t,b)->{profiles.incrementAndGet();return new SpotifyMusic.Response(200,"{\"id\":\"synthetic-user\"}","");},values->{started.countDown();assertTrue(release.await(4,TimeUnit.SECONDS));return new SpotifyAuthorization.Tokens("late-access","late-refresh",3600);})){
            Thread restoring=new Thread(api::restore);restoring.start();assertTrue(started.await(4,TimeUnit.SECONDS));api.disconnect();assertFalse(api.connected());assertEquals("",saved.load());release.countDown();restoring.join(4000);assertFalse(restoring.isAlive());assertFalse(api.connected());assertEquals("",saved.load());assertEquals(0,profiles.get());
        }finally{release.countDown();}
    }
    @Test void aSavedLoginCannotBeReusedWithAnotherDeveloperClient()throws Exception{
        TokenStore saved=savedSpotifyStore();Settings settings=new Settings();settings.spotifyClientId="1123456789abcdef0123456789abcdef";AtomicInteger exchanges=new AtomicInteger();try(SpotifyMusic api=new SpotifyMusic(settings,saved,(m,p,t,b)->{fail("Different Client ID must not send a bearer");return null;},values->{exchanges.incrementAndGet();return null;})){api.restore();assertFalse(api.connected());assertEquals(0,exchanges.get());}
    }
    @Test void serviceSettingsPersistAndBadValuesFallBackToYandex()throws Exception{Settings s=new Settings();s.musicService="spotify";s.spotifyClientId=CLIENT;s.save(temp.resolve("settings.json"));Settings loaded=Settings.load(temp.resolve("settings.json"));assertEquals("spotify",loaded.musicService);assertEquals(CLIENT,loaded.spotifyClientId);loaded.musicService="unknown";loaded.spotifyClientId="bad\r\nvalue";loaded.normalize();assertEquals("yandex",loaded.musicService);assertEquals("",loaded.spotifyClientId);}
    @Test void spotifyArtworkRejectsForeignOriginsAndInjectedCredentials()throws Exception{SpotifyMusic.validateCover(URI.create("https://i.scdn.co/image/example"));for(String url:List.of("https://scdn.co.evil.test/image/a","http://i.scdn.co/a","https://user@i.scdn.co/a","https://i.scdn.co:444/a"))assertThrows(IOException.class,()->SpotifyMusic.validateCover(URI.create(url)));}
    @Test void nativePipeBoundsMalformedFramesAndCredentials()throws Exception{assertEquals("{}",NativeSpotifyPlayback.readLine(new ByteArrayInputStream("{}\n".getBytes()),10));assertThrows(IOException.class,()->NativeSpotifyPlayback.readLine(new ByteArrayInputStream(new byte[12]),10));assertThrows(IOException.class,()->NativeSpotifyPlayback.validateCredentials(JsonParser.parseString("{\"username\":\"test\",\"auth_type\":0,\"auth_data\":\"AA==\"}").getAsJsonObject()));}
}
