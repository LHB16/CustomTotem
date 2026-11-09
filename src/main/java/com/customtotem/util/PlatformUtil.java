package com.customtotem.util;

import java.util.Locale;

public class PlatformUtil {
    private static final boolean IS_ANDROID = checkAndroid();

    private static boolean checkAndroid() {
        String osVersion = System.getProperty("os.version", "").toLowerCase(Locale.ROOT);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osVersion.contains("android")
                || System.getProperty("pojav.path.minecraft") != null
                || System.getenv("POJAV_RENDERER") != null
                || System.getenv("ZALITH_VERSION_CODE") != null;
    }

    public static boolean isAndroid() {
        return IS_ANDROID;
    }
}
