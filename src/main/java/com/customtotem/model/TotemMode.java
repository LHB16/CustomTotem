package com.customtotem.model;

public enum TotemMode {
    SINGLE("SINGLE"),
    SEQUENTIAL("SEQUENTIAL"),
    RANDOM("RANDOM");

    private final String displayName;

    TotemMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TotemMode next() {
        TotemMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
