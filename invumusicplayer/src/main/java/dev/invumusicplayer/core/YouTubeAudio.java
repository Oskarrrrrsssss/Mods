package dev.invumusicplayer.core;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Audio only, with a portable JS runtime and conversion to bundled JavaSound-compatible MP3. */
public final class YouTubeAudio {
    private YouTubeAudio() {}
    public static String validate(String value) throws IOException {
        try {
            URI u=URI.create(value.strip()); String h=u.getHost();
            if (!"https".equals(u.getScheme()) || h==null || u.getUserInfo()!=null || (u.getPort()!=-1&&u.getPort()!=443)
                || !(h.equals("youtu.be")||h.equals("youtube.com")||h.endsWith(".youtube.com"))) throw new IllegalArgumentException();
            String path=u.getPath();
            if (!(h.equals("youtu.be") && path.matches("/[a-zA-Z0-9_-]{11}"))
                && !(path.equals("/watch") && u.getQuery()!=null && Arrays.stream(u.getQuery().split("&")).anyMatch(q->q.matches("v=[a-zA-Z0-9_-]{11}")))
                && !path.matches("/(shorts|live|embed)/[a-zA-Z0-9_-]{11}")) throw new IllegalArgumentException();
            return u.toASCIIString();
        } catch(RuntimeException e) { throw Messages.error("invumusicplayer.error.youtube_link"); }
    }
    static List<String> baseArguments(Path tools) {
        List<String> args = new ArrayList<>(List.of("--ignore-config","--no-playlist","--no-progress","--no-colors","--socket-timeout","20","--retries","2"));
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        Path deno=tools.resolve("deno"+(windows?".exe":""));
        if(Files.isRegularFile(deno)){args.add("--js-runtimes");args.add("deno:"+deno.toAbsolutePath());}
        Path ffmpeg=tools.resolve("ffmpeg"+(windows?".exe":""));
        if(Files.isRegularFile(ffmpeg)){args.add("--ffmpeg-location");args.add(tools.toAbsolutePath().toString());}
        return args;
    }
    public static Track describe(String url,Path gameDir) throws Exception {
        url=validate(url); Path tools=YouTubeTools.prepare(gameDir,t->{});Path dir=Files.createTempDirectory("invu-youtube-");
        try {
            List<String> args=baseArguments(tools);args.addAll(List.of("--skip-download","--dump-single-json","--",url));
            Path json=run(tools,dir,args,180);
            JsonObject info=JsonParser.parseString(Files.readString(json)).getAsJsonObject();
            if(info.has("is_live")&&info.get("is_live").getAsBoolean())throw Messages.error("invumusicplayer.error.youtube_live");
            String title=info.has("title")?info.get("title").getAsString():"YouTube";
            long duration=info.has("duration")&&!info.get("duration").isJsonNull()?Math.round(info.get("duration").getAsDouble()*1000):0;
            return new Track("youtube:"+url,title,"YouTube",duration,null,null,"","");
        } finally { YouTubeTools.clean(dir); }
    }
    public static byte[] audio(Track track,Path gameDir) throws Exception {
        String url=validate(track.id().substring("youtube:".length()));Path tools=YouTubeTools.prepare(gameDir,t->{});Path dir=Files.createTempDirectory("invu-youtube-");
        try {
            List<String> args=baseArguments(tools);
            args.addAll(List.of("--max-filesize","128M","-f","bestaudio/best","-x","--audio-format","mp3","--audio-quality","192K","-o",dir.resolve("audio.%(ext)s").toString(),"--",url));
            run(tools,dir,args,600);
            try(InputStream in=Files.newInputStream(dir.resolve("audio.mp3"))) { return YandexMusic.bounded(in,128*1024*1024); }
        } finally { YouTubeTools.clean(dir); }
    }
    static String errorKey(String diagnostic) {
        String s=diagnostic.toLowerCase(Locale.ROOT);
        if(s.contains("ffmpeg")||s.contains("ffprobe"))return "invumusicplayer.error.youtube_ffmpeg";
        if(s.contains("javascript")||s.contains("js runtime")||s.contains("ejs"))return "invumusicplayer.error.youtube_js";
        if(s.contains("sign in")||s.contains("sign-in")||s.contains("private video")||s.contains("age-restricted"))return "invumusicplayer.error.youtube_login";
        if(s.contains("429")||s.contains("too many requests"))return "invumusicplayer.error.http_rate_limit";
        if(s.contains("not available")||s.contains("unavailable")||s.contains("removed"))return "invumusicplayer.error.youtube_unavailable";
        if(s.contains("403")||s.contains("forbidden"))return "invumusicplayer.error.youtube_forbidden";
        if(s.contains("timed out")||s.contains("unable to download")||s.contains("network"))return "invumusicplayer.error.network";
        return "invumusicplayer.error.youtube_failed";
    }
    private static Path run(Path tools,Path dir,List<String> args,int timeout) throws Exception {
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        Path executable=tools.resolve("yt-dlp"+(windows?".exe":""));
        List<String> command=new ArrayList<>();command.add(Files.isRegularFile(executable)?executable.toAbsolutePath().toString():"yt-dlp");command.addAll(args);
        Path log=dir.resolve("process.txt"),error=dir.resolve("error.txt");Process process;
        try { process=new ProcessBuilder(command).redirectOutput(log.toFile()).redirectError(error.toFile()).start(); }
        catch(IOException e) { throw Messages.error("invumusicplayer.error.youtube_tools"); }
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeout);
            while(!process.waitFor(250,TimeUnit.MILLISECONDS)) {
                if(System.nanoTime()>deadline)throw Messages.error("invumusicplayer.error.timeout");
                if(Files.size(log)>4_000_000||Files.size(error)>1_000_000)throw Messages.error("invumusicplayer.error.youtube_failed");
            }
            if(Files.size(log)>4_000_000||Files.size(error)>1_000_000)throw Messages.error("invumusicplayer.error.youtube_failed");
            if(process.exitValue()!=0){String key=errorKey(Files.readString(error,StandardCharsets.UTF_8));System.getLogger("invumusicplayer").log(System.Logger.Level.WARNING,"YouTube: "+key);throw Messages.error(key);}
            return log;
        } finally { process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly(); }
    }
}
