package com.customtotem.model;

public class TotemTextureTransform {
    public enum FitMode {
        FIT("Fit (Keep Ratio)"),
        STRETCH("Stretch (1:1)"),
        FILL("Fill (Crop)");

        private final String displayName;

        FitMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public FitMode next() {
            FitMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private FitMode fitMode = FitMode.FIT;
    private float scale = 1.0f;
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;
    private boolean flipX = false;
    private boolean flipY = false;
    private boolean animated = true;
    private transient Runnable changeListener = null;

    public TotemTextureTransform() {
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    private void markDirty() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public FitMode getFitMode() {
        return fitMode;
    }

    public void setFitMode(FitMode fitMode) {
        this.fitMode = fitMode;
        markDirty();
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.1f, Math.min(3.0f, scale));
        markDirty();
    }

    public float getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = Math.max(-1.5f, Math.min(1.5f, offsetX));
        markDirty();
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = Math.max(-1.5f, Math.min(1.5f, offsetY));
        markDirty();
    }

    public boolean isFlipX() {
        return flipX;
    }

    public void setFlipX(boolean flipX) {
        this.flipX = flipX;
        markDirty();
    }

    public boolean isFlipY() {
        return flipY;
    }

    public void setFlipY(boolean flipY) {
        this.flipY = flipY;
        markDirty();
    }

    public boolean isAnimated() {
        return animated;
    }

    public void setAnimated(boolean animated) {
        this.animated = animated;
        markDirty();
    }

    public void reset() {
        this.fitMode = FitMode.FIT;
        this.scale = 1.0f;
        this.offsetX = 0.0f;
        this.offsetY = 0.0f;
        this.flipX = false;
        this.flipY = false;
        markDirty();
    }
}
