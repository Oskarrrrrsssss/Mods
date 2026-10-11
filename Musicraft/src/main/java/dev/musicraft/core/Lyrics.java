package dev.musicraft.core;

import java.util.*;
import java.util.regex.*;

/** Immutable LRC timeline; timestamps refer to the audio playback clock. */
public final class Lyrics {
    public record Line(long millis, String text) {}
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,4}):([0-5]\\d)(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);
    public static final Lyrics EMPTY = new Lyrics(List.of());
    private final List<Line> lines;
    public Lyrics(List<Line> lines) { this.lines = List.copyOf(lines); }
    public List<Line> lines() { return lines; }
    public static Lyrics parse(String input) {
        if (input == null || input.isBlank()) return EMPTY;
        long offset = 0;
        Matcher offsets = OFFSET.matcher(input);
        while (offsets.find()) {
            try { offset = Long.parseLong(offsets.group(1)); } catch (NumberFormatException ignored) {}
        }
        List<Line> result = new ArrayList<>();
        for (String raw : input.replace("\ufeff", "").split("\\R")) {
            Matcher timestamps = TIME.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (timestamps.find()) {
                String fraction = timestamps.group(3);
                long fractional = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                long time = Long.parseLong(timestamps.group(1)) * 60_000L
                    + Long.parseLong(timestamps.group(2)) * 1000 + fractional + offset;
                times.add(Math.max(0, time)); end = timestamps.end();
            }
            String text = raw.substring(end).replaceAll("<\\d+:\\d+(?:\\.\\d+)?>", "").strip();
            for (long time : times) result.add(new Line(time, text));
        }
        result.sort(Comparator.comparingLong(Line::millis));
        return new Lyrics(result);
    }
    public int indexAt(long millis) {
        int lo = 0, hi = lines.size() - 1, answer = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).millis <= millis) { answer = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        return answer;
    }
    public String textAt(long millis) { int i = indexAt(millis); return i < 0 ? "" : lines.get(i).text; }
}
