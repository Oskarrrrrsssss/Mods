package dev.musicraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

final class AccountTest {
    @TempDir Path temp;
    private static final String TOKEN = "synthetic-music-token-for-tests";

    @Test void pastedAuthorizationPrefixesAreNormalized() throws Exception {
        assertEquals(TOKEN, TokenInput.normalize("  " + TOKEN + "  "));
        assertEquals(TOKEN, TokenInput.normalize("OAuth " + TOKEN));
        assertEquals(TOKEN, TokenInput.normalize("bearer " + TOKEN));
    }

    @Test void officialRedirectTokenIsExtractedFromFragmentOnly() throws Exception {
        assertEquals(TOKEN, TokenInput.normalize("https://music.yandex.ru/#access_token=" + TOKEN + "&token_type=bearer"));
        assertEquals("synthetic+token==", TokenInput.normalize("https://oauth.yandex.ru/#access_token=synthetic%2Btoken%3D%3D"));
        assertThrows(IOException.class, () -> TokenInput.normalize("https://music.yandex.ru/?access_token=" + TOKEN));
    }

    @Test void invalidInputNeverAppearsInErrorsOrCauses() {
        for (String input : new String[] {
            TOKEN + "\r\nX-Header: injected", TOKEN + " пароль", "\"" + TOKEN + "\"",
            "https://evil.test/#access_token=" + TOKEN,
            "https://music.yandex.ru.evil.test/#access_token=" + TOKEN,
            "https://user@music.yandex.ru/#access_token=" + TOKEN,
            "https://music.yandex.ru:444/#access_token=" + TOKEN,
            "https://music.yandex.ru/#access_token=" + TOKEN + "&access_token=other",
            "https://music.yandex.ru/#access_token=%zz" + TOKEN,
            "https://music.yandex.ru/#access_token=" + TOKEN + " forbidden",
            "x".repeat(TokenInput.MAX_LENGTH + 1), "", "  "
        }) {
            IOException error = assertThrows(IOException.class, () -> TokenInput.normalize(input));
            assertEquals(TokenInput.INVALID, error.getMessage());
            assertNull(error.getCause());
            assertFalse(error.toString().contains(TOKEN));
        }
    }

    @Test void encryptedPersistenceDoesNotWritePlaintextAndClearsBuffers() throws Exception {
        RecordingProtection protection = new RecordingProtection();
        Path file = temp.resolve("config/auth.dat"); TokenStore store = new TokenStore(file, protection);
        store.save(TOKEN);
        assertFalse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains(TOKEN));
        assertArrayEquals(new byte[TOKEN.length()], protection.input);
        assertEquals(TOKEN, store.load());
        assertArrayEquals(new byte[TOKEN.length()], protection.output);
        store.clear(); assertFalse(Files.exists(file)); assertEquals("", store.load());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsDpapiProtectsSyntheticTokenForTheCurrentUser() throws Exception {
        Path file = temp.resolve("config/windows-auth.dat");
        TokenStore store = new TokenStore(file); // Production Windows DPAPI implementation.
        assertEquals("", store.load());
        store.save(TOKEN);
        byte[] protectedFile = Files.readAllBytes(file);
        assertTrue(protectedFile.length > 0);
        assertFalse(new String(protectedFile, StandardCharsets.UTF_8).contains(TOKEN));
        // A separate instance proves the persisted file can be opened under this Windows account.
        assertEquals(TOKEN, new TokenStore(file).load());

        IOException rejected = assertThrows(IOException.class, () -> store.save(TOKEN + "\r\nHeader: injected"));
        assertFalse(rejected.toString().contains(TOKEN));
        assertArrayEquals(protectedFile, Files.readAllBytes(file));
        assertEquals(TOKEN, new TokenStore(file).load());

        store.clear();
        assertFalse(Files.exists(file));
        assertEquals("", new TokenStore(file).load());
        try (var files = Files.list(file.getParent())) { assertEquals(0, files.count()); }
    }

    @Test void invalidTokenCannotReplaceAnExistingSavedAccount() throws Exception {
        Path file = temp.resolve("auth.dat"); TokenStore store = new TokenStore(file, new RecordingProtection());
        store.save(TOKEN); byte[] saved = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> store.save(TOKEN + "\r\n" + TOKEN));
        assertArrayEquals(saved, Files.readAllBytes(file)); assertEquals(TOKEN, store.load());
    }

    @Test void encryptionFailurePreservesPriorAccountAndDoesNotLeaveTemporaryFiles() throws Exception {
        Path file = temp.resolve("auth.dat"); RecordingProtection protection = new RecordingProtection();
        TokenStore store = new TokenStore(file, protection); store.save(TOKEN);
        byte[] saved = Files.readAllBytes(file); protection.fail = true;
        IOException error = assertThrows(IOException.class, () -> store.save("replacement-token"));
        assertArrayEquals(saved, Files.readAllBytes(file));
        assertFalse(error.toString().contains(TOKEN)); assertNull(error.getCause());
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
        assertArrayEquals(new byte["replacement-token".length()], protection.input);
    }

    @Test void emptyOrOversizedEncryptedFilesAreRejectedBeforeDecrypting() throws Exception {
        Path file = temp.resolve("auth.dat"); RecordingProtection protection = new RecordingProtection();
        TokenStore store = new TokenStore(file, protection);
        Files.write(file, new byte[0]); assertThrows(IOException.class, store::load);
        Files.write(file, new byte[65537]); assertThrows(IOException.class, store::load);
        assertEquals(0, protection.decryptCalls);
    }

    @Test void decryptedInvalidTokenIsRejectedAndWiped() throws Exception {
        Path file = temp.resolve("auth.dat"); RecordingProtection protection = new RecordingProtection();
        byte[] invalid = (TOKEN + "\r\nHeader: leaked").getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = Arrays.copyOf(invalid, invalid.length);
        for (int i = 0; i < encrypted.length; i++) encrypted[i] ^= 0x55;
        Files.write(file, encrypted);
        IOException error = assertThrows(IOException.class, new TokenStore(file, protection)::load);
        assertFalse(error.toString().contains(TOKEN)); assertNull(error.getCause());
        assertArrayEquals(new byte[invalid.length], protection.output);
    }

    // Synthetic transform exercises the storage boundary. This is not a DPAPI substitute at runtime.
    private static final class RecordingProtection implements TokenStore.Protection {
        byte[] input, output; boolean fail; int decryptCalls;
        @Override public byte[] protect(byte[] plain) throws IOException {
            input = plain;
            if (fail) throw new IOException(TOKEN);
            byte[] encoded = Arrays.copyOf(plain, plain.length);
            for (int i = 0; i < encoded.length; i++) encoded[i] ^= 0x55;
            return encoded;
        }
        @Override public byte[] unprotect(byte[] encrypted) {
            decryptCalls++; output = Arrays.copyOf(encrypted, encrypted.length);
            for (int i = 0; i < output.length; i++) output[i] ^= 0x55;
            return output;
        }
    }
}
