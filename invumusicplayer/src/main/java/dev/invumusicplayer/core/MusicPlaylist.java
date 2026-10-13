package dev.invumusicplayer.core;

public record MusicPlaylist(String owner,String kind,String title,String service){
    public MusicPlaylist(String owner,String kind,String title){this(owner,kind,title,"yandex");}
    public boolean spotify(){return service.equals("spotify");}
}
