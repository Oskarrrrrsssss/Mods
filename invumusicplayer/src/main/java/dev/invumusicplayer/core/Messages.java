package dev.invumusicplayer.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** A Minecraft-independent bridge to the currently selected game language. */
public final class Messages {
    public static final Text EMPTY = new Text("", new Object[0]);
    private static final Map<String, String> FALLBACK = fallbackLanguage();
    private static final BiFunction<String, Object[], String> DEFAULT = Messages::fallback;
    private static volatile BiFunction<String, Object[], String> translator = DEFAULT;

    private Messages() {}

    /** Bind the game's translator before creating a Player. The translator may run on I/O workers. */
    public static void setTranslator(BiFunction<String, Object[], String> value) {
        translator = Objects.requireNonNull(value);
    }

    /** Restore the dependency-free resource translator, for tools and standalone tests. */
    public static void resetTranslator() { translator = DEFAULT; }

    public static Text text(String key, Object... arguments) { return new Text(key, arguments); }
    public static String get(String key, Object... arguments) { return text(key, arguments).getString(); }
    public static Failure error(String key, Object... arguments) { return new Failure(text(key, arguments)); }

    /** Retain keys rather than translated strings, so an open screen follows language changes. */
    public record Text(String key, Object[] arguments) {
        public Text {
            key = Objects.requireNonNull(key);
            arguments = arguments.clone();
        }
        @Override public Object[] arguments() { return arguments.clone(); }
        public String getString() {
            if (key.isEmpty()) return "";
            Object[] values = arguments.clone();
            for (int i = 0; i < values.length; i++) if (values[i] instanceof Text text) values[i] = text.getString();
            return translator.apply(key, values);
        }
        @Override public String toString() { return getString(); }
    }

    /** Translated, bounded errors without a native cause that could contain a credential or path. */
    public static class Failure extends IOException {
        private final Text text;
        Failure(Text text) { this.text = Objects.requireNonNull(text); }
        public final Text text() { return text; }
        @Override public String getMessage() { return text.getString(); }
        @Override public String getLocalizedMessage() { return getMessage(); }
    }

    private static String fallback(String key, Object[] arguments) {
        String template = FALLBACK.getOrDefault(key, key);
        try { return String.format(Locale.ROOT, template, arguments); }
        catch (RuntimeException ignored) { return template; }
    }

    private static Map<String, String> fallbackLanguage() {
        Map<String, String> messages = new HashMap<>();
        // Keep one source of truth in the mod's language assets. RU remains the standalone
        // default; the game bridge handles any selected language and resource packs.
        for (String language : new String[] { "en_us", "ru_ru" }) {
            String resource = "/assets/invumusicplayer/lang/" + language + ".json";
            try (InputStream input = Messages.class.getResourceAsStream(resource)) {
                if (input == null) continue;
                JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet())
                    if (entry.getValue().isJsonPrimitive()) messages.put(entry.getKey(), entry.getValue().getAsString());
            } catch (IOException | RuntimeException ignored) { /* Missing assets must not block a standalone tool. */ }
        }
        return Map.copyOf(messages);
    }
}
