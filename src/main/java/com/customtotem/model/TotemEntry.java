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
        return getRenderUVs(0)[0];
    }

    public float getU1() {
        return getRenderUVs(0)[1];
    }

    public float getV0(long tick) {
        return getRenderUVs(tick)[2];
    }

    public float getV1(long tick) {
        return getRenderUVs(tick)[3];
    }

    /**
     * Computes UV coordinates [u0, u1, v0, v1] taking into account:
     * - Spritesheet frame animation
     * - FitMode (FIT, STRETCH, FILL)
     * - Zoom (scale)
     * - Pan (offsetX, offsetY)
     * - Flip (flipX, flipY)
     */
    public float[] getRenderUVs(long tick) {
        TotemTextureTransform t = getTransform();
        int frameH = (t.isAnimated() && frameCount > 1) ? height / frameCount : height;
        float aspect = (float) width / (float) Math.max(1, frameH);

        float scale = t.getScale();
        float uSpan = 1.0f;
        float vSpan = 1.0f;

        if (t.getFitMode() == TotemTextureTransform.FitMode.FILL) {
            if (aspect > 1.0f) {
                uSpan = 1.0f / aspect;
                vSpan = 1.0f;
            } else if (aspect < 1.0f) {
                uSpan = 1.0f;
                vSpan = aspect;
            }
        }

        if (scale > 1.0f) {
            uSpan /= scale;
            vSpan /= scale;
        }

        float uCenter = 0.5f - t.getOffsetX() * uSpan;
        float vCenter = 0.5f + t.getOffsetY() * vSpan;

        float rawU0 = uCenter - 0.5f * uSpan;
        float rawU1 = uCenter + 0.5f * uSpan;
        float rawV0 = vCenter - 0.5f * vSpan;
        float rawV1 = vCenter + 0.5f * vSpan;

        if (t.isFlipX()) {
            float temp = rawU0;
            rawU0 = rawU1;
            rawU1 = temp;
        }
        if (t.isFlipY()) {
            float temp = rawV0;
            rawV0 = rawV1;
            rawV1 = temp;
        }

        float frameMinV = getMinV(tick);
        float frameHeight = getMaxV(tick) - frameMinV;

        float finalV0 = frameMinV + rawV0 * frameHeight;
        float finalV1 = frameMinV + rawV1 * frameHeight;

        return new float[]{rawU0, rawU1, finalV0, finalV1};
    }

    /**
     * Calculates model quad bounds [x0, x1, y0, y1] strictly bounded in item space
     */
    public float[] getQuadBounds() {
        TotemTextureTransform t = getTransform();
        int frameH = (t.isAnimated() && frameCount > 1) ? height / frameCount : height;
        float aspect = (float) width / (float) Math.max(1, frameH);

        float baseW = 1.0f;
        float baseH = 1.0f;

        if (t.getFitMode() == TotemTextureTransform.FitMode.FIT) {
            if (aspect > 1.0f) {
                baseW = 1.0f;
                baseH = 1.0f / aspect;
            } else if (aspect < 1.0f) {
                baseW = aspect;
                baseH = 1.0f;
            }
        }

        float scale = t.getScale();
        float finalW = baseW;
        float finalH = baseH;

        float xOffset = 0.0f;
        float yOffset = 0.0f;

        if (scale <= 1.0f) {
            finalW *= scale;
            finalH *= scale;
            xOffset = t.getOffsetX();
            yOffset = t.getOffsetY();
        }

        float x0 = 0.5f - 0.5f * finalW + xOffset;
        float x1 = 0.5f + 0.5f * finalW + xOffset;
        float y0 = 0.5f - 0.5f * finalH + yOffset;
        float y1 = 0.5f + 0.5f * finalH + yOffset;

        return new float[]{x0, x1, y0, y1};
    }

    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
