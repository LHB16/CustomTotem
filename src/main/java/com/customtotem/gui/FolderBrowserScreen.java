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

    private final Screen parent;
    private Path currentDirectory;
    private TextFieldWidget pathField;
    private FileListWidget fileListWidget;

    private String statusMessage = "";
    private int statusColor = 0xFF55FF55;
    private long statusExpireTime = 0;

    public FolderBrowserScreen(Screen parent) {
        this(parent, null);
    }

    public FolderBrowserScreen(Screen parent, Path initialDir) {
        super(Text.literal("Trình Duyệt Tệp & Thư Mục Totem"));
        this.parent = parent;
        if (initialDir != null && Files.exists(initialDir)) {
            this.currentDirectory = initialDir.toAbsolutePath().normalize();
        } else {
            Path initial = TotemManager.getInstance().getImagesDir();
            if (initial != null && Files.exists(initial)) {
                this.currentDirectory = initial.toAbsolutePath().normalize();
            } else {
                this.currentDirectory = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            }
        }
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

        // --- Row 3 & 4: File List & Bottom Action Buttons ---
        boolean twoRows = this.width < 440;
        int bottomReserve = twoRows ? 54 : 32;
        int listY = 70;
        int listHeight = Math.max(40, this.height - listY - bottomReserve);

        this.fileListWidget = new FileListWidget(this.client, this.width - 34, listHeight, listY, 26, this);
        this.fileListWidget.setX(10);
        this.addDrawableChild(fileListWidget);

        // Scroll helper buttons on the right side
        int scrollBtnX = this.width - 24;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▲"),
                btn -> fileListWidget.scroll(-80)
        ).dimensions(scrollBtnX, listY + 2, 18, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▼"),
                btn -> fileListWidget.scroll(80)
        ).dimensions(scrollBtnX, listY + listHeight - 22, 18, 20).build());

        // --- Row 4: Bottom Action Buttons ---
        if (twoRows) {
            int row1Y = this.height - 48;
            int row2Y = this.height - 25;
            int btnSpacing = 4;

            // Row 1: 3 buttons (Dùng Thư Mục, Tải Link, Web)
            int btn3W = (this.width - 20 - (btnSpacing * 2)) / 3;
            int startX1 = (this.width - ((btn3W * 3) + (btnSpacing * 2))) / 2;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("✔ Dùng Thư Mục"),
                    btn -> {
                        TotemManager.getInstance().setCustomImagesDir(this.currentDirectory);
                        if (this.parent instanceof TotemManagerScreen tms) {
                            tms.refreshList();
                        }
                        this.close();
                    }
            ).dimensions(startX1, row1Y, btn3W, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("🌐 Tải Link"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new DownloadUrlScreen(this));
                        }
                    }
            ).dimensions(startX1 + btn3W + btnSpacing, row1Y, btn3W, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("📱 Web WiFi"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new WebGalleryScreen(this));
                        }
                    }
            ).dimensions(startX1 + (btn3W + btnSpacing) * 2, row1Y, btn3W, 20).build());

            // Row 2: 2 buttons (Mặc Định, Quay Lại)
            int btn2W = (this.width - 20 - btnSpacing) / 2;
            int startX2 = (this.width - ((btn2W * 2) + btnSpacing)) / 2;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("↺ Mặc Định"),
                    btn -> {
                        TotemManager.getInstance().setCustomImagesDir(null);
                        if (this.parent instanceof TotemManagerScreen tms) {
                            tms.refreshList();
                        }
                        this.close();
                    }
            ).dimensions(startX2, row2Y, btn2W, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Quay Lại"),
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

            // Button 1: Set as Totem Folder
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("✔ Dùng Thư Mục"),
                    btn -> {
                        TotemManager.getInstance().setCustomImagesDir(this.currentDirectory);
                        if (this.parent instanceof TotemManagerScreen tms) {
                            tms.refreshList();
                        }
                        this.close();
                    }
            ).dimensions(bStartX, bY, btnWidth, 20).build());

            // Button 2: Download by Link URL
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("🌐 Tải Link"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new DownloadUrlScreen(this));
                        }
                    }
            ).dimensions(bStartX + (btnWidth + btnSpacing), bY, btnWidth, 20).build());

            // Button 3: Web Uploader
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("📱 Web (PC/WiFi)"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new WebGalleryScreen(this));
                        }
                    }
            ).dimensions(bStartX + (btnWidth + btnSpacing) * 2, bY, btnWidth, 20).build());

            // Button 4: Reset to Mod Default Folder
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("↺ Mặc Định"),
                    btn -> {
                        TotemManager.getInstance().setCustomImagesDir(null);
                        if (this.parent instanceof TotemManagerScreen tms) {
                            tms.refreshList();
                        }
                        this.close();
                    }
            ).dimensions(bStartX + (btnWidth + btnSpacing) * 3, bY, btnWidth, 20).build());

            // Button 5: Back
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Quay Lại"),
                    btn -> this.close()
            ).dimensions(bStartX + (btnWidth + btnSpacing) * 4, bY, btnWidth, 20).build());
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
                setStatus("§cThư mục không tồn tại: " + dir, true);
            }
        } catch (Exception e) {
            setStatus("§cLỗi truy cập: " + e.getMessage(), true);
        }
    }

    public void navigateTo(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) return;
        try {
            Path p = Path.of(pathStr.trim());
            navigateTo(p);
        } catch (Exception e) {
            setStatus("§cĐường dẫn không hợp lệ: " + e.getMessage(), true);
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
                setStatus("§a✓ Đã thêm Totem: " + file.getFileName().toString(), false);
                if (this.parent instanceof TotemManagerScreen tms) {
                    tms.refreshList();
                }
            } else {
                setStatus("§c✗ Không thể nạp: " + file.getFileName().toString(), true);
            }
        } catch (Exception e) {
            setStatus("§cLỗi thêm ảnh: " + e.getMessage(), true);
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
        } else {
            Path active = TotemManager.getInstance().getImagesDir();
            String activeStr = active != null ? active.toString() : "Mặc định";
            if (this.textRenderer.getWidth("Nguồn ảnh: " + activeStr) > this.width - 20) {
                activeStr = "..." + activeStr.substring(Math.max(0, activeStr.length() - 40));
            }
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§7Nguồn ảnh hiện tại: §e" + activeStr),
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
        list.add(new Shortcut("📁 Mod", TotemManager.getInstance().getDefaultImagesDir()));

        // 2. Mod Inventory Folder
        list.add(new Shortcut("🎒 Túi Đồ", TotemManager.getInstance().getInventoryGuiDir()));

        // 3. Android Download/CustomTotem (Auto created/checked)
        Path dlTotem = resolveFirstExisting(
                Path.of("/storage/emulated/0/Download/CustomTotem"),
                Path.of("/sdcard/Download/CustomTotem"),
                Path.of(System.getProperty("user.home", ""), "Downloads/CustomTotem")
        );
        if (dlTotem != null) {
            list.add(new Shortcut("📥 DL/Totem", dlTotem));
        }

        // 4. Android / PC Downloads
        Path dl = resolveFirstExisting(
                Path.of("/storage/emulated/0/Download"),
                Path.of("/sdcard/Download"),
                Path.of(System.getProperty("user.home", ""), "Downloads")
        );
        if (dl != null) {
            list.add(new Shortcut("📥 Tải Về", dl));
        }

        // 5. Android Pictures
        Path pic = resolveFirstExisting(
                Path.of("/storage/emulated/0/Pictures"),
                Path.of("/sdcard/Pictures"),
                Path.of(System.getProperty("user.home", ""), "Pictures")
        );
        if (pic != null) {
            list.add(new Shortcut("🖼 Ảnh", pic));
        }

        // 6. Android DCIM (Camera)
        Path dcim = resolveFirstExisting(
                Path.of("/storage/emulated/0/DCIM"),
                Path.of("/sdcard/DCIM")
        );
        if (dcim != null) {
            list.add(new Shortcut("📸 DCIM", dcim));
        }

        // 7. Game Root
        list.add(new Shortcut("🎮 Game", FabricLoader.getInstance().getGameDir()));

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
                        Text.literal("§7Thư mục này trống hoặc chưa có file ảnh .png"),
                        this.getX() + this.getWidth() / 2,
                        this.getY() + this.getHeight() / 2 - 8,
                        0xFFAAAAAA
                );
                context.drawCenteredTextWithShadow(
                        tr,
                        Text.literal("§e💡 Bấm nút '🌐 Tải Link' bên dưới để dán link ảnh trực tiếp"),
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
                this.addEntry(new FileItemWidget(parent, ".. [Thư Mục Cha]", true, true));
            }

            List<Path> subDirs = new ArrayList<>();
            List<Path> pngFiles = new ArrayList<>();

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
            return Math.max(100, this.width - 24);
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
                            Text.literal("▲ Lên"),
                            btn -> screen.navigateUp()
                    ).dimensions(0, 0, 46, 20).build();
                } else if (isDirectory) {
                    this.actionButton = ButtonWidget.builder(
                            Text.literal("Mở"),
                            btn -> screen.navigateTo(path)
                    ).dimensions(0, 0, 46, 20).build();
                } else {
                    this.actionButton = ButtonWidget.builder(
                            Text.literal("+ Add"),
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
