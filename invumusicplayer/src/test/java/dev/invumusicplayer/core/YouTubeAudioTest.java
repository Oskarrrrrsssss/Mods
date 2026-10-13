package dev.invumusicplayer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

final class YouTubeAudioTest {
    @TempDir Path temp;
    @Test void acceptsSingleVideosAndRejectsPlaylistsOtherHostsAndCredentialUrls() throws Exception {
        for(String url:List.of("https://youtu.be/abcdefghijk","https://www.youtube.com/watch?v=abcdefghijk&list=test","https://youtube.com/shorts/abcdefghijk"))assertEquals(url,YouTubeAudio.validate(url));
        for(String url:List.of("https://youtube.com/playlist?list=abc","https://youtube.com.attacker.test/watch?v=abcdefghijk","https://user@youtube.com/watch?v=abcdefghijk","http://youtu.be/abcdefghijk","https://youtu.be/short"))assertThrows(IOException.class,()->YouTubeAudio.validate(url));
    }
    @Test void errorsExplainMissingRuntimeConversionAndAccessWithoutPrintingRawDiagnostics() {
        assertEquals("invumusicplayer.error.youtube_ffmpeg",YouTubeAudio.errorKey("ffmpeg not found"));
        assertEquals("invumusicplayer.error.youtube_js",YouTubeAudio.errorKey("No supported JavaScript runtime"));
        assertEquals("invumusicplayer.error.youtube_login",YouTubeAudio.errorKey("Sign in to confirm you are not a bot"));
        assertEquals("invumusicplayer.error.youtube_forbidden",YouTubeAudio.errorKey("HTTP Error 403: Forbidden https://signed-secret-url"));
    }
    @Test void archiveExtractsOnlyNamedHelpersWithoutFollowingArchivePaths() throws Exception {
        Path zip=temp.resolve("tools.zip");
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){
            for(String name:List.of("package/bin/ffmpeg.exe","../ffprobe.exe","../../escape.exe")){
                out.putNextEntry(new ZipEntry(name));out.write(new byte[]{1,2,3});out.closeEntry();}
        }
        YouTubeTools.extract(zip,temp,Set.of("ffmpeg.exe","ffprobe.exe"));
        assertEquals(3,Files.size(temp.resolve("ffmpeg.exe")));assertEquals(3,Files.size(temp.resolve("ffprobe.exe")));assertFalse(Files.exists(temp.resolve("escape.exe")));
    }
    @Test void incompleteArchiveFailsInsteadOfMarkingToolsInstalled() throws Exception {
        Path zip=temp.resolve("tools.zip");try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new ZipEntry("ffmpeg.exe"));out.write(1);out.closeEntry();}
        assertThrows(IOException.class,()->YouTubeTools.extract(zip,temp,Set.of("ffmpeg.exe","ffprobe.exe")));
    }
}
