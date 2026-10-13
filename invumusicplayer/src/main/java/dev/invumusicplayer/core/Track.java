package dev.invumusicplayer.core;

import java.nio.file.Path;

public record Track(String id, String title, String artist, long durationMillis,
                    Path file, Path coverFile, String coverUrl, String batchId) {
    @Override public String title() { return title == null || title.isBlank() ? Messages.get("invumusicplayer.track.untitled") : title; }
    public boolean spotify(){return id.startsWith("spotify:track:");}
    public boolean youtube() { return id.startsWith("youtube:"); }
    public boolean remote() { return file == null; }
    public String display() { return artist.isBlank() ? title() : artist + " — " + title(); }
}
