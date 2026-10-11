package dev.musicraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class CoverTexture implements AutoCloseable {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("musicraft", "cover");
    private final ExecutorService decode = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "musicraft-cover"); t.setDaemon(true); return t;
    });
    private final AtomicLong serial = new AtomicLong();
    private byte[] previous;
    private boolean ready, closed;
    public void update(byte[] bytes) {
        if (closed || bytes == previous) return;
        previous = bytes; long request = serial.incrementAndGet(); ready = false;
        Minecraft.getInstance().getTextureManager().release(ID);
        if (bytes.length == 0) return;
        decode.execute(() -> {
            try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(in); if (!readers.hasNext()) return;
                var reader = readers.next();
                byte[] png;
                try {
                    reader.setInput(in);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long) w * h > 8_000_000) return;
                    BufferedImage source = reader.read(0), scaled = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                    var g = scaled.createGraphics();
                    g.drawImage(source, 0, 0, 256, 256, null); g.dispose();
                    ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(scaled, "png", out); png = out.toByteArray();
                } finally { reader.dispose(); }
                Minecraft.getInstance().execute(() -> {
                    if (closed || request != serial.get()) return;
                    try {
                        DynamicTexture texture = new DynamicTexture(() -> "Musicraft album art", NativeImage.read(new ByteArrayInputStream(png)));
                        Minecraft.getInstance().getTextureManager().register(ID, texture); ready = true;
                    } catch (IOException ignored) {}
                });
            } catch (Exception ignored) {}
        });
    }
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int size, int accent) {
        if (ready) graphics.blit(RenderPipelines.GUI_TEXTURED, ID, x, y, 0f, 0f, size, size, 256, 256, 256, 256);
        else {
            graphics.fill(x, y, x + size, y + size, 0xff202d25);
            graphics.fill(x + size / 4, y + size / 4, x + size * 3 / 4, y + size * 3 / 4, 0xff000000 | accent);
            graphics.fill(x + size * 3 / 8, y + size * 3 / 8, x + size * 5 / 8, y + size * 5 / 8, 0xff202d25);
        }
    }
    @Override public void close() {
        closed = true; serial.incrementAndGet(); decode.shutdownNow();
        Minecraft.getInstance().getTextureManager().release(ID);
    }
}
