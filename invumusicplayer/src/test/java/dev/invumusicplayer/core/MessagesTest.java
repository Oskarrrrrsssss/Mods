package dev.invumusicplayer.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class MessagesTest {
    @TempDir Path temp;
    @AfterEach void restoreDefaultLanguage() { Messages.resetTranslator(); }

    @Test void existingPlayerStatusAndAccountErrorFollowLanguageChanges() throws Exception {
        Messages.resetTranslator();
        try (Player player = new Player(temp)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            String expected = Messages.get("invumusicplayer.status.local_count", 0);
            while (!player.status().equals(expected) && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals("Локальные треки: 0", player.status());
            player.connect("invalid token");
            assertEquals(Messages.get("invumusicplayer.error.token_invalid"), player.accountError());
            useLanguage("en_us");
            assertEquals("Local tracks: 0", player.status());
            assertEquals("Account not connected", player.accountStatus());
            assertEquals("Enter a Yandex Music token. Do not enter your password here.", player.accountError());
            useLanguage("ru_ru");
            assertEquals("Локальные треки: 0", player.status());
            assertEquals(Messages.get("invumusicplayer.error.token_invalid"), player.accountError());
        }
    }

    @Test void capturedServiceErrorAndNestedContextFollowLanguageChanges() throws Exception {
        Messages.Failure failure = Messages.error("invumusicplayer.error.api_unexpected", Messages.text("invumusicplayer.context.audio"));
        assertEquals("Неожиданный ответ Яндекс Музыки: аудио.", failure.getMessage());
        useLanguage("en_us");
        assertEquals("Unexpected Yandex Music response: audio.", failure.getMessage());
        assertEquals(failure.getMessage(), failure.getLocalizedMessage());
        assertNull(failure.getCause());
    }

    @Test void retainedMessageDefensivelyCopiesItsArguments() throws Exception {
        Object[] arguments = { 7 };
        Messages.Text text = Messages.text("invumusicplayer.status.local_count", arguments);
        arguments[0] = 123;
        text.arguments()[0] = 456;
        useLanguage("en_us");
        assertEquals("Local tracks: 7", text.getString());
    }

    private static void useLanguage(String language) throws Exception {
        try (var input = MessagesTest.class.getResourceAsStream("/assets/invumusicplayer/lang/" + language + ".json")) {
            assertNotNull(input, "Language assets must be present on the standalone test classpath");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Messages.setTranslator((key, arguments) -> {
                assertTrue(json.has(key), "Missing translation: " + key);
                return String.format(Locale.ROOT, json.get(key).getAsString(), arguments);
            });
        }
    }
}
