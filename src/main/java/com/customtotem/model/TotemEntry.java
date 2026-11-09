package com.customtotem.model;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
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
    private int frameTime = 2; // Default 2 ticks per frame (10 FPS)
    private int fps = 20;      // Target playback FPS (10, 15, 20, 30, 45, 60...)
    private int baseFps = 60;  // Timeline Base FPS (e.g. 60 FPS)
    private TotemTextureTransform transform;

    private transient Identifier textureId;
    private transient NativeImageBackedTexture texture;
    private transient boolean textureRegistered = false;
    private transient RenderLayer cachedRenderLayer = null;

    // High performance precomputed cache to ensure 0 GC allocations per frame
    private transient float[][] precomputedFrameUVs = null;
    private transient float[] cachedQuadBounds = null;
    private transient CacheKey[] cachedKeys = null;

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
        this.frameTime = 2;
        this.fps = 20;
        this.baseFps = 60;
        this.transform = new TotemTextureTransform();
        this.transform.setChangeListener(this::invalidatePrecomputedCache);
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
        invalidatePrecomputedCache();
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = Math.max(1, rows);
        invalidatePrecomputedCache();
    }

    public int getFrameTime() {
        return frameTime;
    }

    public void setFrameTime(int frameTime) {
        this.frameTime = Math.max(1, frameTime);
        if (this.fps <= 0) {
            this.fps = Math.max(1, Math.round(20.0f / (float) this.frameTime));
        }
        invalidatePrecomputedCache();
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = Math.max(1, fps);
        this.frameTime = Math.max(1, Math.round(20.0f / (float) this.fps));
        invalidatePrecomputedCache();
    }

    public int getBaseFps() {
        return baseFps > 0 ? baseFps : 60;
    }

    public void setBaseFps(int baseFps) {
        this.baseFps = Math.max(1, baseFps);
        invalidatePrecomputedCache();
    }

    public TotemTextureTransform getTransform() {
        if (transform == null) {
            transform = new TotemTextureTransform();
        }
        transform.setChangeListener(this::invalidatePrecomputedCache);
        return transform;
    }

    public void setTransform(TotemTextureTransform transform) {
        this.transform = transform;
        if (this.transform != null) {
            this.transform.setChangeListener(this::invalidatePrecomputedCache);
        }
        invalidatePrecomputedCache();
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public void setTextureId(Identifier textureId) {
        this.textureId = textureId;
        this.cachedRenderLayer = null;
        this.cachedKeys = null;
    }

    public RenderLayer getRenderLayer() {
        if (cachedRenderLayer == null && textureId != null) {
            cachedRenderLayer = RenderLayers.entityTranslucent(textureId);
        }
        return cachedRenderLayer;
    }

    public boolean isTextureRegistered() {
        return textureRegistered;
    }

    public void setTextureRegistered(boolean textureRegistered) {
        this.textureRegistered = textureRegistered;
    }

    public NativeImageBackedTexture getTexture() {
        return texture;
    }

    public void setTexture(NativeImageBackedTexture texture) {
        this.texture = texture;
    }

    public void invalidatePrecomputedCache() {
        this.precomputedFrameUVs = null;
        this.cachedQuadBounds = null;
        this.cachedKeys = null;
    }

    public int getCurrentFrame(long timeOrTick) {
        if (!getTransform().isAnimated() || frameCount <= 1) {
            return 0;
        }
        int effectiveBaseFps = getBaseFps();
        int displayFps = (fps > 0) ? fps : effectiveBaseFps;

        if (displayFps >= effectiveBaseFps) {
            // Smooth playback at base FPS
            long msPerFrame = Math.max(1, 1000 / effectiveBaseFps);
            return (int) ((timeOrTick / msPerFrame) % frameCount);
        } else {
            // Stepped Frame Skipping: Quantize time to displayFps chunks and sample from baseFps timeline
            long intervalMs = Math.max(1, 1000 / displayFps);
            long quantizedTimeMs = (timeOrTick / intervalMs) * intervalMs;
            long baseFrame = (quantizedTimeMs * effectiveBaseFps) / 1000L;
            return (int) ((baseFrame % frameCount + frameCount) % frameCount);
        }
    }

    public float getEstimatedVramMb() {
        TotemTextureTransform t = getTransform();
        int res = t != null ? t.getResolution() : 0;
        return calculateVramMb(res);
    }

    public float calculateVramMb(int targetRes) {
        long totalW = width;
        long totalH = height;
        if (targetRes > 0 && width > 0 && height > 0) {
            int safeCols = Math.max(1, columns);
            int safeRows = Math.max(1, rows);
            int origFrameW = width / safeCols;
            int origFrameH = height / safeRows;
            int targetFrameW = targetRes;
            int targetFrameH = (int) Math.max(1, Math.round((float) targetRes * (float) origFrameH / (float) Math.max(1, origFrameW)));
            totalW = (long) targetFrameW * safeCols;
            totalH = (long) targetFrameH * safeRows;
        }
        long bytes = totalW * totalH * 4L;
        return (float) bytes / (1024.0f * 1024.0f);
    }

    public boolean isVramHigh() {
        return getEstimatedVramMb() > 16.0f || width > 4096 || height > 4096;
    }

    public boolean isVramCritical() {
        return getEstimatedVramMb() > 32.0f || width > 8192 || height > 8192;
    }

    public String getVramBadge() {
        float mb = getEstimatedVramMb();
        if (isVramCritical()) {
            return String.format("§c⚠ %.1fMB VRAM", mb);
        } else if (isVramHigh()) {
            return String.format("§e⚠ %.1fMB VRAM", mb);
        } else {
            return String.format("§a%.1fMB", mb);
        }
    }

    public float getMinV(long tick) {
        return getRenderUVs(tick)[2];
    }

    public float getMaxV(long tick) {
        return getRenderUVs(tick)[3];
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
     * Returns precomputed UVs [u0, u1, v0, v1] with ZERO heap allocations.
     */
    public float[] getRenderUVs(long timeOrTick) {
        ensurePrecomputedCache();
        int frame = getCurrentFrame(timeOrTick);
        return precomputedFrameUVs[frame];
    }

    /**
     * Calculates model quad bounds [x0, x1, y0, y1] with ZERO heap allocations during render.
     */
    public float[] getQuadBounds() {
        ensurePrecomputedCache();
        return cachedQuadBounds;
    }

    private synchronized void ensurePrecomputedCache() {
        if (precomputedFrameUVs == null || cachedQuadBounds == null) {
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

            // Precompute UVs for all frames
            precomputedFrameUVs = new float[frameCount][4];
            for (int f = 0; f < frameCount; f++) {
                int col = (t.isAnimated() && frameCount > 1) ? (f % cols) : 0;
                int row = (t.isAnimated() && frameCount > 1) ? (f / cols) : 0;

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

                precomputedFrameUVs[f][0] = finalU0;
                precomputedFrameUVs[f][1] = finalU1;
                precomputedFrameUVs[f][2] = finalV0;
                precomputedFrameUVs[f][3] = finalV1;
            }

            // Precompute Quad Bounds
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

            cachedQuadBounds = new float[]{x0, x1, y0, y1};
        }
    }

    public Object getCacheKey(long timeOrTick) {
        int frame = getCurrentFrame(timeOrTick);
        if (cachedKeys == null || cachedKeys.length != frameCount) {
            cachedKeys = new CacheKey[frameCount];
        }
        if (cachedKeys[frame] == null) {
            TotemTextureTransform t = getTransform();
            cachedKeys[frame] = new CacheKey(
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
        return cachedKeys[frame];
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
        this.textureRegistered = false;
        this.cachedRenderLayer = null;
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
