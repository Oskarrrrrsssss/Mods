package dev.invumusicplayer.client.mixin;

import dev.invumusicplayer.client.PlayerHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.4 skips Gui.render entirely with F1; draw before the GUI projection is restored. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void invumusicplayer$drawWithHiddenHud(float partialTick, long nanoTime,
                                                  boolean renderLevel, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!renderLevel || !client.isGameLoadFinished() || !client.options.hideGui
            || client.getOverlay() != null || !PlayerHud.isVisible()) return;
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        PlayerHud.draw(graphics);
        graphics.flush();
    }
}
