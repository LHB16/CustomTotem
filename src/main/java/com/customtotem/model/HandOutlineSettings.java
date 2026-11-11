package com.customtotem.model;

public class HandOutlineSettings {
    public enum OutlineColorMode {
        STATIC("Màu Tĩnh (Static)", 0),
        RAINBOW_UNIFIED("Cầu Vồng Đồng Bộ (Rainbow All)", 1),
        RAINBOW_SPIRAL("Xoắn Cầu Vồng (Rainbow Spiral)", 2),
        RAINBOW_WAVE("Sóng Cầu Vồng (Rainbow Wave)", 3);

        private final String displayName;
        private final int modeId;

        OutlineColorMode(String displayName, int modeId) {
            this.displayName = displayName;
            this.modeId = modeId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getModeId() {
            return modeId;
        }
    }

    private boolean enabled = false;
    private boolean totemOnly = false;
    private OutlineColorMode mode = OutlineColorMode.RAINBOW_SPIRAL;
    private int staticColor = 0xFF00FFCC; // Cyan/Aqua default
    private float speed = 1.0f;           // Speed multiplier (0.1 - 5.0)
    private float width = 2.5f;           // Reach in pixels (1.0 - 6.0)
    private float auraIntensity = 0.5f;   // Halo blur alpha (0.0 - 1.0)
    private float sheen = 0.35f;          // Glint shine intensity (0.0 - 1.0)
    private float outlineStrength = 1.0f; // Rim opacity (0.1 - 1.0)

    public HandOutlineSettings() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTotemOnly() {
        return totemOnly;
    }

    public void setTotemOnly(boolean totemOnly) {
        this.totemOnly = totemOnly;
    }

    public OutlineColorMode getMode() {
        return mode != null ? mode : OutlineColorMode.RAINBOW_SPIRAL;
    }

    public void setMode(OutlineColorMode mode) {
        this.mode = mode;
    }

    public int getStaticColor() {
        return staticColor;
    }

    public void setStaticColor(int staticColor) {
        this.staticColor = staticColor;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.1f, Math.min(5.0f, speed));
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = Math.max(0.5f, Math.min(8.0f, width));
    }

    public float getAuraIntensity() {
        return auraIntensity;
    }

    public void setAuraIntensity(float auraIntensity) {
        this.auraIntensity = Math.max(0.0f, Math.min(1.0f, auraIntensity));
    }

    public float getSheen() {
        return sheen;
    }

    public void setSheen(float sheen) {
        this.sheen = Math.max(0.0f, Math.min(1.0f, sheen));
    }

    public float getOutlineStrength() {
        return outlineStrength;
    }

    public void setOutlineStrength(float outlineStrength) {
        this.outlineStrength = Math.max(0.0f, Math.min(1.0f, outlineStrength));
    }
}
