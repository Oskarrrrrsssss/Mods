package dev.invumusicplayer.core;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Library snapshots contain metadata only, never credentials or downloaded audio. */
public final class SessionStore {
    public record State(List<Track> queue, int index, boolean wave, List<Track> pending,
                        Map<String,Integer> ratings, Map<String,List<Track>> playlists, Track current, List<Track> originalQueue, Map<String,String> moods) {}
    private record Item(String id, String title, String artist, long duration, String file, String coverFile, String cover, String batch) {
        static Item from(Track t) { return new Item(t.id(), t.title(), t.artist(), t.durationMillis(), t.file()==null?null:t.file().toString(), t.coverFile()==null?null:t.coverFile().toString(), t.coverUrl(), t.batchId()); }
        Track track() { return new Track(id, title, artist == null ? "" : artist, Math.max(0,duration), file==null?null:Path.of(file), coverFile==null?null:Path.of(coverFile), cover==null?"":cover, batch==null?"":batch); }
    }
    private record Data(List<Item> queue, int index, boolean wave, List<Item> pending, Map<String,Integer> ratings, Map<String,List<Item>> playlists, Item current, List<Item> originalQueue, Map<String,String> moods) {}
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    public SessionStore(Path path) { this.path=path; }
    public State load() {
        State empty = new State(List.of(),-1,false,List.of(),Map.of(),Map.of(),null,List.of(),Map.of());
        if (!Files.exists(path)) return empty;
        try (InputStream in=Files.newInputStream(path)) {
            Data data=JSON.fromJson(new String(YandexMusic.bounded(in,4_000_000),java.nio.charset.StandardCharsets.UTF_8),Data.class);
            Map<String,List<Track>> lists=new LinkedHashMap<>(); if(data.playlists!=null) data.playlists.entrySet().stream().limit(100).forEach(e->lists.put(e.getKey(),read(e.getValue())));
            List<Track> queue=read(data.queue); return new State(queue,Math.max(-1,Math.min(data.index,queue.size()-1)),data.wave,read(data.pending),data.ratings==null?Map.of():Map.copyOf(data.ratings),Map.copyOf(lists),data.current==null?null:data.current.track(),read(data.originalQueue),data.moods==null?Map.of():Map.copyOf(data.moods));
        } catch(Exception ignored) { return empty; }
    }
    private static List<Track> read(List<Item> items) { if(items==null)return List.of(); return items.stream().limit(10000).map(Item::track).toList(); }
    private static List<Item> write(List<Track> tracks) { return tracks.stream().limit(10000).map(Item::from).toList(); }
    public void save(State state) throws IOException {
        Map<String,List<Item>> lists=new LinkedHashMap<>(); state.playlists.entrySet().stream().limit(100).forEach(e->lists.put(e.getKey(),write(e.getValue())));
        Data data=new Data(write(state.queue),state.index,state.wave,write(state.pending),state.ratings,lists,state.current==null?null:Item.from(state.current),write(state.originalQueue),state.moods);
        Files.createDirectories(path.getParent()); Path temp=Files.createTempFile(path.getParent(),"invu-session-",".tmp");
        try { String json=JSON.toJson(data);if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4_000_000)throw Messages.error("invumusicplayer.error.resource_too_large");Files.writeString(temp,json); try { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); } catch(AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); } }
        finally { Files.deleteIfExists(temp); }
    }
}
