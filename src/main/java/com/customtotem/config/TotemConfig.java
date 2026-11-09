package com.customtotem.config;

import com.customtotem.model.InventoryGuiSettings;
import com.customtotem.model.TotemDisplaySettings;
import com.customtotem.model.TotemMode;
import com.customtotem.model.TotemTextureTransform;

import java.util.ArrayList;
import java.util.List;

public class TotemConfig {
    public static class TotemEntryConfig {
        public String id;
        public String fileName;
        public String displayName;
        public boolean enabled = true;
        public int frameTime = 2;
        public int fps = 20;
        public int baseFps = 60;
        public int columns = 1;
        public int rows = 1;
        public int frameCount = 1;
        public TotemTextureTransform transform;

        public TotemEntryConfig() {
        }

        public TotemEntryConfig(String id, String fileName, String displayName, boolean enabled, int frameTime, TotemTextureTransform transform) {
            this(id, fileName, displayName, enabled, frameTime, 20, 60, 1, 1, 1, transform);
        }

        public TotemEntryConfig(String id, String fileName, String displayName, boolean enabled, int frameTime, int columns, int rows, int frameCount, TotemTextureTransform transform) {
            this(id, fileName, displayName, enabled, frameTime, 20, 60, columns, rows, frameCount, transform);
        }

        public TotemEntryConfig(String id, String fileName, String displayName, boolean enabled, int frameTime, int fps, int columns, int rows, int frameCount, TotemTextureTransform transform) {
            this(id, fileName, displayName, enabled, frameTime, fps, 60, columns, rows, frameCount, transform);
        }

        public TotemEntryConfig(String id, String fileName, String displayName, boolean enabled, int frameTime, int fps, int baseFps, int columns, int rows, int frameCount, TotemTextureTransform transform) {
            this.id = id;
            this.fileName = fileName;
            this.displayName = displayName;
            this.enabled = enabled;
            this.frameTime = frameTime;
            this.fps = fps > 0 ? fps : 20;
            this.baseFps = baseFps > 0 ? baseFps : 60;
            this.columns = columns;
            this.rows = rows;
            this.frameCount = frameCount;
            this.transform = transform;
        }
    }

    public TotemMode mode = TotemMode.SEQUENTIAL;
    public String currentTotemId = null;
    public String customImagesDir = null;
    public List<TotemEntryConfig> entries = new ArrayList<>();
    public TotemDisplaySettings displaySettings = new TotemDisplaySettings();
    public InventoryGuiSettings inventoryGuiSettings = new InventoryGuiSettings();
    public Boolean fullbright = null;

    public TotemConfig() {
    }
}
