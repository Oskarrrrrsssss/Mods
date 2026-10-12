package dev.invumusicplayer.core;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Parses a user-pasted Music OAuth token without logging or retaining the pasted text. */
public final class TokenInput {
    public static final int MAX_LENGTH = 4096;
    /** @deprecated Use Messages.get so the error follows the current language. */
    @Deprecated public static final String INVALID = Messages.get("invumusicplayer.error.token_invalid");

    private TokenInput() {}

    public static String normalize(String input) throws IOException {
        if (input == null || input.length() > MAX_LENGTH + 2048) throw invalid();
        String value = input.strip();
        if (value.regionMatches(true, 0, "OAuth ", 0, 6)) value = value.substring(6).strip();
        else if (value.regionMatches(true, 0, "Bearer ", 0, 7)) value = value.substring(7).strip();
        if (value.startsWith("https://")) value = tokenFromRedirect(value);
        // OAuth token syntax. Never permit CR/LF, whitespace, quotes or Unicode in a header.
        if (value.isEmpty() || value.length() > MAX_LENGTH || !value.matches("[A-Za-z0-9._~+/-]+=*")) throw invalid();
        return value;
    }

    private static String tokenFromRedirect(String text) throws IOException {
        try {
            URI uri = URI.create(text);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || !switch (host.toLowerCase(Locale.ROOT)) {
                    case "music.yandex.ru", "music.yandex.com", "oauth.yandex.ru", "oauth.yandex.com" -> true;
                    default -> false;
                }) throw invalid();
            String fragment = uri.getRawFragment();
            if (fragment == null) throw invalid();
            String token = null;
            for (String part : fragment.split("&")) {
                int split = part.indexOf('=');
                if (split < 0 || !part.substring(0, split).equals("access_token")) continue;
                if (token != null) throw invalid();
                // Form decoding only for the fragment's token, not for an arbitrary URL.
                token = URLDecoder.decode(part.substring(split + 1), StandardCharsets.UTF_8);
            }
            if (token == null) throw invalid();
            return token;
        } catch (IllegalArgumentException e) {
            // URI parse errors include the original URI. It contains a credential; do not propagate it.
            throw invalid();
        }
    }

    private static IOException invalid() { return Messages.error("invumusicplayer.error.token_invalid"); }
}
