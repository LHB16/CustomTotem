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
    private TotemTextureTransform transform;

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
        this.transform = new TotemTextureTransform();
        if (this.frameCount <= 1) {
            this.transform.setAnimated(false);
        }
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

    public TotemTextureTransform getTransform() {
        if (transform == null) {
            transform = new TotemTextureTransform();
        }
        return transform;
    }

    public void setTransform(TotemTextureTransform transform) {
        this.transform = transform;
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
        if (!getTransform().isAnimated() || frameCount <= 1) {
            return 0.0f;
        }
        int currentFrame = (int) ((tick / frameTime) % frameCount);
        return (float) currentFrame / (float) frameCount;
    }

    public float getMaxV(long tick) {
        if (!getTransform().isAnimated() || frameCount <= 1) {
            return 1.0f;
        }
        int currentFrame = (int) ((tick / frameTime) % frameCount);
        return (float) (currentFrame + 1) / (float) frameCount;
    }

    public float getU0() {
        return getTransform().isFlipX() ? 1.0f : 0.0f;
    }

    public float getU1() {
        return getTransform().isFlipX() ? 0.0f : 1.0f;
    }

    public float getV0(long tick) {
        float minV = getMinV(tick);
        float maxV = getMaxV(tick);
        return getTransform().isFlipY() ? maxV : minV;
    }

    public float getV1(long tick) {
        float minV = getMinV(tick);
        float maxV = getMaxV(tick);
        return getTransform().isFlipY() ? minV : maxV;
    }

    /**
     * Calculates model quad bounds [x0, x1, y0, y1] in [0, 1] item space
     */
    public float[] getQuadBounds() {
        TotemTextureTransform t = getTransform();
        int frameH = (t.isAnimated() && frameCount > 1) ? height / frameCount : height;
        float aspect = (float) width / (float) Math.max(1, frameH);

        float widthScale = 1.0f;
        float heightScale = 1.0f;

        if (t.getFitMode() == TotemTextureTransform.FitMode.FIT) {
            if (aspect > 1.0f) {
                widthScale = 1.0f;
                heightScale = 1.0f / aspect;
            } else {
                widthScale = aspect;
                heightScale = 1.0f;
            }
        } else if (t.getFitMode() == TotemTextureTransform.FitMode.FILL) {
            if (aspect > 1.0f) {
                widthScale = aspect;
                heightScale = 1.0f;
            } else {
                widthScale = 1.0f;
                heightScale = 1.0f / aspect;
            }
        }

        widthScale *= t.getScale();
        heightScale *= t.getScale();

        float x0 = 0.5f - 0.5f * widthScale + t.getOffsetX();
        float x1 = 0.5f + 0.5f * widthScale + t.getOffsetX();
        float y0 = 0.5f - 0.5f * heightScale + t.getOffsetY();
        float y1 = 0.5f + 0.5f * heightScale + t.getOffsetY();

        return new float[]{x0, x1, y0, y1};
    }

    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
