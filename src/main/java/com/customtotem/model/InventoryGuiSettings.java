package com.customtotem.model;

public class InventoryGuiSettings {
    public enum Mode {
        FRAME("Khung túi đồ"),
        FULLSCREEN("Toàn màn hình");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Mode next() {
            Mode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum FitMode {
        CROP("Cắt vừa khung (Crop)"),
        STRETCH("Kéo dãn (Stretch)"),
        FIT("Vừa vặn (Fit)");

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

    private boolean enabled = false;
    private Mode mode = Mode.FRAME;
    private FitMode fitMode = FitMode.CROP;
    private String imageFileName = null;
    private float opacity = 1.0f;
    private boolean showVanillaSlots = true;
    private float scale = 1.0f;
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;

    public InventoryGuiSettings() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode != null ? mode : Mode.FRAME;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public FitMode getFitMode() {
        return fitMode != null ? fitMode : FitMode.CROP;
    }

    public void setFitMode(FitMode fitMode) {
        this.fitMode = fitMode;
    }

    public String getImageFileName() {
        return imageFileName;
    }

    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }

    public float getOpacity() {
        return Math.max(0.1f, Math.min(1.0f, opacity));
    }

    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.1f, Math.min(1.0f, opacity));
    }

    public boolean isShowVanillaSlots() {
        return showVanillaSlots;
    }

    public void setShowVanillaSlots(boolean showVanillaSlots) {
        this.showVanillaSlots = showVanillaSlots;
    }

    public float getScale() {
        return scale <= 0.01f ? 1.0f : Math.max(0.2f, Math.min(4.0f, scale));
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.2f, Math.min(4.0f, scale));
    }

    public float getOffsetX() {
        return Math.max(-1.5f, Math.min(1.5f, offsetX));
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = Math.max(-1.5f, Math.min(1.5f, offsetX));
    }

    public float getOffsetY() {
        return Math.max(-1.5f, Math.min(1.5f, offsetY));
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = Math.max(-1.5f, Math.min(1.5f, offsetY));
    }

    public void resetTransform() {
        this.scale = 1.0f;
        this.offsetX = 0.0f;
        this.offsetY = 0.0f;
        this.fitMode = FitMode.CROP;
    }
}
