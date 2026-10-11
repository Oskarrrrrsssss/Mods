package dev.musicraft.core;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Windows DPAPI binds the encrypted credential to the current Windows account. */
public final class TokenStore {
    private final Path path;
    private final Protection protection;
    public TokenStore(Path path) { this(path, new WindowsProtection()); }
    TokenStore(Path path, Protection protection) {
        this.path = path.toAbsolutePath(); this.protection = protection;
    }
    public synchronized String load() throws IOException {
        if (!Files.exists(path)) return "";
        byte[] plain = null;
        try {
            byte[] encrypted;
            try (var input = Files.newInputStream(path)) { encrypted = input.readNBytes(65537); }
            if (encrypted.length == 0 || encrypted.length > 65536) throw new IOException();
            plain = protection.unprotect(encrypted);
            if (plain == null || plain.length > TokenInput.MAX_LENGTH) throw new IOException();
            return TokenInput.normalize(new String(plain, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException | LinkageError e) {
            throw new IOException("Не удалось открыть авторизацию. Подключи аккаунт заново.");
        } finally { if (plain != null) Arrays.fill(plain, (byte) 0); }
    }
    public synchronized void save(String token) throws IOException {
        String normalized = TokenInput.normalize(token);
        Files.createDirectories(path.getParent());
        byte[] protectedBytes;
        byte[] plain = normalized.getBytes(StandardCharsets.UTF_8);
        try {
            protectedBytes = protection.protect(plain);
            if (protectedBytes == null || protectedBytes.length == 0 || protectedBytes.length > 65536) throw new IOException();
        } catch (IOException | RuntimeException | LinkageError e) {
            throw new IOException("Windows не смогла сохранить авторизацию. Проверь доступ к папке config.");
        } finally { Arrays.fill(plain, (byte) 0); }
        Path tmp = Files.createTempFile(path.getParent(), "musicraft-auth-", ".tmp");
        try {
            Files.write(tmp, protectedBytes);
            try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    public synchronized void clear() throws IOException { Files.deleteIfExists(path); }
    interface Protection {
        byte[] protect(byte[] plain) throws IOException;
        byte[] unprotect(byte[] encrypted) throws IOException;
    }
    private static final class WindowsProtection implements Protection {
        @Override public byte[] protect(byte[] plain) throws IOException {
            requireWindows(); return Crypt32Util.cryptProtectData(plain);
        }
        @Override public byte[] unprotect(byte[] encrypted) throws IOException {
            requireWindows(); return Crypt32Util.cryptUnprotectData(encrypted);
        }
    }
    private static void requireWindows() throws IOException {
        if (!System.getProperty("os.name").startsWith("Windows")) throw new IOException("Подключение аккаунта в этой сборке поддерживает Windows.");
    }
}
