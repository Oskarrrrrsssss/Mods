package dev.invumusicplayer.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads bounded ID3 APIC/PIC and Vorbis picture comments without caching audio on disk. */
final class LocalArtwork {
    private static final int LIMIT=8_000_000;
    static byte[] read(Path path) throws IOException {
        if(path==null)return new byte[0];
        try(InputStream in=Files.newInputStream(path)){
            byte[] head=in.readNBytes(10);
            if(head.length==10&&head[0]=='I'&&head[1]=='D'&&head[2]=='3')return id3(head,in);
            if(head.length>=4&&head[0]=='O'&&head[1]=='g'&&head[2]=='g'&&head[3]=='S'){
                try(InputStream stream=Files.newInputStream(path)){return ogg(stream);}
            }
        }catch(IllegalArgumentException|IndexOutOfBoundsException ignored){/* A damaged tag does not prevent playback. */}
        return new byte[0];
    }
    private static byte[] id3(byte[] head,InputStream in)throws IOException{
        int version=head[3]&255,size=sync(head,6);if(version<2||version>4||size<0||size>LIMIT)return new byte[0];
        byte[] data=in.readNBytes(size);if(data.length!=size)return new byte[0];
        if((head[5]&128)!=0)data=unsync(data);
        int at=0;
        if((head[5]&64)!=0){if(version==2)return new byte[0];if(data.length<4)return new byte[0];int extended=version==4?sync(data,0):integer(data,0);if(extended<0)return new byte[0];at=version==4?extended:4+extended;}
        byte[] fallback=new byte[0];int header=version==2?6:10;
        while(at>=0&&at+header<=data.length){
            String id=new String(data,at,version==2?3:4,StandardCharsets.US_ASCII);
            int length=version==2?((data[at+3]&255)<<16)|((data[at+4]&255)<<8)|(data[at+5]&255):version==4?sync(data,at+4):integer(data,at+4);
            if(length<=0||length>data.length-at-header)break;
            if(id.equals(version==2?"PIC":"APIC")){
                int flags=version==2?0:data[at+9]&255;
                boolean compressed=version==3?(flags&224)!=0:version==4&&(flags&77)!=0;
                if(!compressed){byte[] frame=Arrays.copyOfRange(data,at+header,at+header+length);if(version==4&&(flags&2)!=0)frame=unsync(frame);Picture p=picture(frame,version==2);if(p.bytes.length>0){if(p.type==3)return p.bytes;if(fallback.length==0)fallback=p.bytes;}}
            }
            at+=header+length;
        }
        return fallback;
    }
    private record Picture(int type,byte[] bytes){}
    private static Picture picture(byte[] data,boolean old){
        if(data.length<5)return new Picture(0,new byte[0]);int encoding=data[0]&255,at=old?4:1;
        if(!old){while(at<data.length&&data[at]!=0)at++;at++;}
        if(at>=data.length)return new Picture(0,new byte[0]);int type=data[at++]&255;
        if(encoding==0||encoding==3){while(at<data.length&&data[at]!=0)at++;at++;}
        else if(encoding==1||encoding==2){while(at+1<data.length&&(data[at]!=0||data[at+1]!=0))at+=2;at+=2;}
        else return new Picture(type,new byte[0]);
        return new Picture(type,at<data.length?image(Arrays.copyOfRange(data,at,data.length)):new byte[0]);
    }
    private static byte[] ogg(InputStream in)throws IOException{
        ByteArrayOutputStream packet=new ByteArrayOutputStream();int consumed=0;
        for(int page=0;page<512&&consumed<LIMIT;page++){
            byte[] h=in.readNBytes(27);if(h.length!=27||h[0]!='O'||h[1]!='g'||h[2]!='g'||h[3]!='S')break;
            byte[] segments=in.readNBytes(h[26]&255);if(segments.length!=(h[26]&255))break;consumed+=27+segments.length;
            for(byte segment:segments){int count=segment&255;if(consumed+count>LIMIT||packet.size()+count>LIMIT)return new byte[0];byte[] bytes=in.readNBytes(count);if(bytes.length!=count)return new byte[0];packet.write(bytes);consumed+=count;
                if(count<255){byte[] p=packet.toByteArray();packet.reset();if(p.length>=7&&p[0]==3&&new String(p,1,6,StandardCharsets.US_ASCII).equals("vorbis"))return comments(p,7);}
            }
        }
        return new byte[0];
    }
    private static byte[] comments(byte[] data,int at){
        if(at+4>data.length)return new byte[0];int vendor=little(data,at);at+=4;if(vendor<0||vendor>data.length-at)return new byte[0];at+=vendor;
        if(at+4>data.length)return new byte[0];int count=little(data,at);at+=4;if(count<0||count>10000)return new byte[0];
        for(int i=0;i<count;i++){if(at+4>data.length)break;int size=little(data,at);at+=4;if(size<0||size>data.length-at)break;String comment=new String(data,at,size,StandardCharsets.UTF_8);at+=size;int equals=comment.indexOf('=');if(equals<0)continue;
            String key=comment.substring(0,equals).toUpperCase(Locale.ROOT);if(!key.equals("METADATA_BLOCK_PICTURE")&&!key.equals("COVERART"))continue;
            try{byte[] picture=Base64.getDecoder().decode(comment.substring(equals+1));byte[] result=key.equals("COVERART")?image(picture):flacPicture(picture);if(result.length>0)return result;}catch(IllegalArgumentException|IndexOutOfBoundsException ignored){}
        }
        return new byte[0];
    }
    private static byte[] flacPicture(byte[] data){int at=4;if(data.length<8)return new byte[0];for(int field=0;field<2;field++){if(at+4>data.length)return new byte[0];int length=integer(data,at);at+=4;if(length<0||length>data.length-at)return new byte[0];at+=length;}if(at+20>data.length)return new byte[0];at+=16;int size=integer(data,at);at+=4;if(size<=0||size>data.length-at)return new byte[0];return image(Arrays.copyOfRange(data,at,at+size));}
    private static byte[] image(byte[] bytes){if(bytes.length<12||bytes.length>LIMIT)return new byte[0];return bytes[0]==(byte)137&&bytes[1]=='P'&&bytes[2]=='N'&&bytes[3]=='G'||bytes[0]==(byte)255&&bytes[1]==(byte)216||bytes[0]=='R'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='F'&&bytes[8]=='W'&&bytes[9]=='E'&&bytes[10]=='B'&&bytes[11]=='P'?bytes:new byte[0];}
    private static int integer(byte[] b,int at){return (b[at]&255)<<24|(b[at+1]&255)<<16|(b[at+2]&255)<<8|b[at+3]&255;}
    private static int little(byte[] b,int at){return (b[at]&255)|(b[at+1]&255)<<8|(b[at+2]&255)<<16|(b[at+3]&255)<<24;}
    private static int sync(byte[] b,int at){int n=0;for(int i=0;i<4;i++){if((b[at+i]&128)!=0)return -1;n=(n<<7)|(b[at+i]&127);}return n;}
    private static byte[] unsync(byte[] b){ByteArrayOutputStream out=new ByteArrayOutputStream();for(int i=0;i<b.length;i++){out.write(b[i]);if(b[i]==(byte)255&&i+1<b.length&&b[i+1]==0)i++;}return out.toByteArray();}
}
