package dev.invumusicplayer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class LocalArtworkTest{
    @TempDir Path temp;
    private byte[] image()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    private static byte[] sync(int n){return new byte[]{(byte)(n>>21&127),(byte)(n>>14&127),(byte)(n>>7&127),(byte)(n&127)};}
    private Path id3(int version,byte[] image,boolean unicode)throws Exception{
        var picture=new ByteArrayOutputStream();picture.write(unicode?1:0);if(version==2)picture.write("PNG".getBytes());else picture.write("image/png\0".getBytes());picture.write(3);
        picture.write(unicode?new byte[]{(byte)255,(byte)254,'A',0,0,0}:new byte[]{0});picture.write(image);
        byte[] payload=picture.toByteArray();var frame=new ByteArrayOutputStream();frame.write((version==2?"PIC":"APIC").getBytes());
        if(version==2)frame.write(new byte[]{(byte)(payload.length>>16),(byte)(payload.length>>8),(byte)payload.length});else{frame.write(version==4?sync(payload.length):ByteBuffer.allocate(4).putInt(payload.length).array());frame.write(new byte[2]);}frame.write(payload);
        var out=new ByteArrayOutputStream();out.write(new byte[]{'I','D','3',(byte)version,0,0});out.write(sync(frame.size()));out.write(frame.toByteArray());Path file=temp.resolve("v"+version+".mp3");Files.write(file,out.toByteArray());return file;
    }
    @Test void embeddedMp3CoversSupportId3VersionsAndUnicodeDescription()throws Exception{
        byte[] png=image();for(int version:new int[]{2,3,4})assertArrayEquals(png,LocalArtwork.read(id3(version,png,version!=2)));
    }
    @Test void boundedMalformedTagsNeverHideTrackOrAllocateDeclaredGigabytes()throws Exception{
        Path file=temp.resolve("broken.mp3");Files.write(file,new byte[]{'I','D','3',4,0,0,127,127,127,127});assertEquals(0,LocalArtwork.read(file).length);
        Files.write(file,new byte[]{'I','D','3',4,0,0,0,0,0,80});assertEquals(0,LocalArtwork.read(file).length);assertEquals(0,LocalArtwork.read(id3(4,new byte[64],false)).length);
    }
    private Path ogg(String key,byte[] picture)throws Exception{
        String comment=key+"="+Base64.getEncoder().encodeToString(picture);byte[] text=comment.getBytes(StandardCharsets.UTF_8);
        var packet=new ByteArrayOutputStream();packet.write(new byte[]{3,'v','o','r','b','i','s'});packet.write(new byte[4]);packet.write(new byte[]{1,0,0,0});packet.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(text.length).array());packet.write(text);packet.write(1);
        byte[] body=packet.toByteArray();var out=new ByteArrayOutputStream();byte[] header=new byte[27];header[0]='O';header[1]='g';header[2]='g';header[3]='S';header[26]=(byte)(body.length/255+1);out.write(header);for(int i=0;i<body.length/255;i++)out.write(255);out.write(body.length%255);out.write(body);Path path=temp.resolve("cover.ogg");Files.write(path,out.toByteArray());return path;
    }
    @Test void vorbisPictureBlockAndLegacyCoverartAreRead()throws Exception{
        byte[] png=image();var out=new ByteArrayOutputStream();var data=new DataOutputStream(out);data.writeInt(3);data.writeInt(9);data.write("image/png".getBytes());data.writeInt(0);for(int n:new int[]{2,2,24,0})data.writeInt(n);data.writeInt(png.length);data.write(png);
        assertArrayEquals(png,LocalArtwork.read(ogg("METADATA_BLOCK_PICTURE",out.toByteArray())));assertArrayEquals(png,LocalArtwork.read(ogg("COVERART",png)));assertEquals(0,LocalArtwork.read(ogg("METADATA_BLOCK_PICTURE",new byte[8])).length);
    }
    @Test void adjacentArtworkOverridesSharedAlbumCoverAndExternalSymlinksAreIgnored()throws Exception{
        Path music=temp.resolve("music");Files.createDirectories(music);Files.write(music.resolve("Song.mp3"),new byte[0]);Files.write(music.resolve("cover.jpg"),image());
        var lib=new LocalLibrary(music);assertEquals(music.resolve("cover.jpg"),lib.scan().getFirst().coverFile());Files.write(music.resolve("Song.webp"),image());assertEquals(music.resolve("Song.webp"),lib.scan().getFirst().coverFile());
        Files.delete(music.resolve("Song.webp"));Path outside=temp.resolve("outside.png");Files.write(outside,image());try{Files.createSymbolicLink(music.resolve("Song.png"),outside);}catch(IOException|UnsupportedOperationException e){return;}assertEquals(music.resolve("cover.jpg"),lib.scan().getFirst().coverFile());
    }
}
