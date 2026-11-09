package com.customtotem.manager;

import com.customtotem.config.TotemConfig;
import com.customtotem.model.TotemDisplaySettings;
import com.customtotem.model.TotemEntry;
import com.customtotem.model.TotemMode;
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
import java.util.stream.Stream;

public class TotemManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotemMod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TotemManager INSTANCE = new TotemManager();

    private final Path baseDir;
    private final Path defaultImagesDir;
    private Path customImagesDir = null;
    private final Path configFile;

    private final List<TotemEntry> entries = new ArrayList<>();
    private TotemEntry currentActiveTotem = null;
    private TotemEntry poppingTotem = null;
    private TotemMode mode = TotemMode.SEQUENTIAL;
    private TotemDisplaySettings displaySettings = new TotemDisplaySettings();

    private boolean renderingFloatingItem = false;
    private long clientTicks = 0;
    private final Random random = new Random();

    private TotemManager() {
        this.baseDir = FabricLoader.getInstance().getConfigDir().resolve("custom_totems");
        this.defaultImagesDir = baseDir.resolve("images");
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
        } catch (Exception e) {
            LOGGER.error("Failed to create default custom totem directories", e);
        }

        TotemConfig loadedConfig = loadConfigFile();
        if (loadedConfig != null) {
            this.mode = loadedConfig.mode != null ? loadedConfig.mode : TotemMode.SEQUENTIAL;
            if (loadedConfig.displaySettings != null) {
                this.displaySettings = loadedConfig.displaySettings;
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

        reloadAllTextures(loadedConfig);
    }

    private TotemConfig loadConfigFile() {
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

        for (TotemEntry entry : entries) {
            config.entries.add(new TotemConfig.TotemEntryConfig(
                    entry.getId(),
                    entry.getFileName(),
                    entry.getDisplayName(),
                    entry.isEnabled(),
                    entry.getFrameTime(),
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

    public synchronized void reloadAllTextures(TotemConfig loadedConfig) {
        MinecraftClient client = MinecraftClient.getInstance();
        for (TotemEntry entry : entries) {
            if (entry.getTextureId() != null) {
                client.getTextureManager().destroyTexture(entry.getTextureId());
            }
            entry.close();
        }
        entries.clear();

        Map<String, TotemConfig.TotemEntryConfig> configMap = new HashMap<>();
        List<String> orderList = new ArrayList<>();
        if (loadedConfig != null && loadedConfig.entries != null) {
            for (TotemConfig.TotemEntryConfig ec : loadedConfig.entries) {
                configMap.put(ec.fileName, ec);
                orderList.add(ec.fileName);
            }
        }

        List<Path> imageFiles = new ArrayList<>();
        Path activeDir = getImagesDir();
        try {
            if (!Files.exists(activeDir)) {
                Files.createDirectories(activeDir);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to ensure images directory: {}", activeDir, e);
        }

        try (Stream<Path> stream = Files.list(activeDir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                  .forEach(imageFiles::add);
        } catch (Exception e) {
            LOGGER.error("Failed to list totem images from {}", activeDir, e);
        }

        // Sort imageFiles based on orderList if present
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

        for (Path file : imageFiles) {
            loadSingleImage(file, configMap.get(file.getFileName().toString()));
        }

        // Restore current active totem
        if (loadedConfig != null && loadedConfig.currentTotemId != null) {
            for (TotemEntry entry : entries) {
                if (entry.getId().equals(loadedConfig.currentTotemId)) {
                    this.currentActiveTotem = entry;
                    break;
                }
            }
        }

        if (this.currentActiveTotem == null || !this.currentActiveTotem.isEnabled()) {
            this.currentActiveTotem = getActiveEntries().stream().findFirst().orElse(null);
        }

        this.poppingTotem = this.currentActiveTotem;

        saveConfig();
    }

    private TotemEntry loadSingleImage(Path file, TotemConfig.TotemEntryConfig configEntry) {
        String fileName = file.getFileName().toString();
        String idName = fileName.replace(".png", "").replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
        String uniqueId = (configEntry != null && configEntry.id != null) ? configEntry.id : idName + "_" + System.currentTimeMillis();

        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            int width = image.getWidth();
            int height = image.getHeight();

            Identifier textureId = Identifier.of("customtotem", "textures/" + uniqueId);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "customtotem/" + uniqueId, image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);

            boolean enabled = configEntry == null || configEntry.enabled;
            String displayName = configEntry != null && configEntry.displayName != null ? configEntry.displayName : fileName;
            TotemEntry entry = new TotemEntry(uniqueId, fileName, displayName, enabled, width, height);
            if (configEntry != null && configEntry.frameTime > 0) {
                entry.setFrameTime(configEntry.frameTime);
            }
            if (configEntry != null && configEntry.transform != null) {
                entry.setTransform(configEntry.transform);
            }
            entry.setTextureId(textureId);
            entry.setTexture(texture);

            entries.add(entry);
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

        saveConfig();
    }

    public synchronized void setCurrentTotem(TotemEntry entry) {
        if (entry != null) {
            entry.setEnabled(true);
            this.currentActiveTotem = entry;
            this.poppingTotem = entry;
            saveConfig();
        }
    }

    public synchronized void onEntryToggle(TotemEntry entry) {
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
            // Do not switch
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
        saveConfig();
    }

    public synchronized void moveUp(TotemEntry entry) {
        int index = entries.indexOf(entry);
        if (index > 0) {
            Collections.swap(entries, index, index - 1);
            saveConfig();
        }
    }

    public synchronized void moveDown(TotemEntry entry) {
        int index = entries.indexOf(entry);
        if (index >= 0 && index < entries.size() - 1) {
            Collections.swap(entries, index, index + 1);
            saveConfig();
        }
    }

    public synchronized List<TotemEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public synchronized List<TotemEntry> getActiveEntries() {
        List<TotemEntry> active = new ArrayList<>();
        for (TotemEntry entry : entries) {
            if (entry.isEnabled()) {
                active.add(entry);
            }
        }
        return active;
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
        reloadAllTextures(loadConfigFile());
    }

    public synchronized void rescanImages() {
        reloadAllTextures(loadConfigFile());
    }

    public boolean hasCustomTotem() {
        return !getActiveEntries().isEmpty() && currentActiveTotem != null && currentActiveTotem.isEnabled() && currentActiveTotem.getTextureId() != null;
    }
}
