package dev.musicraft.core;

import java.nio.file.Path;

public record Track(String id, String title, String artist, long durationMillis,
                    Path file, Path coverFile, String coverUrl, String batchId) {
    public boolean remote() { return file == null; }
    public String display() { return artist.isBlank() ? title : artist + " — " + title; }
}
