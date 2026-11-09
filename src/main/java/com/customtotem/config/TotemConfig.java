package com.customtotem.config;

import com.customtotem.model.TotemDisplaySettings;
import com.customtotem.model.TotemMode;

import java.util.ArrayList;
import java.util.List;

public class TotemConfig {
    public static class TotemEntryConfig {
        public String id;
        public String fileName;
        public String displayName;
        public boolean enabled = true;
        public int frameTime = 2;

        public TotemEntryConfig() {
        }

        public TotemEntryConfig(String id, String fileName, String displayName, boolean enabled, int frameTime) {
            this.id = id;
            this.fileName = fileName;
            this.displayName = displayName;
            this.enabled = enabled;
            this.frameTime = frameTime;
        }
    }

    public TotemMode mode = TotemMode.SEQUENTIAL;
    public String currentTotemId = null;
    public List<TotemEntryConfig> entries = new ArrayList<>();
    public TotemDisplaySettings displaySettings = new TotemDisplaySettings();

    public TotemConfig() {
    }
}
