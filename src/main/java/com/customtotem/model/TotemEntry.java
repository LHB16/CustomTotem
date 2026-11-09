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
    private int columns = 1;
    private int rows = 1;
    private int frameTime;
    private TotemTextureTransform transform;

    private transient Identifier textureId;
    private transient NativeImageBackedTexture texture;

    public TotemEntry(String id, String fileName, String displayName, boolean enabled, int width, int height) {
        this(id, fileName, displayName, enabled, width, height, 1,
                (width > 0 && height >= width && height % width == 0) ? Math.max(1, height / width) : 1,
                (width > 0 && height >= width && height % width == 0) ? Math.max(1, height / width) : 1);
    }

    public TotemEntry(String id, String fileName, String displayName, boolean enabled, int width, int height, int columns, int rows, int frameCount) {
        this.id = id;
        this.fileName = fileName;
        this.displayName = displayName;
        this.enabled = enabled;
        this.width = width;
        this.height = height;
        this.columns = Math.max(1, columns);
        this.rows = Math.max(1, rows);
        this.frameCount = Math.max(1, frameCount);
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

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = Math.max(1, columns);
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = Math.max(1, rows);
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
        int r = currentFrame / Math.max(1, columns);
        return (float) r / (float) Math.max(1, rows);
    }

    public float getMaxV(long tick) {
        if (!getTransform().isAnimated() || frameCount <= 1) {
            return 1.0f;
        }
        int currentFrame = (int) ((tick / frameTime) % frameCount);
        int r = currentFrame / Math.max(1, columns);
        return (float) (r + 1) / (float) Math.max(1, rows);
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
     * - 2D Grid / Spritesheet matrix (columns x rows)
     * - Spritesheet frame animation
     * - FitMode (FIT, STRETCH, FILL)
     * - Zoom (scale)
     * - Pan (offsetX, offsetY)
     * - Flip (flipX, flipY)
     */
    public float[] getRenderUVs(long tick) {
        TotemTextureTransform t = getTransform();
        int cols = Math.max(1, columns);
        int rws = Math.max(1, rows);

        int frameW = (t.isAnimated() && frameCount > 1) ? width / cols : width;
        int frameH = (t.isAnimated() && frameCount > 1) ? height / rws : height;
        float aspect = (float) frameW / (float) Math.max(1, frameH);

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

        int currentFrame = (t.isAnimated() && frameCount > 1) ? (int) ((tick / frameTime) % frameCount) : 0;
        int col = currentFrame % cols;
        int row = currentFrame / cols;

        float frameMinU = (float) col / (float) cols;
        float frameMaxU = (float) (col + 1) / (float) cols;
        float frameMinV = (float) row / (float) rws;
        float frameMaxV = (float) (row + 1) / (float) rws;

        float frameUWidth = frameMaxU - frameMinU;
        float frameVHeight = frameMaxV - frameMinV;

        float finalU0 = frameMinU + rawU0 * frameUWidth;
        float finalU1 = frameMinU + rawU1 * frameUWidth;
        float finalV0 = frameMinV + rawV0 * frameVHeight;
        float finalV1 = frameMinV + rawV1 * frameVHeight;

        return new float[]{finalU0, finalU1, finalV0, finalV1};
    }

    /**
     * Calculates model quad bounds [x0, x1, y0, y1] strictly bounded in item space
     */
    public float[] getQuadBounds() {
        TotemTextureTransform t = getTransform();
        int cols = Math.max(1, columns);
        int rws = Math.max(1, rows);

        int frameW = (t.isAnimated() && frameCount > 1) ? width / cols : width;
        int frameH = (t.isAnimated() && frameCount > 1) ? height / rws : height;
        float aspect = (float) frameW / (float) Math.max(1, frameH);

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

    public Object getCacheKey(long tick) {
        TotemTextureTransform t = getTransform();
        int frame = (t.isAnimated() && frameCount > 1) ? (int) ((tick / frameTime) % frameCount) : 0;
        return new CacheKey(
                id,
                textureId != null ? textureId.toString() : "",
                t.getFitMode(),
                t.getScale(),
                t.getOffsetX(),
                t.getOffsetY(),
                t.isFlipX(),
                t.isFlipY(),
                frame
        );
    }

    public record CacheKey(
            String id,
            String texturePath,
            TotemTextureTransform.FitMode fitMode,
            float scale,
            float offsetX,
            float offsetY,
            boolean flipX,
            boolean flipY,
            int frame
    ) {}

    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
