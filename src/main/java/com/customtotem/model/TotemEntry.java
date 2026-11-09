package com.customtotem.model;

import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public class TotemEntry {
    private final String id;
    private final String fileName;
    private String displayName;
    private boolean enabled;
    private final int width;
    private final int height;
    private final int frameCount;
    private int frameTime;

    private transient Identifier textureId;
    private transient NativeImageBackedTexture texture;

    public TotemEntry(String id, String fileName, String displayName, boolean enabled, int width, int height) {
        this.id = id;
        this.fileName = fileName;
        this.displayName = displayName;
        this.enabled = enabled;
        this.width = width;
        this.height = height;
        if (width > 0 && height >= width && height % width == 0) {
            this.frameCount = Math.max(1, height / width);
        } else {
            this.frameCount = 1;
        }
        this.frameTime = 2; // Default 2 ticks per frame
    }

    public String getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getFrameTime() {
        return frameTime;
    }

    public void setFrameTime(int frameTime) {
        this.frameTime = Math.max(1, frameTime);
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public void setTextureId(Identifier textureId) {
        this.textureId = textureId;
    }

    public NativeImageBackedTexture getTexture() {
        return texture;
    }

    public void setTexture(NativeImageBackedTexture texture) {
        this.texture = texture;
    }

    public float getMinV(long tick) {
        if (frameCount <= 1) {
            return 0.0f;
        }
        int currentFrame = (int) ((tick / frameTime) % frameCount);
        return (float) currentFrame / (float) frameCount;
    }

    public float getMaxV(long tick) {
        if (frameCount <= 1) {
            return 1.0f;
        }
        int currentFrame = (int) ((tick / frameTime) % frameCount);
        return (float) (currentFrame + 1) / (float) frameCount;
    }

    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
