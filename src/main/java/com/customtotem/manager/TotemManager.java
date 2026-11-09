package com.customtotem.manager;

import com.customtotem.config.TotemConfig;
import com.customtotem.model.InventoryGuiSettings;
import com.customtotem.model.TotemDisplaySettings;
import com.customtotem.model.TotemEntry;
import com.customtotem.model.TotemMode;
import com.customtotem.model.TotemTextureTransform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class TotemManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotemMod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TotemManager INSTANCE = new TotemManager();

    private static final ExecutorService ASYNC_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CustomTotem-AsyncLoader");
        t.setDaemon(true);
        return t;
    });

    private final Path baseDir;
    private final Path defaultImagesDir;
    private final Path inventoryGuiDir;
    private Path customImagesDir = null;
    private final Path configFile;

    private final List<TotemEntry> entries = new ArrayList<>();
    private final List<TotemEntry> activeEntries = new ArrayList<>();
    private final List<TotemEntry> activeEntriesView = Collections.unmodifiableList(activeEntries);
    private volatile boolean hasActiveCustomTotem = false;

    private TotemEntry currentActiveTotem = null;
    private TotemEntry poppingTotem = null;
    private TotemMode mode = TotemMode.SEQUENTIAL;
    private TotemDisplaySettings displaySettings = new TotemDisplaySettings();
    private InventoryGuiSettings inventoryGuiSettings = new InventoryGuiSettings();
    private Identifier inventoryGuiTextureId = null;
    private NativeImageBackedTexture inventoryGuiTexture = null;
    private final List<String> availableInventoryImages = new ArrayList<>();

    private boolean renderingFloatingItem = false;
    private long clientTicks = 0;
    private final Random random = new Random();

    // Async Loading State & Progress Telemetry
    private volatile boolean isReloading = false;
    private volatile int reloadTotal = 0;
    private volatile int reloadCurrent = 0;
    private volatile String reloadStatus = "";

    private record PreloadedTotem(
            Path file,
            NativeImage image,
            int width,
            int height,
            String uniqueId,
            String fileName,
            String displayName,
            boolean enabled,
            int cols,
            int rows,
            int frameCount,
            int frameTime,
            int fps,
            int baseFps,
            TotemTextureTransform transform
    ) {}

    private TotemManager() {
        this.baseDir = FabricLoader.getInstance().getConfigDir().resolve("custom_totems");
        this.defaultImagesDir = baseDir.resolve("images");
        this.inventoryGuiDir = baseDir.resolve("inventory");
        this.configFile = baseDir.resolve("config.json");
    }

    public static TotemManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        try {
            if (!Files.exists(defaultImagesDir)) {
                Files.createDirectories(defaultImagesDir);
            }
            if (!Files.exists(inventoryGuiDir)) {
                Files.createDirectories(inventoryGuiDir);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create default custom totem directories", e);
        }

        TotemConfig loadedConfig = loadConfigFile();
        if (loadedConfig != null) {
            this.mode = loadedConfig.mode != null ? loadedConfig.mode : TotemMode.SEQUENTIAL;
            if (loadedConfig.displaySettings != null) {
                this.displaySettings = loadedConfig.displaySettings;
            }
            if (loadedConfig.inventoryGuiSettings != null) {
                this.inventoryGuiSettings = loadedConfig.inventoryGuiSettings;
            }
            if (loadedConfig.fullbright != null) {
                this.displaySettings.setFullbright(loadedConfig.fullbright);
            }
            if (loadedConfig.customImagesDir != null && !loadedConfig.customImagesDir.trim().isEmpty()) {
                try {
                    Path customPath = Path.of(loadedConfig.customImagesDir);
                    if (Files.exists(customPath) && Files.isDirectory(customPath)) {
                        this.customImagesDir = customPath.toAbsolutePath().normalize();
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to resolve custom images dir: {}", loadedConfig.customImagesDir, e);
                }
            }
        }

        scanInventoryImages();
        loadInventoryGuiTexture();
        reloadAllTexturesAsync(loadedConfig, null);
    }

    public TotemConfig loadConfigFile() {
        if (!Files.exists(configFile)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(configFile)) {
            return GSON.fromJson(reader, TotemConfig.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load custom totem config", e);
            return null;
        }
    }

    public synchronized void saveConfig() {
        TotemConfig config = new TotemConfig();
        config.mode = this.mode;
        config.currentTotemId = this.currentActiveTotem != null ? this.currentActiveTotem.getId() : null;
        config.customImagesDir = this.customImagesDir != null ? this.customImagesDir.toAbsolutePath().toString() : null;
        config.displaySettings = this.displaySettings;
        config.inventoryGuiSettings = this.inventoryGuiSettings;
        config.fullbright = this.displaySettings != null ? this.displaySettings.isFullbright() : true;

        for (TotemEntry entry : entries) {
            config.entries.add(new TotemConfig.TotemEntryConfig(
                    entry.getId(),
                    entry.getFileName(),
                    entry.getDisplayName(),
                    entry.isEnabled(),
                    entry.getFrameTime(),
                    entry.getFps(),
                    entry.getBaseFps(),
                    entry.getColumns(),
                    entry.getRows(),
                    entry.getFrameCount(),
                    entry.getTransform()
            ));
        }

        try {
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save custom totem config", e);
        }
    }

    private synchronized void updateActiveCache() {
        this.activeEntries.clear();
        for (TotemEntry entry : this.entries) {
            if (entry.isEnabled()) {
                this.activeEntries.add(entry);
            }
        }
        this.hasActiveCustomTotem = !this.activeEntries.isEmpty()
                && this.currentActiveTotem != null
                && this.currentActiveTotem.isEnabled()
                && this.currentActiveTotem.getTextureId() != null;
    }

    public boolean isReloading() {
        return isReloading;
    }

    public int getReloadTotal() {
        return reloadTotal;
    }

    public int getReloadCurrent() {
        return reloadCurrent;
    }

    public String getReloadStatus() {
        return reloadStatus;
    }

    public void reloadAllTextures(TotemConfig loadedConfig) {
        reloadAllTexturesAsync(loadedConfig, null);
    }

    public void rescanImages() {
        reloadAllTexturesAsync(loadConfigFile(), null);
    }

    public void rescanImages(Runnable onComplete) {
        reloadAllTexturesAsync(loadConfigFile(), onComplete);
    }

    public void reloadAllTexturesAsync(TotemConfig loadedConfig, Runnable onComplete) {
        isReloading = true;
        reloadTotal = 0;
        reloadCurrent = 0;
        reloadStatus = "Đang quét thư mục ảnh...";

        Path activeDir = getImagesDir();

        ASYNC_POOL.submit(() -> {
            try {
                if (!Files.exists(activeDir)) {
                    Files.createDirectories(activeDir);
                }

                Map<String, TotemConfig.TotemEntryConfig> configMap = new HashMap<>();
                List<String> orderList = new ArrayList<>();
                if (loadedConfig != null && loadedConfig.entries != null) {
                    for (TotemConfig.TotemEntryConfig ec : loadedConfig.entries) {
                        configMap.put(ec.fileName, ec);
                        orderList.add(ec.fileName);
                    }
                }

                List<Path> imageFiles = new ArrayList<>();
                try (Stream<Path> stream = Files.list(activeDir)) {
                    stream.filter(p -> {
                        String name = p.getFileName().toString();
                        if (name.startsWith(".") || name.startsWith(".trashed")) {
                            return false;
                        }
                        return name.toLowerCase(Locale.ROOT).endsWith(".png");
                    }).forEach(imageFiles::add);
                }

                imageFiles.sort((p1, p2) -> {
                    String name1 = p1.getFileName().toString();
                    String name2 = p2.getFileName().toString();
                    int idx1 = orderList.indexOf(name1);
                    int idx2 = orderList.indexOf(name2);
                    if (idx1 != -1 && idx2 != -1) return Integer.compare(idx1, idx2);
                    if (idx1 != -1) return -1;
                    if (idx2 != -1) return 1;
                    return name1.compareToIgnoreCase(name2);
                });

                reloadTotal = imageFiles.size();
                reloadStatus = "Đang nạp và giải mã ảnh (0/" + reloadTotal + ")...";

                List<PreloadedTotem> preloadedList = new ArrayList<>();
                for (Path file : imageFiles) {
                    try {
                        String fileName = file.getFileName().toString();
                        TotemConfig.TotemEntryConfig configEntry = configMap.get(fileName);

                        String idName = fileName.replace(".png", "")
                                .replaceAll("[^a-zA-Z0-9_-]", "_")
                                .replaceAll("^_+", "")
                                .toLowerCase(Locale.ROOT);
                        if (idName.isEmpty()) idName = "totem";

                        String uniqueId = (configEntry != null && configEntry.id != null)
                                ? configEntry.id.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("^_+", "").toLowerCase(Locale.ROOT)
                                : idName + "_" + System.currentTimeMillis();

                        NativeImage image;
                        try (InputStream in = Files.newInputStream(file)) {
                            image = NativeImage.read(in);
                        }

                        int width = image.getWidth();
                        int height = image.getHeight();
                        boolean enabled = configEntry == null || configEntry.enabled;
                        String displayName = configEntry != null && configEntry.displayName != null ? configEntry.displayName : fileName;

                        int cols = 1;
                        int rows = 1;
                        int frameCount = 1;
                        int frameTime = 2;
                        int fps = 20;
                        int baseFps = 60;

                        if (configEntry != null && configEntry.columns > 0 && configEntry.rows > 0 && configEntry.frameCount > 0) {
                            cols = configEntry.columns;
                            rows = configEntry.rows;
                            frameCount = configEntry.frameCount;
                            if (configEntry.fps > 0) {
                                fps = configEntry.fps;
                            }
                            if (configEntry.frameTime > 0) {
                                frameTime = configEntry.frameTime;
                            }
                            if (configEntry.baseFps > 0) {
                                baseFps = configEntry.baseFps;
                            }
                        } else {
                            // Detect grid format e.g. _grid8x8_64f
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("_grid(\\d+)x(\\d+)(?:_(\\d+)f)?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(fileName);
                            if (m.find()) {
                                try {
                                    cols = Integer.parseInt(m.group(1));
                                    rows = Integer.parseInt(m.group(2));
                                    if (m.group(3) != null) {
                                        frameCount = Integer.parseInt(m.group(3));
                                    } else {
                                        frameCount = cols * rows;
                                    }
                                } catch (Exception ignored) {}
                            }

                            // Detect FPS from filename e.g. _30fps, _60fps
                            java.util.regex.Matcher fpsMatcher = java.util.regex.Pattern.compile("(?i)_(\\d+)fps").matcher(fileName);
                            if (fpsMatcher.find()) {
                                try {
                                    int matched = Integer.parseInt(fpsMatcher.group(1));
                                    fps = matched;
                                    baseFps = matched;
                                } catch (Exception ignored) {}
                            }

                            Path metaFile = file.resolveSibling(fileName + ".mcmeta");
                            if (!Files.exists(metaFile)) {
                                String base = fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
                                metaFile = file.resolveSibling(base + ".json");
                            }
                            if (Files.exists(metaFile)) {
                                try (Reader reader = Files.newBufferedReader(metaFile)) {
                                    com.google.gson.JsonObject obj = GSON.fromJson(reader, com.google.gson.JsonObject.class);
                                    if (obj != null) {
                                        if (obj.has("columns") && obj.has("rows")) {
                                            cols = obj.get("columns").getAsInt();
                                            rows = obj.get("rows").getAsInt();
                                            frameCount = obj.has("frameCount") ? obj.get("frameCount").getAsInt() : cols * rows;
                                        }
                                        if (obj.has("fps")) {
                                            fps = obj.get("fps").getAsInt();
                                            baseFps = fps;
                                        }
                                        if (obj.has("baseFps")) {
                                            baseFps = obj.get("baseFps").getAsInt();
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }

                            if (cols == 1 && rows == 1 && frameCount == 1) {
                                if (width > 0 && height >= width && height % width == 0) {
                                    frameCount = Math.max(1, height / width);
                                    rows = frameCount;
                                    cols = 1;
                                }
                            }
                        }

                        if (frameCount >= 45 && baseFps < 60) {
                            baseFps = 60;
                        }

                        if (displayName.equals(fileName)) {
                            String clean = displayName.replace(".png", "")
                                    .replaceAll("(?i)_grid\\d+x\\d+(?:_\\d+f)?", "")
                                    .replaceAll("(?i)_\\d+fps", "")
                                    .replaceAll("^_+|_+$", "");
                            if (!clean.isEmpty()) {
                                displayName = clean;
                            }
                        }

                        TotemTextureTransform transform = (configEntry != null && configEntry.transform != null)
                                ? configEntry.transform : null;

                        if (transform != null && transform.getResolution() > 0) {
                            image = createResampledImage(image, cols, rows, transform.getResolution());
                        }

                        preloadedList.add(new PreloadedTotem(
                                file, image, width, height, uniqueId, fileName, displayName,
                                enabled, cols, rows, frameCount, frameTime, fps, baseFps, transform
                        ));
                    } catch (Exception e) {
                        LOGGER.error("Failed to decode totem image file: {}", file, e);
                    }
                    reloadCurrent++;
                    reloadStatus = "Đang nạp và giải mã ảnh (" + reloadCurrent + "/" + reloadTotal + ")...";
                }

                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.execute(() -> {
                        synchronized (this) {
                            for (TotemEntry entry : entries) {
                                if (entry.getTextureId() != null && client.getTextureManager() != null) {
                                    client.getTextureManager().destroyTexture(entry.getTextureId());
                                }
                                entry.close();
                            }
                            entries.clear();

                            for (PreloadedTotem p : preloadedList) {
                                Identifier textureId = Identifier.of("customtotem", "textures/" + p.uniqueId);
                                NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/" + p.uniqueId, p.image);
                                if (client.getTextureManager() != null) {
                                    client.getTextureManager().registerTexture(textureId, texture);
                                }

                                TotemEntry entry = new TotemEntry(p.uniqueId, p.fileName, p.displayName, p.enabled, p.width, p.height, p.cols, p.rows, p.frameCount);
                                entry.setBaseFps(p.baseFps);
                                if (p.fps > 0) {
                                    entry.setFps(p.fps);
                                } else if (p.frameTime > 0) {
                                    entry.setFrameTime(p.frameTime);
                                }
                                if (p.transform != null) {
                                    entry.setTransform(p.transform);
                                }
                                entry.setTextureId(textureId);
                                entry.setTexture(texture);
                                entry.setTextureRegistered(true);

                                entries.add(entry);
                            }

                            // Restore active totem
                            if (loadedConfig != null && loadedConfig.currentTotemId != null) {
                                for (TotemEntry entry : entries) {
                                    if (entry.getId().equals(loadedConfig.currentTotemId)) {
                                        this.currentActiveTotem = entry;
                                        break;
                                    }
                                }
                            }

                            if (this.currentActiveTotem == null || !this.currentActiveTotem.isEnabled()) {
                                this.currentActiveTotem = entries.stream().filter(TotemEntry::isEnabled).findFirst().orElse(null);
                            }

                            this.poppingTotem = this.currentActiveTotem;
                            updateActiveCache();
                            isReloading = false;
                            reloadStatus = "Đã nạp xong " + entries.size() + " Totem!";
                            saveConfig();

                            if (onComplete != null) {
                                onComplete.run();
                            }
                        }
                    });
                } else {
                    isReloading = false;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to complete async totem reload", e);
                isReloading = false;
                reloadStatus = "Lỗi khi nạp ảnh: " + e.getMessage();
            }
        });
    }

    public static NativeImage createResampledImage(NativeImage originalImage, int cols, int rows, int targetRes) {
        if (targetRes <= 0 || originalImage == null) {
            return originalImage;
        }
        int origW = originalImage.getWidth();
        int origH = originalImage.getHeight();
        int safeCols = Math.max(1, cols);
        int safeRows = Math.max(1, rows);

        int origFrameW = origW / safeCols;
        int origFrameH = origH / safeRows;

        int targetFrameW = targetRes;
        int targetFrameH = (int) Math.max(1, Math.round((float) targetRes * (float) origFrameH / (float) Math.max(1, origFrameW)));

        int newTotalW = targetFrameW * safeCols;
        int newTotalH = targetFrameH * safeRows;

        if (newTotalW == origW && newTotalH == origH) {
            return originalImage;
        }

        NativeImage targetImage = new NativeImage(originalImage.getFormat(), newTotalW, newTotalH, false);
        originalImage.resizeSubRectTo(0, 0, origW, origH, targetImage);
        originalImage.close();
        return targetImage;
    }

    public void applyResolution(TotemEntry entry) {
        if (entry == null || entry.getTextureId() == null) {
            return;
        }
        Path file = getImagesDir().resolve(entry.getFileName());
        if (!Files.exists(file)) {
            return;
        }

        try (InputStream in = Files.newInputStream(file)) {
            NativeImage originalImage = NativeImage.read(in);
            int targetRes = entry.getTransform().getResolution();
            NativeImage finalImage = createResampledImage(originalImage, entry.getColumns(), entry.getRows(), targetRes);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null) {
                NativeImageBackedTexture newTexture = new NativeImageBackedTexture(() -> "customtotem/" + entry.getId(), finalImage);
                client.getTextureManager().registerTexture(entry.getTextureId(), newTexture);
                entry.setTexture(newTexture);
                entry.setTextureRegistered(true);
            }
            entry.invalidatePrecomputedCache();
        } catch (Exception e) {
            LOGGER.error("Failed to apply resolution to totem: {}", entry.getFileName(), e);
        }
    }

    public void ensureTextureRegistered(TotemEntry entry) {
        if (entry == null || entry.getTextureId() == null || entry.isTextureRegistered()) {
            return;
        }
        synchronized (this) {
            if (entry.isTextureRegistered()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getTextureManager() == null) return;

            if (client.getTextureManager().getTexture(entry.getTextureId()) == null) {
                Path file = getImagesDir().resolve(entry.getFileName());
                if (Files.exists(file)) {
                    try (InputStream in = Files.newInputStream(file)) {
                        NativeImage image = NativeImage.read(in);
                        if (entry.getTransform().getResolution() > 0) {
                            image = createResampledImage(image, entry.getColumns(), entry.getRows(), entry.getTransform().getResolution());
                        }
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/" + entry.getId(), image);
                        client.getTextureManager().registerTexture(entry.getTextureId(), texture);
                        entry.setTexture(texture);
                    } catch (Exception e) {
                        LOGGER.error("Failed to restore texture for entry: {}", entry.getFileName(), e);
                    }
                }
            }
            entry.setTextureRegistered(true);
        }
    }

    private TotemEntry loadSingleImage(Path file, TotemConfig.TotemEntryConfig configEntry) {
        String fileName = file.getFileName().toString();
        if (fileName.startsWith(".") || fileName.startsWith(".trashed")) {
            return null;
        }

        String idName = fileName.replace(".png", "")
                .replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("^_+", "")
                .toLowerCase(Locale.ROOT);
        if (idName.isEmpty()) idName = "totem";

        String uniqueId = (configEntry != null && configEntry.id != null)
                ? configEntry.id.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("^_+", "").toLowerCase(Locale.ROOT)
                : idName + "_" + System.currentTimeMillis();

        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            int width = image.getWidth();
            int height = image.getHeight();

            Identifier textureId = Identifier.of("customtotem", "textures/" + uniqueId);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/" + uniqueId, image);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null) {
                client.getTextureManager().registerTexture(textureId, texture);
            }

            boolean enabled = configEntry == null || configEntry.enabled;
            String displayName = configEntry != null && configEntry.displayName != null ? configEntry.displayName : fileName;

            int cols = 1;
            int rows = 1;
            int frameCount = 1;
            int fps = 20;
            int baseFps = (configEntry != null && configEntry.baseFps > 0) ? configEntry.baseFps : 60;

            if (configEntry != null && configEntry.columns > 0 && configEntry.rows > 0 && configEntry.frameCount > 0) {
                cols = configEntry.columns;
                rows = configEntry.rows;
                frameCount = configEntry.frameCount;
                if (configEntry.fps > 0) {
                    fps = configEntry.fps;
                }
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("_grid(\\d+)x(\\d+)(?:_(\\d+)f)?(?:\\.png)?$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(fileName);
                if (m.find()) {
                    try {
                        cols = Integer.parseInt(m.group(1));
                        rows = Integer.parseInt(m.group(2));
                        if (m.group(3) != null) {
                            frameCount = Integer.parseInt(m.group(3));
                        } else {
                            frameCount = cols * rows;
                        }
                    } catch (Exception ignored) {}
                }

                java.util.regex.Matcher fpsMatcher = java.util.regex.Pattern.compile("(?i)_(\\d+)fps").matcher(fileName);
                if (fpsMatcher.find()) {
                    try {
                        int matched = Integer.parseInt(fpsMatcher.group(1));
                        fps = matched;
                        baseFps = matched;
                    } catch (Exception ignored) {}
                }

                Path metaFile = file.resolveSibling(fileName + ".mcmeta");
                if (!Files.exists(metaFile)) {
                    String base = fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
                    metaFile = file.resolveSibling(base + ".json");
                }
                if (Files.exists(metaFile)) {
                    try (Reader reader = Files.newBufferedReader(metaFile)) {
                        com.google.gson.JsonObject obj = GSON.fromJson(reader, com.google.gson.JsonObject.class);
                        if (obj != null) {
                            if (obj.has("columns") && obj.has("rows")) {
                                cols = obj.get("columns").getAsInt();
                                rows = obj.get("rows").getAsInt();
                                frameCount = obj.has("frameCount") ? obj.get("frameCount").getAsInt() : cols * rows;
                            }
                            if (obj.has("fps")) {
                                fps = obj.get("fps").getAsInt();
                                baseFps = fps;
                            }
                            if (obj.has("baseFps")) {
                                baseFps = obj.get("baseFps").getAsInt();
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (cols == 1 && rows == 1 && frameCount == 1) {
                    if (width > 0 && height >= width && height % width == 0) {
                        frameCount = Math.max(1, height / width);
                        rows = frameCount;
                        cols = 1;
                    }
                }
            }

            if (frameCount >= 45 && baseFps < 60) {
                baseFps = 60;
            }

            if (displayName.equals(fileName)) {
                String clean = displayName.replace(".png", "")
                        .replaceAll("(?i)_grid\\d+x\\d+(?:_\\d+f)?", "")
                        .replaceAll("(?i)_\\d+fps", "")
                        .replaceAll("^_+|_+$", "");
                if (!clean.isEmpty()) {
                    displayName = clean;
                }
            }

            TotemEntry entry = new TotemEntry(uniqueId, fileName, displayName, enabled, width, height, cols, rows, frameCount);
            entry.setBaseFps(baseFps);
            if (fps > 0) {
                entry.setFps(fps);
            } else if (configEntry != null && configEntry.frameTime > 0) {
                entry.setFrameTime(configEntry.frameTime);
            }
            if (configEntry != null && configEntry.transform != null) {
                entry.setTransform(configEntry.transform);
            }
            entry.setTextureId(textureId);
            entry.setTexture(texture);
            entry.setTextureRegistered(true);
            if (entry.getTransform().getResolution() > 0) {
                applyResolution(entry);
            }

            entries.add(entry);
            updateActiveCache();
            return entry;
        } catch (Exception e) {
            LOGGER.error("Failed to load totem image: {}", file, e);
            return null;
        }
    }

    public synchronized TotemEntry addTotem(Path sourceFile) {
        String fileName = sourceFile.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return null;
        }

        Path activeDir = getImagesDir();
        Path target = activeDir.resolve(fileName);
        int counter = 1;
        while (Files.exists(target)) {
            String base = fileName.substring(0, fileName.length() - 4);
            target = activeDir.resolve(base + "_" + counter + ".png");
            counter++;
        }

        try {
            Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            TotemEntry entry = loadSingleImage(target, null);
            if (entry != null) {
                if (currentActiveTotem == null) {
                    currentActiveTotem = entry;
                }
                updateActiveCache();
                saveConfig();
            }
            return entry;
        } catch (Exception e) {
            LOGGER.error("Failed to copy and add totem image: {}", sourceFile, e);
            return null;
        }
    }

    public synchronized void removeTotem(TotemEntry entry) {
        if (entry == null) return;

        MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.getTextureId());
        entry.close();

        try {
            Path file = getImagesDir().resolve(entry.getFileName());
            Files.deleteIfExists(file);
        } catch (Exception e) {
            LOGGER.error("Failed to delete totem file: {}", entry.getFileName(), e);
        }

        entries.remove(entry);

        if (currentActiveTotem == entry) {
            currentActiveTotem = getActiveEntries().stream().findFirst().orElse(null);
        }
        if (poppingTotem == entry) {
            poppingTotem = currentActiveTotem;
        }

        updateActiveCache();
        saveConfig();
    }

    public synchronized void setCurrentTotem(TotemEntry entry) {
        if (entry != null) {
            entry.setEnabled(true);
            this.currentActiveTotem = entry;
            this.poppingTotem = entry;
            updateActiveCache();
            saveConfig();
        }
    }

    public synchronized void onEntryToggle(TotemEntry entry) {
        updateActiveCache();
        saveConfig();
        List<TotemEntry> active = getActiveEntries();
        if (active.isEmpty()) {
            this.currentActiveTotem = null;
            this.poppingTotem = null;
        } else {
            if (this.currentActiveTotem == null || !this.currentActiveTotem.isEnabled() || this.currentActiveTotem == entry) {
                if (!entry.isEnabled()) {
                    this.currentActiveTotem = active.get(0);
                    this.poppingTotem = this.currentActiveTotem;
                }
            }
        }
        updateActiveCache();
    }

    public synchronized void onTotemPop() {
        if (hasCustomTotem()) {
            this.poppingTotem = this.currentActiveTotem;
            advanceNextTotem();
        }
    }

    public synchronized void advanceNextTotem() {
        List<TotemEntry> active = getActiveEntries();
        if (active.isEmpty()) {
            return;
        }

        if (mode == TotemMode.SINGLE) {
            return;
        } else if (mode == TotemMode.SEQUENTIAL) {
            int currentIndex = active.indexOf(currentActiveTotem);
            int nextIndex = (currentIndex + 1) % active.size();
            this.currentActiveTotem = active.get(nextIndex);
        } else if (mode == TotemMode.RANDOM) {
            if (active.size() == 1) {
                this.currentActiveTotem = active.get(0);
            } else {
                List<TotemEntry> candidates = new ArrayList<>(active);
                if (currentActiveTotem != null) {
                    candidates.remove(currentActiveTotem);
                }
                if (candidates.isEmpty()) {
                    candidates = active;
                }
                this.currentActiveTotem = candidates.get(random.nextInt(candidates.size()));
            }
        }
        updateActiveCache();
        saveConfig();
    }

    public synchronized void moveUp(TotemEntry entry) {
        int index = entries.indexOf(entry);
        if (index > 0) {
            Collections.swap(entries, index, index - 1);
            updateActiveCache();
            saveConfig();
        }
    }

    public synchronized void moveDown(TotemEntry entry) {
        int index = entries.indexOf(entry);
        if (index >= 0 && index < entries.size() - 1) {
            Collections.swap(entries, index, index + 1);
            updateActiveCache();
            saveConfig();
        }
    }

    public synchronized List<TotemEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public List<TotemEntry> getActiveEntries() {
        return activeEntriesView;
    }

    public TotemEntry getCurrentActiveTotem() {
        return currentActiveTotem;
    }

    public TotemEntry getPoppingTotem() {
        return poppingTotem != null ? poppingTotem : currentActiveTotem;
    }

    public TotemMode getMode() {
        return mode;
    }

    public void setMode(TotemMode mode) {
        this.mode = mode;
        saveConfig();
    }

    public TotemDisplaySettings getDisplaySettings() {
        return displaySettings;
    }

    public boolean isFullbright() {
        return displaySettings != null && displaySettings.isFullbright();
    }

    public void setFullbright(boolean fullbright) {
        if (displaySettings != null) {
            displaySettings.setFullbright(fullbright);
            saveConfig();
        }
    }

    public boolean isRenderingFloatingItem() {
        return renderingFloatingItem;
    }

    public void setRenderingFloatingItem(boolean renderingFloatingItem) {
        this.renderingFloatingItem = renderingFloatingItem;
    }

    public long getClientTicks() {
        return clientTicks;
    }

    public void tick() {
        this.clientTicks++;
    }

    public Path getImagesDir() {
        if (customImagesDir != null && Files.exists(customImagesDir) && Files.isDirectory(customImagesDir)) {
            return customImagesDir;
        }
        return defaultImagesDir;
    }

    public Path getDefaultImagesDir() {
        return defaultImagesDir;
    }

    public Path getCustomImagesDir() {
        return customImagesDir;
    }

    public boolean isUsingCustomImagesDir() {
        return customImagesDir != null && Files.exists(customImagesDir);
    }

    public synchronized void setCustomImagesDir(Path newDir) {
        if (newDir != null) {
            try {
                if (!Files.exists(newDir)) {
                    Files.createDirectories(newDir);
                }
                this.customImagesDir = newDir.toAbsolutePath().normalize();
            } catch (Exception e) {
                LOGGER.error("Failed to set custom images dir: {}", newDir, e);
                return;
            }
        } else {
            this.customImagesDir = null;
        }
        saveConfig();
        reloadAllTexturesAsync(loadConfigFile(), null);
    }

    public boolean hasCustomTotem() {
        return hasActiveCustomTotem;
    }

    public Path getInventoryGuiDir() {
        return inventoryGuiDir;
    }

    public InventoryGuiSettings getInventoryGuiSettings() {
        return inventoryGuiSettings;
    }

    public Identifier getInventoryGuiTexture() {
        return inventoryGuiTextureId;
    }

    public List<String> getAvailableInventoryImages() {
        return availableInventoryImages;
    }

    public synchronized void scanInventoryImages() {
        availableInventoryImages.clear();
        try {
            if (!Files.exists(inventoryGuiDir)) {
                Files.createDirectories(inventoryGuiDir);
            }
            try (Stream<Path> stream = Files.list(inventoryGuiDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .map(p -> p.getFileName().toString())
                        .forEach(availableInventoryImages::add);
            }
            Path activeImages = getImagesDir();
            if (activeImages != null && Files.exists(activeImages) && !activeImages.equals(inventoryGuiDir)) {
                try (Stream<Path> stream = Files.list(activeImages)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                            .map(p -> p.getFileName().toString())
                            .filter(name -> !availableInventoryImages.contains(name))
                            .forEach(availableInventoryImages::add);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to scan inventory GUI images", e);
        }
    }

    public void setInventoryGuiImage(String fileName) {
        this.inventoryGuiSettings.setImageFileName(fileName);
        loadInventoryGuiTexture();
        saveConfig();
    }

    public void loadInventoryGuiTexture() {
        String fileName = inventoryGuiSettings.getImageFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }

        Path imgPath = inventoryGuiDir.resolve(fileName);
        if (!Files.exists(imgPath)) {
            imgPath = getImagesDir().resolve(fileName);
        }
        if (!Files.exists(imgPath)) {
            LOGGER.warn("Inventory GUI image file not found: {}", fileName);
            return;
        }

        try (InputStream in = Files.newInputStream(imgPath)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.of("customtotem", "inventory_gui_" + Math.abs(fileName.hashCode()));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/inventory_gui", image);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> {
                    if (inventoryGuiTexture != null) {
                        inventoryGuiTexture.close();
                    }
                    inventoryGuiTexture = texture;
                    inventoryGuiTextureId = id;
                    if (client.getTextureManager() != null) {
                        client.getTextureManager().registerTexture(id, texture);
                    }
                    LOGGER.info("Registered custom inventory GUI texture: {}", fileName);
                });
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load inventory GUI texture from: {}", imgPath, e);
        }
    }
}
