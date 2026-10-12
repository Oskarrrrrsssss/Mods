package dev.invumusicplayer.core;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Optional yt-dlp/ffmpeg audio adapter; neither cookies nor Yandex credentials are passed. */
public final class YouTubeAudio {
    private YouTubeAudio() {}
    public static String validate(String value) throws IOException {
        try {
            URI u=URI.create(value.strip()); String h=u.getHost();
            if (!"https".equals(u.getScheme()) || h==null || u.getUserInfo()!=null || (u.getPort()!=-1&&u.getPort()!=443)
                || !(h.equals("youtu.be")||h.equals("youtube.com")||h.endsWith(".youtube.com"))) throw new IllegalArgumentException();
            return u.toASCIIString();
        } catch(RuntimeException e) { throw Messages.error("invumusicplayer.error.youtube_link"); }
    }
    private static String executable(Path gameDir) {
        Path local=gameDir.resolve("tools/yt-dlp.exe"); return Files.isRegularFile(local)?local.toAbsolutePath().toString():"yt-dlp";
    }
    public static Track describe(String url,Path gameDir) throws Exception {
        url=validate(url); Path dir=Files.createTempDirectory("invu-youtube-");
        try {
            Path json=run(gameDir,dir,List.of("--ignore-config","--no-playlist","--skip-download","--dump-single-json","--",url));
            JsonObject info=JsonParser.parseString(Files.readString(json)).getAsJsonObject();
            String title=info.has("title")?info.get("title").getAsString():"YouTube";
            long duration=info.has("duration")&&!info.get("duration").isJsonNull()?info.get("duration").getAsLong()*1000:0;
            return new Track("youtube:"+url,title,"YouTube",duration,null,null,"","");
        } finally { clean(dir); }
    }
    public static byte[] audio(Track track,Path gameDir) throws Exception {
        String url=validate(track.id().substring("youtube:".length())); Path dir=Files.createTempDirectory("invu-youtube-");
        try {
            List<String> args=new ArrayList<>(List.of("--ignore-config","--no-playlist","--no-progress","--max-filesize","128M","-f","bestaudio","-x","--audio-format","wav","-o",dir.resolve("audio.%(ext)s").toString()));
            Path ffmpeg=gameDir.resolve("tools/ffmpeg.exe"); if(Files.isRegularFile(ffmpeg)){args.add("--ffmpeg-location");args.add(ffmpeg.getParent().toAbsolutePath().toString());}
            args.add("--");args.add(url);run(gameDir,dir,args);
            try(InputStream in=Files.newInputStream(dir.resolve("audio.wav"))) { return YandexMusic.bounded(in,128*1024*1024); }
        } finally { clean(dir); }
    }
    private static Path run(Path gameDir,Path dir,List<String> args) throws Exception {
        List<String> command=new ArrayList<>();command.add(executable(gameDir));command.addAll(args);Path log=dir.resolve("process.txt");Process process;
        try { process=new ProcessBuilder(command).redirectOutput(log.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
        catch(IOException e) { throw Messages.error("invumusicplayer.error.youtube_tools"); }
        try {
            if(!process.waitFor(180,TimeUnit.SECONDS)) throw Messages.error("invumusicplayer.error.youtube_failed");
            if(process.exitValue()!=0||Files.size(log)>4_000_000) throw Messages.error("invumusicplayer.error.youtube_failed");return log;
        } finally { process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly(); }
    }
    private static void clean(Path dir) { try(var paths=Files.walk(dir)) { paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}}); } catch(IOException ignored){} }
}
