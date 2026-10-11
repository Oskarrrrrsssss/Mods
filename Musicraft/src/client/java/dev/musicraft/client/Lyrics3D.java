package dev.musicraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.musicraft.core.*;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Matrix4f;

/** Capture game state during extraction, submit immutable text data during rendering. */
public final class Lyrics3D {
    private record Snapshot(Vec3 anchor, Quaternionf rotation, FormattedCharSequence text,
                            FormattedCharSequence next, float width, float nextWidth, float scale, int color) {}
    private static final Snapshot EMPTY = new Snapshot(null, null, null, null, 0, 0, 0, 0);
    private static final RenderStateDataKey<Snapshot> KEY = RenderStateDataKey.create(() -> "musicraft lyrics");
    private static Vec3 previousAnchor;
    private static long previousTime;
    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            Minecraft mc = Minecraft.getInstance(); Player player = MusicraftClient.player; Settings s = player.settings;
            Snapshot state = EMPTY;
            long position = player.positionMillis() + s.lyricOffsetMillis;
            Lyrics timeline = player.lyrics();
            int index = timeline.indexAt(position);
            if (mc.player != null && mc.gui.screen() == null && !mc.gui.hud.isHidden() && s.lyrics3d
                && player.playing() && (!s.sprintOnly || mc.player.isSprinting()) && index >= 0) {
                var lines = timeline.lines(); String text = lines.get(index).text();
                if (!text.isBlank()) {
                    float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
                    Vec3 direction = Vec3.directionFromRotation(0, mc.player.getYRot());
                    Vec3 target = mc.player.getEyePosition(partial).add(direction.scale(s.lyricDistance)).add(0, s.lyricHeight, 0);
                    long now = System.nanoTime();
                    double dt = Math.max(0, Math.min(.1, (now - previousTime) / 1_000_000_000.0));
                    if (previousAnchor == null || previousAnchor.distanceToSqr(target) > 64) previousAnchor = target;
                    else previousAnchor = previousAnchor.lerp(target, 1 - Math.exp(-12 * dt));
                    previousTime = now;
                    float fade = Math.min(1, Math.max(0, (position - lines.get(index).millis()) / 180f));
                    int alpha = Math.max(8, (int) (255 * fade));
                    String next = index + 1 < lines.size() ? lines.get(index + 1).text() : "";
                    // Keep world text comfortably sized even with unusually long lyric lines.
                    text = mc.font.plainSubstrByWidth(text, 360); next = mc.font.plainSubstrByWidth(next, 360);
                    state = new Snapshot(previousAnchor, new Quaternionf(context.camera().rotation()),
                        Component.literal(text).getVisualOrderText(), Component.literal(next).getVisualOrderText(),
                        mc.font.width(text), mc.font.width(next), s.lyricScale * (.92f + .08f * fade), (alpha << 24) | s.accent);
                }
            }
            if (state == EMPTY) { previousAnchor = null; previousTime = 0; }
            context.levelState().setData(KEY, state);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Snapshot state = context.levelState().getData(KEY);
            if (state == null || state.anchor == null) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            PoseStack pose = context.poseStack(); pose.pushPose();
            Vec3 relative = state.anchor.subtract(camera); pose.translate(relative.x, relative.y, relative.z);
            pose.mulPose(new Matrix4f().rotation(state.rotation)); pose.scale(state.scale, -state.scale, state.scale);
            context.submitNodeCollector().submitText(pose, -state.width / 2, 0, state.text,
                true, Font.DisplayMode.NORMAL, 0x00f000f0, state.color, 0x600e1711, 0);
            context.submitNodeCollector().submitText(pose, -state.nextWidth / 2, 13, state.next,
                false, Font.DisplayMode.NORMAL, 0x00f000f0, 0x989bad9f, 0, 0);
            pose.popPose();
        });
    }
}
