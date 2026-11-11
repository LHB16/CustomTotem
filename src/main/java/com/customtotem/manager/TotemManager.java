package com.customtotem.manager;

import com.customtotem.config.TotemConfig;
import com.customtotem.model.HandOutlineSettings;
import com.customtotem.model.InventoryGuiSettings;
import com.customtotem.model.TotemDisplaySettings;
import com.customtotem.model.TotemEntry;
import com.customtotem.model.TotemMode;
import com.customtotem.model.TotemTextureTransform;
import com.customtotem.util.PlatformUtil;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class TotemManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotemMod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TotemManager INSTANCE = new TotemManager();

    private static final int CPU_CORES = PlatformUtil.isAndroid() ? 1 : Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 2));
    private static final ExecutorService ASYNC_POOL = Executors.newFixedThreadPool(CPU_CORES, r -> {
        Thread t = new Thread(r, "CustomTotem-DecodeWorker");
        t.setDaemon(true);
        return t;
    });

    public static int getMaxSafeTextureSize() {
        if (PlatformUtil.isAndroid()) {
            return 2048;
        }
        // Safe cap of 4096 ensures optimal performance, avoids severe memory bandwidth bottlenecks
        // on Intel integrated GPUs (reducing uncompressed texture RAM from 268MB down to 67MB per image).
        return 4096;
    }

    public record GridDetectionResult(int cols, int rows, int frameCount, int fps, int baseFps) {}

    public static GridDetectionResult detectGrid(String fileName, Path file, int width, int height) {
        int cols = 1;
        int rows = 1;
        int frameCount = 1;
        int fps = 20;
        int baseFps = 60;

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:^|[_-])grid(\\d+)x(\\d+)(?:[_-](\\d+)f)?").matcher(fileName);
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

        java.util.regex.Matcher fpsMatcher = java.util.regex.Pattern.compile("(?i)(?:^|[_-])(\\d+)fps").matcher(fileName);
        if (fpsMatcher.find()) {
            try {
                int matched = Integer.parseInt(fpsMatcher.group(1));
                fps = matched;
                baseFps = matched;
            } catch (Exception ignored) {}
        }

        if (file != null) {
            try {
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
            } catch (Exception ignored) {}
        }

        if (cols == 1 && rows == 1 && frameCount == 1) {
            if (width > 0 && height >= width && height % width == 0) {
                frameCount = Math.max(1, height / width);
                rows = frameCount;
                cols = 1;
            }
        }

        if (frameCount >= 45 && baseFps < 60) {
            baseFps = 60;
        }

        return new GridDetectionResult(cols, rows, frameCount, fps, baseFps);
    }

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
    private HandOutlineSettings outlineSettings = new HandOutlineSettings();
    private InventoryGuiSettings inventoryGuiSettings = new InventoryGuiSettings();
    private Identifier inventoryGuiTextureId = null;
    private NativeImageBackedTexture inventoryGuiTexture = null;
    private int inventoryGuiImageWidth = 0;
    private int inventoryGuiImageHeight = 0;
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
            NativeImage thumbImage,
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
            TotemTextureTransform transform,
            long lastModified,
            long fileSize
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
            if (loadedConfig.outlineSettings != null) {
                this.outlineSettings = loadedConfig.outlineSettings;
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
        config.outlineSettings = this.outlineSettings;
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

    public void forceReloadAllTextures(TotemConfig loadedConfig, Runnable onComplete) {
        synchronized (this) {
            for (TotemEntry entry : entries) {
                entry.setLastModified(0);
                entry.setFileSize(0);
            }
        }
        reloadAllTexturesAsync(loadedConfig, onComplete);
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

        // Snapshot existing entries in memory to compare and reuse unchanged textures
        Map<String, TotemEntry> existingMap = new HashMap<>();
        synchronized (this) {
            for (TotemEntry entry : entries) {
                existingMap.put(entry.getFileName(), entry);
            }
        }

        CompletableFuture.runAsync(() -> {
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

                // Determine target active totem fileName for initial eagerly-loaded full texture
                String targetActiveId = this.currentActiveTotem != null ? this.currentActiveTotem.getId() : (loadedConfig != null ? loadedConfig.currentTotemId : null);
                String targetActiveFileName = null;
                if (targetActiveId != null) {
                    for (Map.Entry<String, TotemConfig.TotemEntryConfig> entry : configMap.entrySet()) {
                        if (targetActiveId.equals(entry.getValue().id)) {
                            targetActiveFileName = entry.getKey();
                            break;
                        }
                    }
                }
                if (targetActiveFileName == null) {
                    for (Path p : imageFiles) {
                        String fn = p.getFileName().toString();
                        TotemConfig.TotemEntryConfig ec = configMap.get(fn);
                        if (ec == null || ec.enabled) {
                            targetActiveFileName = fn;
                            break;
                        }
                    }
                }

                // Diff checking: identify unchanged totems vs new/modified ones
                Set<String> activeFileNames = new HashSet<>();
                Map<String, TotemEntry> reusedMap = new HashMap<>();
                List<Path> filesToDecode = new ArrayList<>();

                for (Path file : imageFiles) {
                    String fileName = file.getFileName().toString();
                    activeFileNames.add(fileName);

                    long lastMod = 0;
                    long size = 0;
                    try {
                        lastMod = Files.getLastModifiedTime(file).toMillis();
                        size = Files.size(file);
                    } catch (Exception ignored) {}

                    TotemEntry existing = existingMap.get(fileName);
                    TotemConfig.TotemEntryConfig configEntry = configMap.get(fileName);
                    boolean isTargetActive = fileName.equals(targetActiveFileName);

                    boolean canReuse = existing != null
                            && existing.getTextureId() != null
                            && existing.getLastModified() != 0
                            && existing.getLastModified() == lastMod
                            && existing.getFileSize() == size
                            && (!isTargetActive || existing.isTextureRegistered());

                    if (canReuse) {
                        if (configEntry != null) {
                            if (configEntry.displayName != null && !configEntry.displayName.isEmpty()) {
                                existing.setDisplayName(configEntry.displayName);
                            }
                            existing.setEnabled(configEntry.enabled);
                            if (configEntry.fps > 0) {
                                existing.setFps(configEntry.fps);
                            }
                            if (configEntry.baseFps > 0) {
                                existing.setBaseFps(configEntry.baseFps);
                            }
                        }
                        reusedMap.put(fileName, existing);
                    } else {
                        filesToDecode.add(file);
                    }
                }

                reloadTotal = filesToDecode.size();
                if (reloadTotal == 0) {
                    reloadStatus = "All " + imageFiles.size() + " Totems are ready.";
                } else {
                    reloadStatus = "Loading " + reloadTotal + " images (0/" + reloadTotal + ")...";
                }

                AtomicInteger completedCount = new AtomicInteger(0);
                int maxSafeDim = getMaxSafeTextureSize();

                final String activeFileNameRef = targetActiveFileName;
                List<CompletableFuture<PreloadedTotem>> futures = new ArrayList<>();
                for (Path file : filesToDecode) {
                    String fn = file.getFileName().toString();
                    boolean loadFull = fn.equals(activeFileNameRef);
                    TotemConfig.TotemEntryConfig configEntry = configMap.get(fn);
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        PreloadedTotem pre = decodeTotemFile(file, configEntry, maxSafeDim, loadFull);
                        int count = completedCount.incrementAndGet();
                        reloadCurrent = count;
                        reloadStatus = "Loading " + reloadTotal + " images (" + count + "/" + reloadTotal + ")...";
                        return pre;
                    }, ASYNC_POOL));
                }

                List<PreloadedTotem> preloadedList = new ArrayList<>();
                for (CompletableFuture<PreloadedTotem> f : futures) {
                    try {
                        PreloadedTotem p = f.get();
                        if (p != null) {
                            preloadedList.add(p);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to retrieve preloaded totem result", e);
                    }
                }

                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.execute(() -> {
                        synchronized (this) {
                            // 1. Destroy GPU textures for deleted files only
                            for (TotemEntry existing : existingMap.values()) {
                                if (!activeFileNames.contains(existing.getFileName())) {
                                    if (client.getTextureManager() != null) {
                                        if (existing.getTextureId() != null) {
                                            client.getTextureManager().destroyTexture(existing.getTextureId());
                                        }
                                        if (existing.getThumbnailTextureId() != null) {
                                            client.getTextureManager().destroyTexture(existing.getThumbnailTextureId());
                                        }
                                    }
                                    existing.close();
                                }
                            }

                            // 2. Register newly decoded or updated totems
                            Map<String, TotemEntry> newlyCreated = new HashMap<>();
                            for (PreloadedTotem p : preloadedList) {
                                // If this file was modified, clean up its old texture first
                                TotemEntry oldEntry = existingMap.get(p.fileName());
                                if (oldEntry != null) {
                                    if (client.getTextureManager() != null) {
                                        if (oldEntry.getTextureId() != null) {
                                            client.getTextureManager().destroyTexture(oldEntry.getTextureId());
                                        }
                                        if (oldEntry.getThumbnailTextureId() != null) {
                                            client.getTextureManager().destroyTexture(oldEntry.getThumbnailTextureId());
                                        }
                                    }
                                    oldEntry.close();
                                }

                                Identifier textureId = Identifier.of("customtotem", "textures/" + p.uniqueId());
                                NativeImageBackedTexture texture = null;
                                if (p.image() != null) {
                                    texture = new NativeImageBackedTexture(() -> "customtotem/" + p.uniqueId(), p.image());
                                    if (client.getTextureManager() != null) {
                                        client.getTextureManager().registerTexture(textureId, texture);
                                    }
                                }

                                Identifier thumbId = Identifier.of("customtotem", "thumbnails/" + p.uniqueId());
                                NativeImageBackedTexture thumbTexture = new NativeImageBackedTexture(() -> "customtotem/thumb/" + p.uniqueId(), p.thumbImage());
                                if (client.getTextureManager() != null) {
                                    client.getTextureManager().registerTexture(thumbId, thumbTexture);
                                }

                                TotemEntry entry = new TotemEntry(p.uniqueId(), p.fileName(), p.displayName(), p.enabled(), p.width(), p.height(), p.cols(), p.rows(), p.frameCount());
                                entry.setBaseFps(p.baseFps());
                                if (p.fps() > 0) {
                                    entry.setFps(p.fps());
                                } else if (p.frameTime() > 0) {
                                    entry.setFrameTime(p.frameTime());
                                }
                                if (p.transform() != null) {
                                    entry.setTransform(p.transform());
                                }
                                entry.setLastModified(p.lastModified());
                                entry.setFileSize(p.fileSize());
                                entry.setTextureId(textureId);
                                entry.setTexture(texture);
                                entry.setTextureRegistered(p.image() != null);
                                entry.setThumbnailTextureId(thumbId);
                                entry.setThumbnailTexture(thumbTexture);

                                newlyCreated.put(p.fileName(), entry);
                            }

                            // 3. Rebuild entries in order without recreating existing textures
                            entries.clear();
                            for (Path file : imageFiles) {
                                String fn = file.getFileName().toString();
                                TotemEntry e = newlyCreated.get(fn);
                                if (e == null) {
                                    e = reusedMap.get(fn);
                                }
                                if (e != null) {
                                    entries.add(e);
                                }
                            }

                            // 4. Restore/preserve active totem
                            String activeId = this.currentActiveTotem != null ? this.currentActiveTotem.getId() : (loadedConfig != null ? loadedConfig.currentTotemId : null);
                            TotemEntry foundActive = null;
                            if (activeId != null) {
                                for (TotemEntry entry : entries) {
                                    if (entry.getId().equals(activeId)) {
                                        foundActive = entry;
                                        break;
                                    }
                                }
                            }

                            if (foundActive != null && foundActive.isEnabled()) {
                                this.currentActiveTotem = foundActive;
                            } else if (this.currentActiveTotem == null || !this.currentActiveTotem.isEnabled() || !entries.contains(this.currentActiveTotem)) {
                                this.currentActiveTotem = entries.stream().filter(TotemEntry::isEnabled).findFirst().orElse(null);
                            }

                            this.poppingTotem = this.currentActiveTotem;
                            if (this.currentActiveTotem != null && !this.currentActiveTotem.isTextureRegistered()) {
                                ensureTextureRegistered(this.currentActiveTotem);
                            }
                            updateActiveCache();
                            isReloading = false;
                            if (newlyCreated.isEmpty()) {
                                reloadStatus = "Updated " + entries.size() + " Totems (no changes).";
                            } else {
                                reloadStatus = "Loaded " + entries.size() + " Totems (" + newlyCreated.size() + " new/updated).";
                            }
                            saveConfig();

                            if (mode != TotemMode.SINGLE) {
                                prefetchActiveTotemsAsync();
                            }

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
                reloadStatus = "Error loading images: " + e.getMessage();
            }
        });
    }

    private PreloadedTotem decodeTotemFile(Path file, TotemConfig.TotemEntryConfig configEntry, int maxSafeDim, boolean loadFullImage) {
        try {
            String fileName = file.getFileName().toString();
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

            GridDetectionResult detected = detectGrid(fileName, file, width, height);

            if (configEntry != null && configEntry.columns > 0 && configEntry.rows > 0 && configEntry.frameCount > 1) {
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
                // Use detected grid (also auto-heals corrupted frameCount <= 1 configs if detected.frameCount > 1)
                cols = detected.cols();
                rows = detected.rows();
                frameCount = detected.frameCount();
                fps = detected.fps();
                baseFps = detected.baseFps();
            }

            if (displayName.equals(fileName)) {
                String clean = displayName.replace(".png", "")
                        .replaceAll("(?i)[_-]grid\\d+x\\d+(?:[_-]\\d+f)?", "")
                        .replaceAll("(?i)[_-]\\d+fps", "")
                        .replaceAll("^_+|_+$", "");
                if (!clean.isEmpty()) {
                    displayName = clean;
                }
            }

            TotemTextureTransform transform = (configEntry != null && configEntry.transform != null)
                    ? configEntry.transform : null;
            if (transform != null && frameCount > 1 && !transform.isAnimated()) {
                transform.setAnimated(true);
            }

            int safeCols = Math.max(1, cols);
            int safeRows = Math.max(1, rows);

            // Extract lightweight Frame 0 thumbnail (max 128x128)
            int curW = image.getWidth();
            int curH = image.getHeight();
            int curFrameW = Math.max(1, curW / safeCols);
            int curFrameH = Math.max(1, curH / safeRows);
            int thumbW = Math.min(curFrameW, 128);
            int thumbH = Math.min(curFrameH, 128);
            NativeImage thumbImage = new NativeImage(image.getFormat(), thumbW, thumbH, false);
            image.resizeSubRectTo(0, 0, curFrameW, curFrameH, thumbImage);

            if (!loadFullImage) {
                // Lazy loading: free full image immediately to save memory and avoid software resampling overhead
                image.close();
                image = null;
            } else {
                int targetRes = (transform != null && transform.getResolution() > 0) ? transform.getResolution() : 0;
                int origFrameW = width / safeCols;
                int origFrameH = height / safeRows;

                // Auto-clamp if oversized (e.g. 15360x15360 or frame > 512)
                if (targetRes <= 0) {
                    if (width > maxSafeDim || height > maxSafeDim || origFrameW > 512 || origFrameH > 512) {
                        int maxAllowedFrameW = Math.min(512, maxSafeDim / safeCols);
                        int maxAllowedFrameH = Math.min(512, maxSafeDim / safeRows);
                        targetRes = Math.min(maxAllowedFrameW, maxAllowedFrameH);
                        if (targetRes < 16) targetRes = 16;
                    }
                }

                if (targetRes > 0 && (origFrameW > targetRes || width > maxSafeDim || height > maxSafeDim)) {
                    image = createResampledImage(image, cols, rows, targetRes);
                }
            }

            long lastModified = 0;
            long fileSize = 0;
            try {
                lastModified = Files.getLastModifiedTime(file).toMillis();
                fileSize = Files.size(file);
            } catch (Exception ignored) {}

            return new PreloadedTotem(
                    file, image, thumbImage, width, height, uniqueId, fileName, displayName,
                    enabled, cols, rows, frameCount, frameTime, fps, baseFps, transform,
                    lastModified, fileSize
            );
        } catch (Exception e) {
            LOGGER.error("Failed to decode totem image file: {}", file, e);
            return null;
        }
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
            int targetRes = entry.getEffectiveResolution();
            NativeImage finalImage = createResampledImage(originalImage, entry.getColumns(), entry.getRows(), targetRes);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null) {
                NativeImageBackedTexture newTexture = new NativeImageBackedTexture(() -> "customtotem/" + entry.getId(), finalImage);
                client.getTextureManager().registerTexture(entry.getTextureId(), newTexture);
                entry.setTexture(newTexture);
                entry.setTextureRegistered(true);

                // Update thumbnail texture
                int curW = finalImage.getWidth();
                int curH = finalImage.getHeight();
                int fW = Math.max(1, curW / Math.max(1, entry.getColumns()));
                int fH = Math.max(1, curH / Math.max(1, entry.getRows()));
                int tW = Math.min(fW, 128);
                int tH = Math.min(fH, 128);
                NativeImage thumbImg = new NativeImage(finalImage.getFormat(), tW, tH, false);
                finalImage.resizeSubRectTo(0, 0, fW, fH, thumbImg);

                Identifier thumbId = Identifier.of("customtotem", "thumbnails/" + entry.getId());
                NativeImageBackedTexture newThumbTex = new NativeImageBackedTexture(() -> "customtotem/thumb/" + entry.getId(), thumbImg);
                if (entry.getThumbnailTexture() != null) {
                    entry.getThumbnailTexture().close();
                }
                client.getTextureManager().registerTexture(thumbId, newThumbTex);
                entry.setThumbnailTexture(newThumbTex);
                entry.setThumbnailTextureId(thumbId);
            }
            entry.invalidatePrecomputedCache();
        } catch (Exception e) {
            LOGGER.error("Failed to apply resolution to totem: {}", entry.getFileName(), e);
        }
    }

    private final Set<String> loadingTextureIds = Collections.synchronizedSet(new HashSet<>());

    public void ensureTextureRegistered(TotemEntry entry) {
        if (entry == null || entry.getTextureId() == null || entry.isTextureRegistered()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return;

        if (client.isOnThread()) {
            loadAndRegisterTexture(entry, client);
        } else {
            ensureTextureRegisteredAsync(entry, null);
        }
    }

    public void ensureTextureRegisteredAsync(TotemEntry entry, Runnable onComplete) {
        if (entry == null || entry.getTextureId() == null || entry.isTextureRegistered()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (!loadingTextureIds.add(entry.getId())) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Path file = getImagesDir().resolve(entry.getFileName());
        if (!Files.exists(file)) {
            loadingTextureIds.remove(entry.getId());
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                int maxSafeDim = getMaxSafeTextureSize();
                int targetRes = entry.getEffectiveResolution();
                int safeCols = Math.max(1, entry.getColumns());
                int safeRows = Math.max(1, entry.getRows());
                int origFrameW = image.getWidth() / safeCols;
                int origFrameH = image.getHeight() / safeRows;

                if (targetRes <= 0) {
                    if (image.getWidth() > maxSafeDim || image.getHeight() > maxSafeDim || origFrameW > 512 || origFrameH > 512) {
                        int maxAllowedFrameW = Math.min(512, maxSafeDim / safeCols);
                        int maxAllowedFrameH = Math.min(512, maxSafeDim / safeRows);
                        targetRes = Math.min(maxAllowedFrameW, maxAllowedFrameH);
                        if (targetRes < 16) targetRes = 16;
                    }
                }

                if (targetRes > 0 && (origFrameW > targetRes || image.getWidth() > maxSafeDim || image.getHeight() > maxSafeDim)) {
                    image = createResampledImage(image, entry.getColumns(), entry.getRows(), targetRes);
                }
                return image;
            } catch (Exception e) {
                LOGGER.error("Async decode failed for {}", entry.getFileName(), e);
                return null;
            }
        }, ASYNC_POOL).thenAccept(decodedImage -> {
            loadingTextureIds.remove(entry.getId());
            if (decodedImage == null) return;
            if (client != null) {
                client.execute(() -> {
                    try {
                        if (entry.isTextureRegistered()) {
                            decodedImage.close();
                            if (onComplete != null) onComplete.run();
                            return;
                        }
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/" + entry.getId(), decodedImage);
                        if (client.getTextureManager() != null) {
                            client.getTextureManager().registerTexture(entry.getTextureId(), texture);
                        }
                        entry.setTexture(texture);
                        entry.setTextureRegistered(true);
                        LOGGER.info("Async registered full texture for totem: {}", entry.getFileName());
                        if (onComplete != null) onComplete.run();
                    } catch (Exception e) {
                        LOGGER.error("Failed to register async texture for {}", entry.getFileName(), e);
                    }
                });
            }
        });
    }

    public void prefetchActiveTotemsAsync() {
        List<TotemEntry> active = getActiveEntries();
        for (TotemEntry entry : active) {
            if (!entry.isTextureRegistered()) {
                ensureTextureRegisteredAsync(entry, null);
            }
        }
    }

    public synchronized void applyGlobalResolution() {
        for (TotemEntry entry : entries) {
            entry.invalidatePrecomputedCache();
            if (entry.isLoadedInRam()) {
                applyResolution(entry);
            }
        }
    }

    public synchronized void unloadTotemTexture(TotemEntry entry) {
        if (entry == null) return;

        // If the entry being unloaded is currently active, switch to the first active totem or default
        if (this.currentActiveTotem == entry) {
            TotemEntry nextActive = null;
            for (TotemEntry e : entries) {
                if (e != entry && e.isEnabled()) {
                    nextActive = e;
                    break;
                }
            }
            if (nextActive != null) {
                this.currentActiveTotem = nextActive;
                this.poppingTotem = nextActive;
                ensureTextureRegisteredAsync(nextActive, null);
            } else {
                this.currentActiveTotem = null;
                this.poppingTotem = null;
            }
            updateActiveCache();
            saveConfig();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (client.isOnThread()) {
            if (client.getTextureManager() != null) {
                entry.unloadTexture(client.getTextureManager());
            }
        } else {
            client.execute(() -> {
                if (client.getTextureManager() != null) {
                    entry.unloadTexture(client.getTextureManager());
                }
            });
        }
    }

    public synchronized void unloadInactiveTotems() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return;
        client.execute(() -> {
            for (TotemEntry entry : entries) {
                if (entry != currentActiveTotem && !entry.isEnabled() && entry.isLoadedInRam()) {
                    entry.unloadTexture(client.getTextureManager());
                }
            }
            System.gc();
        });
    }

    private void loadAndRegisterTexture(TotemEntry entry, MinecraftClient client) {
        if (entry == null || entry.isTextureRegistered()) return;
        Path file = getImagesDir().resolve(entry.getFileName());
        if (!Files.exists(file)) return;

        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            int maxSafeDim = getMaxSafeTextureSize();
            int targetRes = entry.getEffectiveResolution();
            int safeCols = Math.max(1, entry.getColumns());
            int safeRows = Math.max(1, entry.getRows());
            int origFrameW = image.getWidth() / safeCols;
            int origFrameH = image.getHeight() / safeRows;

            if (targetRes <= 0) {
                if (image.getWidth() > maxSafeDim || image.getHeight() > maxSafeDim || origFrameW > 512 || origFrameH > 512) {
                    int maxAllowedFrameW = Math.min(512, maxSafeDim / safeCols);
                    int maxAllowedFrameH = Math.min(512, maxSafeDim / safeRows);
                    targetRes = Math.min(maxAllowedFrameW, maxAllowedFrameH);
                    if (targetRes < 16) targetRes = 16;
                }
            }

            if (targetRes > 0 && (origFrameW > targetRes || image.getWidth() > maxSafeDim || image.getHeight() > maxSafeDim)) {
                image = createResampledImage(image, entry.getColumns(), entry.getRows(), targetRes);
            }

            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/" + entry.getId(), image);
            if (client.getTextureManager() != null) {
                client.getTextureManager().registerTexture(entry.getTextureId(), texture);
            }
            entry.setTexture(texture);
            entry.setTextureRegistered(true);

            if (entry.getThumbnailTextureId() == null || client.getTextureManager() == null || client.getTextureManager().getTexture(entry.getThumbnailTextureId()) == null) {
                int curW = image.getWidth();
                int curH = image.getHeight();
                int curFrameW = Math.max(1, curW / safeCols);
                int curFrameH = Math.max(1, curH / safeRows);
                int thumbW = Math.min(curFrameW, 128);
                int thumbH = Math.min(curFrameH, 128);
                NativeImage thumbImage = new NativeImage(image.getFormat(), thumbW, thumbH, false);
                image.resizeSubRectTo(0, 0, curFrameW, curFrameH, thumbImage);

                Identifier thumbId = Identifier.of("customtotem", "thumbnails/" + entry.getId());
                NativeImageBackedTexture thumbTex = new NativeImageBackedTexture(() -> "customtotem/thumb/" + entry.getId(), thumbImage);
                if (client.getTextureManager() != null) {
                    client.getTextureManager().registerTexture(thumbId, thumbTex);
                }
                entry.setThumbnailTexture(thumbTex);
                entry.setThumbnailTextureId(thumbId);
            }
            LOGGER.info("Lazily loaded and registered full texture for totem: {}", entry.getFileName());
        } catch (Exception e) {
            LOGGER.error("Failed to lazily load texture for entry: {}", entry.getFileName(), e);
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

            boolean enabled = configEntry == null || configEntry.enabled;
            String displayName = configEntry != null && configEntry.displayName != null ? configEntry.displayName : fileName;

            int cols = 1;
            int rows = 1;
            int frameCount = 1;
            int fps = 20;
            int baseFps = (configEntry != null && configEntry.baseFps > 0) ? configEntry.baseFps : 60;

            GridDetectionResult detected = detectGrid(fileName, file, width, height);

            if (configEntry != null && configEntry.columns > 0 && configEntry.rows > 0 && configEntry.frameCount > 1) {
                cols = configEntry.columns;
                rows = configEntry.rows;
                frameCount = configEntry.frameCount;
                if (configEntry.fps > 0) {
                    fps = configEntry.fps;
                }
            } else {
                cols = detected.cols();
                rows = detected.rows();
                frameCount = detected.frameCount();
                fps = detected.fps();
                baseFps = detected.baseFps();
            }

            if (displayName.equals(fileName)) {
                String clean = displayName.replace(".png", "")
                        .replaceAll("(?i)[_-]grid\\d+x\\d+(?:[_-]\\d+f)?", "")
                        .replaceAll("(?i)[_-]\\d+fps", "")
                        .replaceAll("^_+|_+$", "");
                if (!clean.isEmpty()) {
                    displayName = clean;
                }
            }

            TotemTextureTransform transform = (configEntry != null && configEntry.transform != null)
                    ? configEntry.transform : null;

            int targetRes = (transform != null && transform.getResolution() > 0) ? transform.getResolution() : 0;
            int safeCols = Math.max(1, cols);
            int safeRows = Math.max(1, rows);
            int origFrameW = width / safeCols;
            int origFrameH = height / safeRows;
            int maxSafeDim = getMaxSafeTextureSize();

            if (targetRes <= 0) {
                if (width > maxSafeDim || height > maxSafeDim || origFrameW > 512 || origFrameH > 512) {
                    int maxAllowedFrameW = Math.min(512, maxSafeDim / safeCols);
                    int maxAllowedFrameH = Math.min(512, maxSafeDim / safeRows);
                    targetRes = Math.min(maxAllowedFrameW, maxAllowedFrameH);
                    if (targetRes < 16) targetRes = 16;
                }
            }

            if (targetRes > 0 && (origFrameW > targetRes || width > maxSafeDim || height > maxSafeDim)) {
                image = createResampledImage(image, cols, rows, targetRes);
            }

            // Extract Frame 0 thumbnail
            int curW = image.getWidth();
            int curH = image.getHeight();
            int curFrameW = Math.max(1, curW / safeCols);
            int curFrameH = Math.max(1, curH / safeRows);
            int thumbW = Math.min(curFrameW, 128);
            int thumbH = Math.min(curFrameH, 128);
            NativeImage thumbImage = new NativeImage(image.getFormat(), thumbW, thumbH, false);
            image.resizeSubRectTo(0, 0, curFrameW, curFrameH, thumbImage);

            Identifier thumbId = Identifier.of("customtotem", "thumbnails/" + uniqueId);
            NativeImageBackedTexture thumbTexture = new NativeImageBackedTexture(() -> "customtotem/thumb/" + uniqueId, thumbImage);

            Identifier textureId = Identifier.of("customtotem", "textures/" + uniqueId);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/" + uniqueId, image);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null) {
                client.getTextureManager().registerTexture(textureId, texture);
                client.getTextureManager().registerTexture(thumbId, thumbTexture);
            }

            TotemEntry entry = new TotemEntry(uniqueId, fileName, displayName, enabled, width, height, cols, rows, frameCount);
            entry.setBaseFps(baseFps);
            if (fps > 0) {
                entry.setFps(fps);
            } else if (configEntry != null && configEntry.frameTime > 0) {
                entry.setFrameTime(configEntry.frameTime);
            }
            if (transform != null) {
                entry.setTransform(transform);
            }
            try {
                entry.setLastModified(Files.getLastModifiedTime(file).toMillis());
                entry.setFileSize(Files.size(file));
            } catch (Exception ignored) {}
            entry.setTextureId(textureId);
            entry.setTexture(texture);
            entry.setTextureRegistered(true);
            entry.setThumbnailTextureId(thumbId);
            entry.setThumbnailTexture(thumbTexture);

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

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getTextureManager() != null) {
            if (entry.getTextureId() != null) {
                client.getTextureManager().destroyTexture(entry.getTextureId());
            }
            if (entry.getThumbnailTextureId() != null) {
                client.getTextureManager().destroyTexture(entry.getThumbnailTextureId());
            }
        }
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
            ensureTextureRegistered(entry);
            updateActiveCache();
            saveConfig();
        }
    }

    public synchronized void onEntryToggle(TotemEntry entry) {
        if (!entry.isEnabled()) {
            unloadTotemTexture(entry);
        } else {
            ensureTextureRegisteredAsync(entry, null);
        }
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
                    ensureTextureRegisteredAsync(this.currentActiveTotem, null);
                }
            }
        }
        updateActiveCache();
        if (mode != TotemMode.SINGLE) {
            prefetchActiveTotemsAsync();
        }
    }

    public synchronized void onTotemPop() {
        if (hasCustomTotem()) {
            this.poppingTotem = this.currentActiveTotem;
            advanceNextTotem();
            if (this.currentActiveTotem != null && !this.currentActiveTotem.isTextureRegistered()) {
                ensureTextureRegisteredAsync(this.currentActiveTotem, null);
            }
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
        if (this.currentActiveTotem != null && !this.currentActiveTotem.isTextureRegistered()) {
            ensureTextureRegisteredAsync(this.currentActiveTotem, null);
        }
        updateActiveCache();
        saveConfig();
        if (mode != TotemMode.SINGLE) {
            prefetchActiveTotemsAsync();
        }
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
        if (mode != TotemMode.SINGLE) {
            prefetchActiveTotemsAsync();
        }
    }

    public TotemDisplaySettings getDisplaySettings() {
        return displaySettings;
    }

    public HandOutlineSettings getOutlineSettings() {
        if (outlineSettings == null) {
            outlineSettings = new HandOutlineSettings();
        }
        return outlineSettings;
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

    public int getInventoryGuiImageWidth() {
        return inventoryGuiImageWidth;
    }

    public int getInventoryGuiImageHeight() {
        return inventoryGuiImageHeight;
    }

    public void loadInventoryGuiTexture() {
        if (inventoryGuiSettings == null || !inventoryGuiSettings.isEnabled()) {
            return;
        }
        String fileName = inventoryGuiSettings.getImageFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            this.inventoryGuiImageWidth = 0;
            this.inventoryGuiImageHeight = 0;
            return;
        }

        Path imgPath = inventoryGuiDir.resolve(fileName);
        if (!Files.exists(imgPath)) {
            imgPath = getImagesDir().resolve(fileName);
        }
        if (!Files.exists(imgPath)) {
            LOGGER.warn("Inventory GUI image file not found: {}", fileName);
            this.inventoryGuiImageWidth = 0;
            this.inventoryGuiImageHeight = 0;
            return;
        }

        try (InputStream in = Files.newInputStream(imgPath)) {
            NativeImage image = NativeImage.read(in);
            int origW = image.getWidth();
            int origH = image.getHeight();
            // Cap at 2048 for GUI background textures to prevent memory spikes on Intel iGPUs
            int maxSafeDim = Math.min(2048, getMaxSafeTextureSize());
            if (origW > maxSafeDim || origH > maxSafeDim) {
                LOGGER.warn("Inventory GUI image {} exceeds max safe size ({}x{}), downscaling...", fileName, origW, origH);
                int targetW = maxSafeDim;
                int targetH = (int) Math.max(1, Math.round((float) maxSafeDim * (float) origH / (float) origW));
                if (origH > origW) {
                    targetH = maxSafeDim;
                    targetW = (int) Math.max(1, Math.round((float) maxSafeDim * (float) origW / (float) origH));
                }
                NativeImage downscaled = new NativeImage(image.getFormat(), targetW, targetH, false);
                image.resizeSubRectTo(0, 0, origW, origH, downscaled);
                image.close();
                image = downscaled;
            }

            this.inventoryGuiImageWidth = image.getWidth();
            this.inventoryGuiImageHeight = image.getHeight();
            Identifier id = Identifier.of("customtotem", "inventory_gui_" + Math.abs(fileName.hashCode()));
            final NativeImage finalImage = image;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> {
                    try {
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/inventory_gui", finalImage);
                        if (inventoryGuiTexture != null) {
                            inventoryGuiTexture.close();
                        }
                        inventoryGuiTexture = texture;
                        inventoryGuiTextureId = id;
                        if (client.getTextureManager() != null) {
                            client.getTextureManager().registerTexture(id, texture);
                        }
                        LOGGER.info("Registered custom inventory GUI texture: {}", fileName);
                    } catch (Exception e) {
                        LOGGER.error("Failed to register inventory GUI texture on render thread", e);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load inventory GUI texture from: {}", imgPath, e);
        }
    }
}
