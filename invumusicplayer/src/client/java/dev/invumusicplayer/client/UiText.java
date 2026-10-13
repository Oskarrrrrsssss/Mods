package dev.invumusicplayer.client;

import net.minecraft.network.chat.Component;

/** Resolve strings only when drawing or measuring; widgets retain translatable components. */
final class UiText {
    private UiText() {}
    static String text(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
    static Component toggle(boolean enabled) {
        return Component.translatable(enabled ? "invumusicplayer.common.on" : "invumusicplayer.common.off");
    }
}
