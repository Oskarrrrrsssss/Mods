package dev.invumusicplayer.core;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Portable helpers stay inside this Minecraft instance; no shell, cookies or account credentials. */
public final class YouTubeTools {
    private static final int LIMIT = 256 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "invu-tools-timeout"); t.setDaemon(true); return t;
    });
    private record Release(String url, String sha) {}
    private YouTubeTools() {}
    public static synchronized Path prepare(Path game, Consumer<Messages.Text> progress) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        Path supplied = game.resolve("tools");
        String suffix = windows ? ".exe" : "";
        if (complete(supplied, suffix)) return supplied.toAbsolutePath();
        if (!windows) return supplied.toAbsolutePath(); // Linux/macOS may use helpers from PATH.
        if (!Set.of("amd64", "x86_64").contains(System.getProperty("os.arch"))) throw Messages.error("invumusicplayer.error.youtube_platform");
        Path target = game.resolve("tools/invumusicplayer");
        if (complete(target, suffix) && Files.isRegularFile(target.resolve("ready-v1"))) return target.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), "invu-tools-");
        try {
            progress.accept(Messages.text("invumusicplayer.youtube.tools", "yt-dlp", 1, 3));
            download(new Release("https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp.exe",
                "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a"), staging.resolve("yt-dlp.exe"));
            progress.accept(Messages.text("invumusicplayer.youtube.tools", "Deno", 2, 3));
            Path deno = staging.resolve("deno.zip");
            download(new Release("https://github.com/denoland/deno/releases/download/v2.9.7/deno-x86_64-pc-windows-msvc.zip",
                "a0c3101b4158d1dfb7d6a78a7bf0f3de80c96bb423c152beec8beb22786f2238"), deno);
            extract(deno, staging, Set.of("deno.exe")); Files.delete(deno);
            progress.accept(Messages.text("invumusicplayer.youtube.tools", "FFmpeg", 3, 3));
            Path ffmpeg = staging.resolve("ffmpeg.zip");
            // BtbN's latest tag rotates. Verify the asset digest from its official release metadata.
            download(ffmpegRelease(), ffmpeg);
            extract(ffmpeg, staging, Set.of("ffmpeg.exe", "ffprobe.exe")); Files.delete(ffmpeg);
            Files.writeString(staging.resolve("NOTICE.txt"), "YouTube helpers / Инструменты YouTube\n"
                + "yt-dlp: https://github.com/yt-dlp/yt-dlp (Unlicense; bundled executable components have their own licenses)\n"
                + "Deno: https://github.com/denoland/deno (MIT)\n"
                + "FFmpeg LGPL build: https://github.com/BtbN/FFmpeg-Builds (sources and build scripts available there)\n"
                + "No Yandex token or browser cookies are passed / Токен Яндекса и cookies браузера не передаются.\n");
            if (!complete(staging, suffix)) throw Messages.error("invumusicplayer.error.youtube_tools");
            Files.createDirectories(target);
            try (var files = Files.list(staging)) {
                for (Path file : files.toList()) Files.move(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(target.resolve("ready-v1"), "2026.08.19 / Deno 2.9.7 / FFmpeg LGPL");
            return target.toAbsolutePath();
        } finally { clean(staging); }
    }
    private static boolean complete(Path dir, String suffix) {
        return List.of("yt-dlp", "deno", "ffmpeg", "ffprobe").stream().allMatch(n -> {
            try { return Files.isRegularFile(dir.resolve(n + suffix)) && Files.size(dir.resolve(n + suffix)) > 0; }
            catch (IOException e) { return false; }
        });
    }
    private static Release ffmpegRelease() throws Exception {
        try (InputStream in = get(URI.create("https://api.github.com/repos/BtbN/FFmpeg-Builds/releases/latest"))) {
            JsonObject release = JsonParser.parseString(new String(YandexMusic.bounded(in, 2_000_000), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : release.getAsJsonArray("assets")) {
                JsonObject asset = e.getAsJsonObject();
                if ("ffmpeg-master-latest-win64-lgpl.zip".equals(asset.get("name").getAsString())) {
                    String sha = asset.get("digest").getAsString();
                    if (!sha.matches("sha256:[a-f0-9]{64}")) break;
                    String url = asset.get("browser_download_url").getAsString();
                    if (!url.startsWith("https://github.com/BtbN/FFmpeg-Builds/releases/download/")) break;
                    return new Release(url, sha.substring(7));
                }
            }
        } catch (RuntimeException e) { throw Messages.error("invumusicplayer.error.youtube_install"); }
        throw Messages.error("invumusicplayer.error.youtube_install");
    }
    private static void download(Release release, Path destination) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256"); long total = 0;
        try (InputStream in = get(URI.create(release.url)); OutputStream out = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) >= 0) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                total += n; if (total > LIMIT) throw Messages.error("invumusicplayer.error.resource_too_large");
                sha.update(buffer, 0, n); out.write(buffer, 0, n);
            }
        }
        if (!HexFormat.of().formatHex(sha.digest()).equals(release.sha)) throw Messages.error("invumusicplayer.error.youtube_checksum");
    }
    private static InputStream get(URI uri) throws Exception {
        for (int redirect = 0; redirect <= 5; redirect++) {
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)
                || host==null || !(host.equals("github.com") || host.equals("api.github.com") || host.equals("release-assets.githubusercontent.com")
                    || host.equals("objects.githubusercontent.com"))) throw Messages.error("invumusicplayer.error.youtube_install");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5))
                .header("User-Agent", "InvuMusicPlayer/0.2.1").GET().build();
            CompletableFuture<HttpResponse<InputStream>> pending = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            HttpResponse<InputStream> response;
            try { response = pending.get(5, TimeUnit.MINUTES); }
            catch (InterruptedException e) { pending.cancel(true); throw e; }
            catch (ExecutionException | TimeoutException e) { pending.cancel(true); throw Messages.error("invumusicplayer.error.youtube_install"); }
            if (Set.of(301,302,303,307,308).contains(response.statusCode())) {
                response.body().close(); String location = response.headers().firstValue("Location").orElseThrow(() -> Messages.error("invumusicplayer.error.youtube_install"));
                uri = uri.resolve(location); continue;
            }
            if (response.statusCode()!=200) { response.body().close(); throw Messages.error("invumusicplayer.error.youtube_install_http", response.statusCode()); }
            return new TimedStream(response.body());
        }
        throw Messages.error("invumusicplayer.error.youtube_install");
    }
    private static final class TimedStream extends FilterInputStream {
        private final ScheduledFuture<?> watchdog;
        TimedStream(InputStream source) {
            super(source); Thread owner = Thread.currentThread(); long deadline = System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
            watchdog = WATCHDOG.scheduleAtFixedRate(() -> {
                if(owner.isInterrupted() || System.nanoTime()>deadline) try { source.close(); } catch(IOException ignored) {}
            }, 200, 200, TimeUnit.MILLISECONDS);
        }
        @Override public void close() throws IOException { watchdog.cancel(false); super.close(); }
    }
    static void extract(Path zip, Path destination, Set<String> expected) throws Exception {
        Set<String> found = new HashSet<>(); long total = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1);
                if (!expected.contains(name) || entry.isDirectory()) continue;
                if (!found.add(name)) throw Messages.error("invumusicplayer.error.youtube_install");
                try (OutputStream out = Files.newOutputStream(destination.resolve(name))) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = in.read(buffer)) >= 0) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        total += n; if (total > 512L*1024*1024) throw Messages.error("invumusicplayer.error.resource_too_large");
                        out.write(buffer,0,n);
                    }
                }
            }
        }
        if (!found.equals(expected)) throw Messages.error("invumusicplayer.error.youtube_install");
    }
    static void clean(Path directory) {
        try (var files = Files.walk(directory)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        catch (IOException ignored) {}
    }
    /** CI checks the real Windows binaries, without downloading any video. */
    public static void main(String[] args) throws Exception {
        Path path = prepare(Path.of(args[0]), text -> System.out.println(text.getString()));
        for (String binary : List.of("yt-dlp", "deno", "ffmpeg", "ffprobe")) {
            Process p = new ProcessBuilder(path.resolve(binary + ".exe").toString(), binary.startsWith("ff") ? "-version" : "--version")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(30,TimeUnit.SECONDS) || p.exitValue()!=0) { p.destroyForcibly(); throw new IOException("Helper did not start: " + binary); }
            System.out.println("Verified helper / Инструмент проверен: " + binary);
        }
    }
}
