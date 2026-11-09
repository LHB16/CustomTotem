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

    private boolean enabled = false;
    private Mode mode = Mode.FRAME;
    private String imageFileName = null;
    private float opacity = 1.0f;
    private boolean showVanillaSlots = true;

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
}
