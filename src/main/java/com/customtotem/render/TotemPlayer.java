package com.customtotem.render;

import com.customtotem.baker.TotemBaker;
import com.customtotem.util.PlatformUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Zero-allocation single-frame streaming texture player.
 * Reads positional frames (4MB) on demand and uploads via glTexSubImage2D.
 */
public class TotemPlayer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotem/Player");

    private final Path binPath;
    private final String id;
    private FileChannel fileChannel;

    private int frameWidth;
    private int frameHeight;
    private int frameCount;
    private int fileFps;
    private int effectiveFps;

    private NativeImage nativeImage;
    private NativeImageBackedTexture dynamicTexture;
    private Identifier textureId;
    private ByteBuffer frameBuffer; // Reusable direct ByteBuffer (4 MB)
    private long nativePointer;

    private int currentFrame = -1;
    private long startTimeMs = -1;
    private boolean closed = false;

    public TotemPlayer(String id, Path binPath) throws IOException {
        this.id = id;
        this.binPath = binPath;
        initHeader();
    }

    private void initHeader() throws IOException {
        this.fileChannel = FileChannel.open(binPath, StandardOpenOption.READ);
        ByteBuffer header = ByteBuffer.allocate(TotemBaker.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        fileChannel.read(header);
        header.flip();

        int magic = header.getInt();
        if (magic != TotemBaker.HEADER_MAGIC) {
            throw new IOException("Invalid TOTM bin magic header: " + Integer.toHexString(magic));
        }
        this.frameWidth = header.getInt();
        this.frameHeight = header.getInt();
        this.frameCount = header.getInt();
        this.fileFps = header.getInt();

        int defaultCap = PlatformUtil.isAndroid() ? 30 : 60;
        this.effectiveFps = Math.min(this.fileFps > 0 ? this.fileFps : 60, defaultCap);

        int frameSizeBytes = frameWidth * frameHeight * 4;
        this.frameBuffer = ByteBuffer.allocateDirect(frameSizeBytes).order(ByteOrder.LITTLE_ENDIAN);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getTextureManager() != null) {
            this.nativeImage = new NativeImage(NativeImage.Format.RGBA, frameWidth, frameHeight, false);
            this.nativePointer = this.nativeImage.imageId(); // Yarn 1.21.11 public pointer accessor
            this.dynamicTexture = new NativeImageBackedTexture(() -> "customtotem/stream_" + id, this.nativeImage);
            this.textureId = Identifier.of("customtotem", "stream_" + id);
            client.getTextureManager().registerTexture(this.textureId, this.dynamicTexture);
        }
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public boolean update(long nowMs) {
        return update(nowMs, true, 0);
    }

    public boolean update(long nowMs, boolean animated, int targetFps) {
        if (closed || fileChannel == null || !fileChannel.isOpen() || dynamicTexture == null) {
            return false;
        }

        int defaultCap = PlatformUtil.isAndroid() ? 30 : 60;
        int chosenFps = (targetFps > 0) ? targetFps : (fileFps > 0 ? fileFps : 60);
        this.effectiveFps = Math.min(chosenFps, defaultCap);

        int targetFrame = 0;
        if (animated && frameCount > 1) {
            if (startTimeMs < 0) {
                startTimeMs = nowMs;
            }
            long elapsedMs = Math.max(0, nowMs - startTimeMs);
            targetFrame = (int) ((elapsedMs * (long) effectiveFps / 1000L) % (long) frameCount);
        }

        if (targetFrame == currentFrame) {
            return true;
        }

        currentFrame = targetFrame;
        long offset = (long) TotemBaker.HEADER_SIZE + ((long) currentFrame * (long) (frameWidth * frameHeight * 4));

        try {
            frameBuffer.clear();
            int bytesRead = fileChannel.read(frameBuffer, offset);
            if (bytesRead <= 0) return false;
            frameBuffer.flip();

            // Direct off-heap copy to NativeImage buffer (Zero-GC)
            long srcAddress = MemoryUtil.memAddress(frameBuffer);
            MemoryUtil.memCopy(srcAddress, nativePointer, (long) frameWidth * frameHeight * 4L);

            // Upload single frame via glTexSubImage2D
            dynamicTexture.upload();
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to stream frame {} from {}", currentFrame, binPath, e);
            return false;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        if (fileChannel != null) {
            try { fileChannel.close(); } catch (Exception ignored) {}
            fileChannel = null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getTextureManager() != null && textureId != null) {
            client.getTextureManager().destroyTexture(textureId);
        }

        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        if (nativeImage != null) {
            nativeImage.close();
            nativeImage = null;
        }
        frameBuffer = null;
    }

    public boolean isClosed() {
        return closed;
    }
}
