package dev.invumusicplayer.client;

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
    private final Identifier ID = Identifier.fromNamespaceAndPath("invumusicplayer", "cover_" + java.util.UUID.randomUUID().toString().replace("-", ""));
    private static final ExecutorService decode = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "invumusicplayer-cover"); t.setDaemon(true); return t;
    });
    private final AtomicLong serial = new AtomicLong();
    private byte[] previous;
    private boolean ready, closed;
    private int palette=0x352a19,accent=0xb6a384;
    public int paletteAccent(){return accent;}
    public int paletteColor(){return palette;}
    private volatile boolean decodeFailed;
    public String errorMessage() { return decodeFailed ? UiText.text("invumusicplayer.cover.decode_failed") : ""; }
    public boolean ready() { return ready; }
    public void update(byte[] bytes) {
        if (closed || java.util.Arrays.equals(bytes, previous)) return;
        previous = bytes; long request = serial.incrementAndGet(); ready = false; decodeFailed = false;
        Minecraft.getInstance().getTextureManager().release(ID);
        if (bytes.length == 0) return;
        decode.execute(() -> {
            Thread.currentThread().setContextClassLoader(CoverTexture.class.getClassLoader());
            ImageIO.scanForPlugins();
            try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(in); if (!readers.hasNext()) throw new IOException("Unsupported image format");
                var reader = readers.next();
                byte[] png;int background,foreground;
                try {
                    reader.setInput(in);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long) w * h > 8_000_000) return;
                    BufferedImage source = reader.read(0), scaled = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                    var g = scaled.createGraphics();
                    g.drawImage(source, 0, 0, 256, 256, null); g.dispose();
                    long red=0,green=0,blue=0;int count=0;for(int yy=0;yy<256;yy+=8)for(int xx=0;xx<256;xx+=8){int rgb=scaled.getRGB(xx,yy);red+=(rgb>>16)&255;green+=(rgb>>8)&255;blue+=rgb&255;count++;}
                    background=((int)(red/count*.42)<<16)|((int)(green/count*.42)<<8)|(int)(blue/count*.42);
                    foreground=((int)(red/count*.6+96)<<16)|((int)(green/count*.6+96)<<8)|(int)(blue/count*.6+96);
                    BufferedImage mask=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);var mg=mask.createGraphics();mg.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);mg.setColor(java.awt.Color.WHITE);mg.fillRoundRect(0,0,256,256,12,12);mg.dispose();
                    var clip=scaled.createGraphics();clip.setComposite(java.awt.AlphaComposite.DstIn);clip.drawImage(mask,0,0,null);clip.dispose();
                    ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(scaled, "png", out); png = out.toByteArray();
                } finally { reader.dispose(); }
                Minecraft.getInstance().execute(() -> {
                    if (closed || request != serial.get()) return;
                    try {
                        DynamicTexture texture = new DynamicTexture(() -> "InvuMusicPlayer album art", NativeImage.read(new ByteArrayInputStream(png)));
                        Minecraft.getInstance().getTextureManager().register(ID, texture); palette=background;accent=foreground; ready = true;
                    } catch (IOException e) { failed(request); }
                });
            } catch (Exception e) { failed(request); }
        });
    }
    private void failed(long request) {
        if (request != serial.get() || closed) return;
        decodeFailed = true;
        org.slf4j.LoggerFactory.getLogger("invumusicplayer").warn("Artwork decode failed / Не удалось прочитать изображение обложки");
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
        closed = true; serial.incrementAndGet();
        Minecraft.getInstance().getTextureManager().release(ID);
    }
}
