package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemEntry;
import com.customtotem.util.PlatformUtil;
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

    public enum BrowseTarget {
        TOTEM,
        INVENTORY_GUI
    }

    private final Screen parent;
    private final BrowseTarget browseTarget;
    private Path currentDirectory;
    private TextFieldWidget pathField;
    private FileListWidget fileListWidget;

    private String statusMessage = "";
    private int statusColor = 0xFF55FF55;
    private long statusExpireTime = 0;

    public FolderBrowserScreen(Screen parent) {
        this(parent, null, BrowseTarget.TOTEM);
    }

    public FolderBrowserScreen(Screen parent, Path initialDir) {
        this(parent, initialDir, (parent instanceof InventoryGuiSettingsScreen) ? BrowseTarget.INVENTORY_GUI : BrowseTarget.TOTEM);
    }

    public FolderBrowserScreen(Screen parent, Path initialDir, BrowseTarget browseTarget) {
        super(Text.literal(browseTarget == BrowseTarget.INVENTORY_GUI ? "Inventory Background Browser" : "Totem File & Folder Browser"));
        this.parent = parent;
        this.browseTarget = browseTarget != null ? browseTarget : BrowseTarget.TOTEM;
        if (initialDir != null && Files.exists(initialDir)) {
            this.currentDirectory = initialDir.toAbsolutePath().normalize();
        } else if (this.browseTarget == BrowseTarget.INVENTORY_GUI) {
            Path invDir = TotemManager.getInstance().getInventoryGuiDir();
            if (invDir != null && Files.exists(invDir)) {
                this.currentDirectory = invDir.toAbsolutePath().normalize();
            } else {
                this.currentDirectory = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            }
        } else {
            Path initial = TotemManager.getInstance().getImagesDir();
            if (initial != null && Files.exists(initial)) {
                this.currentDirectory = initial.toAbsolutePath().normalize();
            } else {
                this.currentDirectory = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            }
        }
    }

    public BrowseTarget getBrowseTarget() {
        return browseTarget;
    }

    @Override
    protected void init() {
        // --- Row 1: Quick Shortcuts (Y = 22) ---
        List<Shortcut> shortcuts = getShortcuts();
        int scCount = shortcuts.size();
        int scSpacing = 4;
        int scBtnWidth = Math.min(85, (this.width - 20 - (scSpacing * (scCount - 1))) / scCount);
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
                Text.literal("Up"),
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

        // --- Row 3 & 4: File List & Bottom Action Buttons ---
        boolean twoRows = this.width < 440;
        int bottomReserve = twoRows ? 54 : 32;
        int listY = 70;
        int listHeight = Math.max(40, this.height - listY - bottomReserve);

        this.fileListWidget = new FileListWidget(this.client, this.width - 34, listHeight, listY, 26, this);
        this.fileListWidget.setX(10);
        this.addDrawableChild(fileListWidget);

        // Scroll helper buttons on the right side
        int scrollBtnX = this.width - 28;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Up"),
                btn -> fileListWidget.scroll(-80)
        ).dimensions(scrollBtnX, listY + 2, 24, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Down"),
                btn -> fileListWidget.scroll(80)
        ).dimensions(scrollBtnX, listY + listHeight - 22, 24, 20).build());

        // --- Row 4: Bottom Action Buttons ---
        if (twoRows) {
            int row1Y = this.height - 48;
            int row2Y = this.height - 25;
            int btnSpacing = 4;

            // Row 1: 3 buttons (Select/Import Folder, Download, Web Server)
            int btn3W = (this.width - 20 - (btnSpacing * 2)) / 3;
            int startX1 = (this.width - ((btn3W * 3) + (btnSpacing * 2))) / 2;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(browseTarget == BrowseTarget.INVENTORY_GUI ? "Import Dir" : "Select Folder"),
                    btn -> handleSelectFolder()
            ).dimensions(startX1, row1Y, btn3W, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Download"),
                    btn -> {
                        if (this.client != null) {
                            DownloadUrlScreen.TargetType target = (browseTarget == BrowseTarget.INVENTORY_GUI)
                                    ? DownloadUrlScreen.TargetType.INVENTORY_GUI
                                    : DownloadUrlScreen.TargetType.TOTEM;
                            this.client.setScreen(new DownloadUrlScreen(this, target));
                        }
                    }
            ).dimensions(startX1 + btn3W + btnSpacing, row1Y, btn3W, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Web Server"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new WebGalleryScreen(this));
                        }
                    }
            ).dimensions(startX1 + (btn3W + btnSpacing) * 2, row1Y, btn3W, 20).build());

            // Row 2: 2 buttons (Default, Back)
            int btn2W = (this.width - 20 - btnSpacing) / 2;
            int startX2 = (this.width - ((btn2W * 2) + btnSpacing)) / 2;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Default"),
                    btn -> handleDefaultFolder()
            ).dimensions(startX2, row2Y, btn2W, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Back"),
                    btn -> this.close()
            ).dimensions(startX2 + btn2W + btnSpacing, row2Y, btn2W, 20).build());
        } else {
            int btnWidth = 92;
            int btnSpacing = 4;
            int btnCount = 5;
            int totalBottomWidth = (btnWidth * btnCount) + (btnSpacing * (btnCount - 1));
            if (totalBottomWidth > this.width - 10) {
                btnWidth = (this.width - 20 - (btnSpacing * (btnCount - 1))) / btnCount;
                totalBottomWidth = (btnWidth * btnCount) + (btnSpacing * (btnCount - 1));
            }
            int bStartX = (this.width - totalBottomWidth) / 2;
            int bY = this.height - 25;

            // Button 1: Set as Totem Folder or Import Folder for Inventory
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(browseTarget == BrowseTarget.INVENTORY_GUI ? "Import Dir" : "Select Folder"),
                    btn -> handleSelectFolder()
            ).dimensions(bStartX, bY, btnWidth, 20).build());

            // Button 2: Download by Link URL
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Download"),
                    btn -> {
                        if (this.client != null) {
                            DownloadUrlScreen.TargetType target = (browseTarget == BrowseTarget.INVENTORY_GUI)
                                    ? DownloadUrlScreen.TargetType.INVENTORY_GUI
                                    : DownloadUrlScreen.TargetType.TOTEM;
                            this.client.setScreen(new DownloadUrlScreen(this, target));
                        }
                    }
            ).dimensions(bStartX + (btnWidth + btnSpacing), bY, btnWidth, 20).build());

            // Button 3: Web Uploader
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Web Server"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new WebGalleryScreen(this));
                        }
                    }
            ).dimensions(bStartX + (btnWidth + btnSpacing) * 2, bY, btnWidth, 20).build());

            // Button 4: Reset to Default Folder
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Default"),
                    btn -> handleDefaultFolder()
            ).dimensions(bStartX + (btnWidth + btnSpacing) * 3, bY, btnWidth, 20).build());

            // Button 5: Back
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Back"),
                    btn -> this.close()
            ).dimensions(bStartX + (btnWidth + btnSpacing) * 4, bY, btnWidth, 20).build());
        }
    }

    private void handleSelectFolder() {
        if (browseTarget == BrowseTarget.INVENTORY_GUI) {
            importAllImagesFromCurrentDir();
        } else {
            TotemManager.getInstance().setCustomImagesDir(this.currentDirectory);
            if (this.parent instanceof TotemManagerScreen tms) {
                tms.refreshList();
            }
            this.close();
        }
    }

    private void handleDefaultFolder() {
        if (browseTarget == BrowseTarget.INVENTORY_GUI) {
            Path invDir = TotemManager.getInstance().getInventoryGuiDir();
            if (invDir != null && Files.exists(invDir)) {
                navigateTo(invDir);
            }
            setStatus("§aNavigated to default inventory folder", false);
        } else {
            TotemManager.getInstance().setCustomImagesDir(null);
            if (this.parent instanceof TotemManagerScreen tms) {
                tms.refreshList();
            }
            this.close();
        }
    }

    public void importAllImagesFromCurrentDir() {
        if (currentDirectory == null || !Files.exists(currentDirectory)) return;
        try {
            int count = 0;
            try (Stream<Path> stream = Files.list(currentDirectory)) {
                List<Path> pngs = stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .toList();
                for (Path p : pngs) {
                    if (TotemManager.getInstance().importInventoryImage(p)) {
                        count++;
                    }
                }
            }
            if (count > 0) {
                setStatus("§aImported " + count + " image(s) to inventory!", false);
                if (this.parent instanceof InventoryGuiSettingsScreen igss) {
                    igss.refreshImageSelection();
                }
            } else {
                setStatus("§cNo .png images found in this folder.", true);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to import images from folder", e);
            setStatus("§cError: " + e.getMessage(), true);
        }
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
                    this.fileListWidget.setScrollY(0);
                    this.fileListWidget.refresh();
                }
            } else {
                setStatus("§cDirectory does not exist: " + dir, true);
            }
        } catch (Exception e) {
            setStatus("§cAccess error: " + e.getMessage(), true);
        }
    }

    public void navigateTo(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) return;
        try {
            Path p = Path.of(pathStr.trim());
            navigateTo(p);
        } catch (Exception e) {
            setStatus("§cInvalid path: " + e.getMessage(), true);
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
        if (browseTarget == BrowseTarget.INVENTORY_GUI) {
            selectInventoryImage(file);
            return;
        }
        try {
            TotemEntry entry = TotemManager.getInstance().addTotem(file);
            if (entry != null) {
                setStatus("§aSuccessfully added Totem: " + file.getFileName().toString(), false);
                if (this.parent instanceof TotemManagerScreen tms) {
                    tms.refreshList();
                }
            } else {
                setStatus("§cCould not load: " + file.getFileName().toString(), true);
            }
        } catch (Exception e) {
            setStatus("§cError adding image: " + e.getMessage(), true);
        }
    }

    public void selectInventoryImage(Path file) {
        try {
            boolean success = TotemManager.getInstance().importInventoryImage(file);
            if (success) {
                setStatus("§aSelected inventory background: " + file.getFileName().toString(), false);
                if (this.parent instanceof InventoryGuiSettingsScreen igss) {
                    igss.refreshImageSelection();
                }
            } else {
                setStatus("§cCould not load: " + file.getFileName().toString(), true);
            }
        } catch (Exception e) {
            LOGGER.error("Error setting inventory image", e);
            setStatus("§cError: " + e.getMessage(), true);
        }
    }

    public void enableDisabledImage(Path file) {
        try {
            boolean success = TotemManager.getInstance().enableDisabledTotem(file);
            if (success) {
                setStatus("§aSuccessfully re-enabled Totem: " + file.getFileName().toString(), false);
                if (this.parent instanceof TotemManagerScreen tms) {
                    tms.refreshList();
                }
                if (this.fileListWidget != null) {
                    this.fileListWidget.refresh();
                }
            } else {
                setStatus("§cCould not re-enable: " + file.getFileName().toString(), true);
            }
        } catch (Exception e) {
            setStatus("§cError re-enabling image: " + e.getMessage(), true);
        }
    }

    public void setStatus(String message, boolean isError) {
        this.statusMessage = message;
        this.statusColor = isError ? 0xFFFF5555 : 0xFF55FF55;
        this.statusExpireTime = System.currentTimeMillis() + 4000;
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 7, 0xFFFFFFFF);

        // Status or Active Folder display above bottom buttons
        if (System.currentTimeMillis() < statusExpireTime && !statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusMessage), this.width / 2, this.height - 42, statusColor);
        } else if (browseTarget == BrowseTarget.INVENTORY_GUI) {
            String cur = TotemManager.getInstance().getInventoryGuiSettings().getImageFileName();
            if (cur == null || cur.trim().isEmpty()) {
                cur = "None";
            }
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§7Current inventory background: §e" + cur),
                    this.width / 2,
                    this.height - 42,
                    0xFFAAAAAA
            );
        } else {
            Path active = TotemManager.getInstance().getImagesDir();
            String activeStr = active != null ? active.toString() : "Default";
            if (this.textRenderer.getWidth("Current Source: " + activeStr) > this.width - 20) {
                activeStr = "..." + activeStr.substring(Math.max(0, activeStr.length() - 40));
            }
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§7Current image source: §e" + activeStr),
                    this.width / 2,
                    this.height - 42,
                    0xFFAAAAAA
            );
        }
    }

    public Path getCurrentDirectory() {
        return currentDirectory;
    }

    private static List<Shortcut> getShortcuts() {
        List<Shortcut> list = new ArrayList<>();

        // 1. Mod Default
        list.add(new Shortcut("Mod", TotemManager.getInstance().getDefaultImagesDir()));

        // 2. Mod Inventory Folder
        list.add(new Shortcut("Inventory", TotemManager.getInstance().getInventoryGuiDir()));

        // 3. Android Download/CustomTotem (Auto created/checked)
        Path dlTotem = resolveFirstExisting(
                Path.of("/storage/emulated/0/Download/CustomTotem"),
                Path.of("/sdcard/Download/CustomTotem"),
                Path.of(System.getProperty("user.home", ""), "Downloads/CustomTotem")
        );
        if (dlTotem != null) {
            list.add(new Shortcut("DL/Totem", dlTotem));
        }

        // 4. Android / PC Downloads
        Path dl = resolveFirstExisting(
                Path.of("/storage/emulated/0/Download"),
                Path.of("/sdcard/Download"),
                Path.of(System.getProperty("user.home", ""), "Downloads")
        );
        if (dl != null) {
            list.add(new Shortcut("Downloads", dl));
        }

        // 5. Android Pictures
        Path pic = resolveFirstExisting(
                Path.of("/storage/emulated/0/Pictures"),
                Path.of("/sdcard/Pictures"),
                Path.of(System.getProperty("user.home", ""), "Pictures")
        );
        if (pic != null) {
            list.add(new Shortcut("Pictures", pic));
        }

        // 6. Android DCIM (Camera)
        Path dcim = resolveFirstExisting(
                Path.of("/storage/emulated/0/DCIM"),
                Path.of("/sdcard/DCIM")
        );
        if (dcim != null) {
            list.add(new Shortcut("DCIM", dcim));
        }

        // 7. Game Root
        list.add(new Shortcut("Game", FabricLoader.getInstance().getGameDir()));

        return list;
    }

    private static Path resolveFirstExisting(Path... candidates) {
        for (Path p : candidates) {
            try {
                if (p != null) {
                    if (Files.exists(p) && Files.isDirectory(p)) {
                        return p.toAbsolutePath().normalize();
                    } else if (p.getParent() != null && Files.exists(p.getParent()) && p.toString().contains("CustomTotem")) {
                        Files.createDirectories(p);
                        return p.toAbsolutePath().normalize();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private record Shortcut(String name, Path path) {}

    // =========================================================================
    // File List Widget with Touch Drag Scrolling
    // =========================================================================
    public static class FileListWidget extends ElementListWidget<FileListWidget.FileItemWidget> {
        private final FolderBrowserScreen screen;

        public FileListWidget(MinecraftClient client, int width, int height, int y, int itemHeight, FolderBrowserScreen screen) {
            super(client, width, height, y, itemHeight);
            this.screen = screen;
            refresh();
        }

        public void scroll(double amount) {
            setScrollY(getScrollY() + amount);
        }

        @Override
        public boolean mouseDragged(Click click, double deltaX, double deltaY) {
            if (super.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
            if (overflows()) {
                setScrollY(getScrollY() - deltaY);
                return true;
            }
            return false;
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float tickDelta) {
            super.renderWidget(context, mouseX, mouseY, tickDelta);
            if (this.children().isEmpty()) {
                TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                context.drawCenteredTextWithShadow(
                        tr,
                        Text.literal("§7This folder is empty or has no .png images"),
                        this.getX() + this.getWidth() / 2,
                        this.getY() + this.getHeight() / 2 - 8,
                        0xFFAAAAAA
                );
                context.drawCenteredTextWithShadow(
                        tr,
                        Text.literal("§eClick 'Download' below to paste an image URL directly"),
                        this.getX() + this.getWidth() / 2,
                        this.getY() + this.getHeight() / 2 + 6,
                        0xFFFFFF55
                );
            }
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
                this.addEntry(new FileItemWidget(parent, ".. [Parent Folder]", true, true, false));
            }

            List<Path> subDirs = new ArrayList<>();
            List<Path> pngFiles = new ArrayList<>();
            List<Path> disabledFiles = new ArrayList<>();

            try (Stream<Path> stream = Files.list(current)) {
                stream.forEach(p -> {
                    try {
                        String name = p.getFileName().toString();
                        if (name.startsWith(".") || name.startsWith(".trashed")) {
                            return; // Ignore hidden or trash files!
                        }
                        if (Files.isDirectory(p) && !Files.isHidden(p)) {
                            subDirs.add(p);
                        } else if (name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                            pngFiles.add(p);
                        } else if (name.toLowerCase(Locale.ROOT).endsWith(".disable")) {
                            disabledFiles.add(p);
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                LOGGER.error("Failed to list files in {}", current, e);
            }

            subDirs.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            for (Path dir : subDirs) {
                this.addEntry(new FileItemWidget(dir, dir.getFileName().toString(), true, false, false));
            }

            pngFiles.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            for (Path file : pngFiles) {
                this.addEntry(new FileItemWidget(file, file.getFileName().toString(), false, false, false));
            }

            disabledFiles.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            for (Path file : disabledFiles) {
                this.addEntry(new FileItemWidget(file, file.getFileName().toString(), false, false, true));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(100, this.width - 24);
        }

        public class FileItemWidget extends ElementListWidget.Entry<FileItemWidget> {
            private final Path path;
            private final String displayName;
            private final boolean isDirectory;
            private final boolean isParent;
            private final boolean isDisabled;
            private final ButtonWidget actionButton;
            private final List<ClickableWidget> children = new ArrayList<>();

            public FileItemWidget(Path path, String displayName, boolean isDirectory, boolean isParent, boolean isDisabled) {
                this.path = path;
                this.displayName = displayName;
                this.isDirectory = isDirectory;
                this.isParent = isParent;
                this.isDisabled = isDisabled;

                if (isParent) {
                    this.actionButton = ButtonWidget.builder(
                            Text.literal("Up"),
                            btn -> screen.navigateUp()
                    ).dimensions(0, 0, 46, 20).build();
                } else if (isDirectory) {
                    this.actionButton = ButtonWidget.builder(
                            Text.literal("Open"),
                            btn -> screen.navigateTo(path)
                    ).dimensions(0, 0, 46, 20).build();
                } else if (isDisabled) {
                    this.actionButton = ButtonWidget.builder(
                            Text.literal("Enable"),
                            btn -> screen.enableDisabledImage(path)
                    ).dimensions(0, 0, 46, 20).build();
                } else {
                    String btnText = (screen.getBrowseTarget() == BrowseTarget.INVENTORY_GUI) ? "Select" : "Add";
                    this.actionButton = ButtonWidget.builder(
                            Text.literal(btnText),
                            btn -> screen.importImage(path)
                    ).dimensions(0, 0, 46, 20).build();
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

                // Clean ARGB prefix colors with full alpha (0xFF...) so Minecraft doesn't drop the text
                String prefix;
                int prefixColor;
                if (isParent) {
                    prefix = "§6[UP] ";
                    prefixColor = 0xFFFFAA00;
                } else if (isDirectory) {
                    prefix = "§e[DIR] ";
                    prefixColor = 0xFFFFFF55;
                } else if (isDisabled) {
                    prefix = "§8[OFF] ";
                    prefixColor = 0xFFAAAAAA;
                } else {
                    prefix = "§b[IMG] ";
                    prefixColor = 0xFF55FFFF;
                }

                String label = prefix + displayName + (isDirectory && !isParent ? "/" : "");
                int maxTextWidth = Math.max(30, width - 54);
                if (textRenderer.getWidth(label) > maxTextWidth) {
                    label = textRenderer.trimToWidth(label, Math.max(10, maxTextWidth - 8)) + "...";
                }
                context.drawTextWithShadow(textRenderer, Text.literal(label), x + 4, y + 6, prefixColor);

                // Render Action Button
                actionButton.setX(x + width - 48);
                actionButton.setY(y + 2);
                actionButton.render(context, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(Click click, boolean focused) {
                // Only button clicks trigger actions; row clicking does not, allowing smooth touch drag scrolling!
                return actionButton.mouseClicked(click, focused);
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
