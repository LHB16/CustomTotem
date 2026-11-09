package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class FolderBrowserScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotemMod");

    private final Screen parent;
    private Path currentDirectory;
    private TextFieldWidget pathField;
    private FileListWidget fileListWidget;

    private String statusMessage = "";
    private int statusColor = 0x55FF55;
    private long statusExpireTime = 0;

    public FolderBrowserScreen(Screen parent) {
        super(Text.literal("Totem Images Folder Browser"));
        this.parent = parent;
        Path initial = TotemManager.getInstance().getImagesDir();
        if (initial != null && Files.exists(initial)) {
            this.currentDirectory = initial.toAbsolutePath().normalize();
        } else {
            this.currentDirectory = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // --- Row 1: Quick Shortcuts (Y = 22) ---
        List<Shortcut> shortcuts = getShortcuts();
        int scCount = shortcuts.size();
        int scSpacing = 4;
        int scBtnWidth = Math.min(105, (this.width - 20 - (scSpacing * (scCount - 1))) / scCount);
        int totalScWidth = (scBtnWidth * scCount) + (scSpacing * (scCount - 1));
        int scStartX = (this.width - totalScWidth) / 2;

        for (int i = 0; i < scCount; i++) {
            Shortcut sc = shortcuts.get(i);
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(sc.name),
                    btn -> navigateTo(sc.path)
            ).dimensions(scStartX + (scBtnWidth + scSpacing) * i, 22, scBtnWidth, 20).build());
        }

        // --- Row 2: Path input & Navigation (Y = 46) ---
        int upBtnWidth = 44;
        int goBtnWidth = 36;
        int pathSpacing = 4;
        int pathFieldWidth = Math.min(this.width - 20 - upBtnWidth - goBtnWidth - (pathSpacing * 2), 480);
        int totalPathWidth = upBtnWidth + pathFieldWidth + goBtnWidth + (pathSpacing * 2);
        int pathStartX = (this.width - totalPathWidth) / 2;

        // Up button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▲ Up"),
                btn -> navigateUp()
        ).dimensions(pathStartX, 46, upBtnWidth, 20).build());

        // Path Text Field
        this.pathField = new TextFieldWidget(
                this.textRenderer,
                pathStartX + upBtnWidth + pathSpacing,
                46,
                pathFieldWidth,
                20,
                Text.literal("Folder Path")
        );
        this.pathField.setMaxLength(512);
        this.pathField.setText(this.currentDirectory != null ? this.currentDirectory.toString() : "");
        this.addDrawableChild(pathField);

        // Go button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Go"),
                btn -> navigateTo(pathField.getText())
        ).dimensions(pathStartX + upBtnWidth + pathSpacing + pathFieldWidth + pathSpacing, 46, goBtnWidth, 20).build());

        // --- Row 3: File / Folder List (Y = 70 to height - 56) ---
        int listY = 70;
        int listHeight = Math.max(40, this.height - listY - 56);
        this.fileListWidget = new FileListWidget(this.client, this.width, listHeight, listY, 26, this);
        this.addDrawableChild(fileListWidget);

        // --- Row 4: Bottom Action Buttons (Y = height - 26) ---
        int btnWidth = 110;
        int btnSpacing = 6;
        int totalBottomWidth = (btnWidth * 4) + (btnSpacing * 3);
        if (totalBottomWidth > this.width - 10) {
            btnWidth = (this.width - 20 - (btnSpacing * 3)) / 4;
            totalBottomWidth = (btnWidth * 4) + (btnSpacing * 3);
        }
        int bStartX = (this.width - totalBottomWidth) / 2;
        int bY = this.height - 26;

        // Button 1: Set as Totem Folder
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("✔ Set As Folder"),
                btn -> {
                    TotemManager.getInstance().setCustomImagesDir(this.currentDirectory);
                    if (this.parent instanceof TotemManagerScreen tms) {
                        tms.refreshList();
                    }
                    this.close();
                }
        ).dimensions(bStartX, bY, btnWidth, 20).build());

        // Button 2: Reset to Mod Default Folder
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Reset Default"),
                btn -> {
                    TotemManager.getInstance().setCustomImagesDir(null);
                    if (this.parent instanceof TotemManagerScreen tms) {
                        tms.refreshList();
                    }
                    this.close();
                }
        ).dimensions(bStartX + (btnWidth + btnSpacing), bY, btnWidth, 20).build());

        // Button 3: Native PC File Picker (Desktop only)
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("PC Picker"),
                btn -> openDesktopFileDialog()
        ).dimensions(bStartX + (btnWidth + btnSpacing) * 2, bY, btnWidth, 20).build());

        // Button 4: Back / Close
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> this.close()
        ).dimensions(bStartX + (btnWidth + btnSpacing) * 3, bY, btnWidth, 20).build());
    }

    public void navigateTo(Path dir) {
        if (dir == null) return;
        try {
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                this.currentDirectory = dir.toAbsolutePath().normalize();
                if (this.pathField != null) {
                    this.pathField.setText(this.currentDirectory.toString());
                }
                if (this.fileListWidget != null) {
                    this.fileListWidget.refresh();
                }
            } else {
                setStatus("§cDirectory does not exist: " + dir, true);
            }
        } catch (Exception e) {
            setStatus("§cCannot access: " + e.getMessage(), true);
        }
    }

    public void navigateTo(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) return;
        try {
            Path p = Path.of(pathStr.trim());
            navigateTo(p);
        } catch (Exception e) {
            setStatus("§cInvalid path syntax: " + e.getMessage(), true);
        }
    }

    public void navigateUp() {
        if (currentDirectory != null) {
            Path parent = currentDirectory.getParent();
            if (parent != null && Files.exists(parent)) {
                navigateTo(parent);
            }
        }
    }

    public void importImage(Path file) {
        try {
            TotemEntry entry = TotemManager.getInstance().addTotem(file);
            if (entry != null) {
                setStatus("§a✓ Added: " + file.getFileName().toString(), false);
                if (this.parent instanceof TotemManagerScreen tms) {
                    tms.refreshList();
                }
            } else {
                setStatus("§c✗ Failed to load: " + file.getFileName().toString(), true);
            }
        } catch (Exception e) {
            setStatus("§c✗ Error adding image: " + e.getMessage(), true);
        }
    }

    public void setStatus(String message, boolean isError) {
        this.statusMessage = message;
        this.statusColor = isError ? 0xFF5555 : 0x55FF55;
        this.statusExpireTime = System.currentTimeMillis() + 4000;
    }

    private void openDesktopFileDialog() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Totem PNG Image",
                        this.currentDirectory != null ? this.currentDirectory.toString() : "",
                        null,
                        "PNG Images (*.png)",
                        false
                );
            } catch (Throwable t) {
                LOGGER.warn("TinyFileDialogs failed (expected on mobile): {}", t.getMessage());
                return null;
            }
        }).thenAccept(filePath -> {
            if (filePath != null && !filePath.trim().isEmpty()) {
                Path path = Path.of(filePath);
                MinecraftClient.getInstance().execute(() -> {
                    importImage(path);
                    if (Files.exists(path) && path.getParent() != null) {
                        navigateTo(path.getParent());
                    }
                });
            }
        });
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.key() == 257) { // Enter key
            if (pathField != null && pathField.isFocused()) {
                navigateTo(pathField.getText());
                return true;
            }
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        super.render(context, mouseX, mouseY, tickDelta);

        // Header Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 7, 0xFFFFFF);

        // Status or Active Folder display above bottom buttons
        if (System.currentTimeMillis() < statusExpireTime && !statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusMessage), this.width / 2, this.height - 42, statusColor);
        } else {
            Path active = TotemManager.getInstance().getImagesDir();
            String activeStr = active != null ? active.toString() : "Default";
            if (this.textRenderer.getWidth("Active: " + activeStr) > this.width - 20) {
                activeStr = "..." + activeStr.substring(Math.max(0, activeStr.length() - 40));
            }
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§7Active Folder: §e" + activeStr),
                    this.width / 2,
                    this.height - 42,
                    0xAAAAAA
            );
        }
    }

    public Path getCurrentDirectory() {
        return currentDirectory;
    }

    private static List<Shortcut> getShortcuts() {
        List<Shortcut> list = new ArrayList<>();

        // 1. Mod Default
        list.add(new Shortcut("📁 Mod Default", TotemManager.getInstance().getDefaultImagesDir()));

        // 2. Android / PC Downloads
        Path dl = resolveFirstExisting(
                Path.of("/storage/emulated/0/Download"),
                Path.of("/sdcard/Download"),
                Path.of(System.getProperty("user.home", ""), "Downloads")
        );
        if (dl != null) {
            list.add(new Shortcut("📥 Downloads", dl));
        }

        // 3. Android / PC Pictures
        Path pic = resolveFirstExisting(
                Path.of("/storage/emulated/0/Pictures"),
                Path.of("/sdcard/Pictures"),
                Path.of(System.getProperty("user.home", ""), "Pictures")
        );
        if (pic != null) {
            list.add(new Shortcut("🖼 Pictures", pic));
        }

        // 4. Game Root
        list.add(new Shortcut("🎮 Game Dir", FabricLoader.getInstance().getGameDir()));

        return list;
    }

    private static Path resolveFirstExisting(Path... candidates) {
        for (Path p : candidates) {
            try {
                if (p != null && Files.exists(p) && Files.isDirectory(p)) {
                    return p.toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private record Shortcut(String name, Path path) {}

    // =========================================================================
    // File List Widget
    // =========================================================================
    public static class FileListWidget extends ElementListWidget<FileListWidget.FileItemWidget> {
        private final FolderBrowserScreen screen;

        public FileListWidget(MinecraftClient client, int width, int height, int y, int itemHeight, FolderBrowserScreen screen) {
            super(client, width, height, y, itemHeight);
            this.screen = screen;
            refresh();
        }

        public void refresh() {
            this.clearEntries();
            Path current = screen.getCurrentDirectory();
            if (current == null || !Files.exists(current)) {
                return;
            }

            // Up entry if has parent
            Path parent = current.getParent();
            if (parent != null && Files.exists(parent)) {
                this.addEntry(new FileItemWidget(parent, ".. [Parent Folder]", true, true));
            }

            List<Path> subDirs = new ArrayList<>();
            List<Path> pngFiles = new ArrayList<>();

            try (Stream<Path> stream = Files.list(current)) {
                stream.forEach(p -> {
                    try {
                        if (Files.isDirectory(p) && !Files.isHidden(p)) {
                            subDirs.add(p);
                        } else if (p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                            pngFiles.add(p);
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                LOGGER.error("Failed to list files in {}", current, e);
            }

            subDirs.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            for (Path dir : subDirs) {
                this.addEntry(new FileItemWidget(dir, dir.getFileName().toString(), true, false));
            }

            pngFiles.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            for (Path file : pngFiles) {
                this.addEntry(new FileItemWidget(file, file.getFileName().toString(), false, false));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(this.width - 20, 520);
        }

        public class FileItemWidget extends ElementListWidget.Entry<FileItemWidget> {
            private final Path path;
            private final String displayName;
            private final boolean isDirectory;
            private final boolean isParent;
            private final ButtonWidget actionButton;
            private final List<ClickableWidget> children = new ArrayList<>();

            public FileItemWidget(Path path, String displayName, boolean isDirectory, boolean isParent) {
                this.path = path;
                this.displayName = displayName;
                this.isDirectory = isDirectory;
                this.isParent = isParent;

                if (isParent) {
                    this.actionButton = ButtonWidget.builder(
                            Text.literal("▲ Up"),
                            btn -> screen.navigateUp()
                    ).dimensions(0, 0, 48, 20).build();
                } else if (isDirectory) {
                    this.actionButton = ButtonWidget.builder(
                            Text.literal("Open"),
                            btn -> screen.navigateTo(path)
                    ).dimensions(0, 0, 48, 20).build();
                } else {
                    this.actionButton = ButtonWidget.builder(
                            Text.literal("+ Add"),
                            btn -> screen.importImage(path)
                    ).dimensions(0, 0, 48, 20).build();
                }

                this.children.add(this.actionButton);
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int x = getX();
                int y = getY();
                int width = getWidth();
                int height = getHeight();
                TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

                if (hovered) {
                    context.fill(x - 2, y, x + width + 2, y + height, 0x25FFFFFF);
                }

                // Render Icon & Label
                String label;
                int color;
                if (isParent) {
                    label = "📁 .. [Parent Folder]";
                    color = 0xFFAA00;
                } else if (isDirectory) {
                    label = "📁 " + displayName + "/";
                    color = 0xFFFF55;
                } else {
                    label = "🖼 " + displayName;
                    color = 0x55FFFF;
                }

                if (textRenderer.getWidth(label) > width - 65) {
                    label = textRenderer.trimToWidth(label, width - 75) + "...";
                }
                context.drawTextWithShadow(textRenderer, Text.literal(label), x + 4, y + 6, color);

                // Render Action Button
                actionButton.setX(x + width - 50);
                actionButton.setY(y + 2);
                actionButton.render(context, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(Click click, boolean focused) {
                if (actionButton.mouseClicked(click, focused)) {
                    return true;
                }
                // Also allow clicking anywhere on row to open or add
                if (isParent) {
                    screen.navigateUp();
                    return true;
                } else if (isDirectory) {
                    screen.navigateTo(path);
                    return true;
                } else {
                    screen.importImage(path);
                    return true;
                }
            }

            @Override
            public List<? extends Element> children() {
                return this.children;
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return this.children;
            }
        }
    }
}
