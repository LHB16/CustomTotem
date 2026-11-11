package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TotemManagerScreen extends Screen {
    private final Screen parent;
    private TotemListWidget listWidget;
    private ButtonWidget modeButton;

    public TotemManagerScreen(Screen parent) {
        super(Text.translatable("gui.customtotem.title"));
        this.parent = parent;
    }

    private int folderLabelY = 46;

    @Override
    protected void init() {
        boolean twoRows = this.width < 480;
        int listY;

        if (twoRows) {
            int row1Y = 18;
            int row2Y = 40;
            int btnSpacing = 4;
            int btnCount = 3;
            int btnWidth = Math.min(130, (this.width - 20 - (btnSpacing * (btnCount - 1))) / btnCount);
            int totalRowWidth = (btnWidth * btnCount) + (btnSpacing * (btnCount - 1));
            int startX = (this.width - totalRowWidth) / 2;

            // Row 1: Folder, Download, Rescan
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Folder"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new FolderBrowserScreen(this));
                        }
                    }
            ).dimensions(startX, row1Y, btnWidth, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Download"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new DownloadUrlScreen(this));
                        }
                    }
            ).dimensions(startX + (btnWidth + btnSpacing), row1Y, btnWidth, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Rescan"),
                    btn -> TotemManager.getInstance().rescanImages(this::refreshList)
            ).dimensions(startX + (btnWidth + btnSpacing) * 2, row1Y, btnWidth, 20).build());

            // Row 2: Mode, Display, Inventory
            modeButton = ButtonWidget.builder(
                    getModeButtonText(),
                    btn -> {
                        TotemManager.getInstance().setMode(TotemManager.getInstance().getMode().next());
                        btn.setMessage(getModeButtonText());
                    }
            ).dimensions(startX, row2Y, btnWidth, 20).build();
            this.addDrawableChild(modeButton);

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Display"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new DisplaySettingsScreen(this));
                        }
                    }
            ).dimensions(startX + (btnWidth + btnSpacing), row2Y, btnWidth, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Inventory"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new InventoryGuiSettingsScreen(this));
                        }
                    }
            ).dimensions(startX + (btnWidth + btnSpacing) * 2, row2Y, btnWidth, 20).build());

            folderLabelY = 64;
            listY = 76;
        } else {
            int topBarY = 22;
            int btnSpacing = 4;
            int btnCount = 6;
            int btnWidth = Math.min(84, (this.width - 20 - (btnSpacing * (btnCount - 1))) / btnCount);
            int totalTopBarWidth = (btnWidth * btnCount) + (btnSpacing * (btnCount - 1));
            int startX = (this.width - totalTopBarWidth) / 2;

            // Button 1: Folder Browser
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Folder"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new FolderBrowserScreen(this));
                        }
                    }
            ).dimensions(startX, topBarY, btnWidth, 20).build());

            // Button 2: Download by URL
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Download"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new DownloadUrlScreen(this));
                        }
                    }
            ).dimensions(startX + (btnWidth + btnSpacing), topBarY, btnWidth, 20).build());

            // Button 3: Rescan current folder
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Rescan"),
                    btn -> TotemManager.getInstance().rescanImages(this::refreshList)
            ).dimensions(startX + (btnWidth + btnSpacing) * 2, topBarY, btnWidth, 20).build());

            // Button 4: Switch Totem Pop Mode
            modeButton = ButtonWidget.builder(
                    getModeButtonText(),
                    btn -> {
                        TotemManager.getInstance().setMode(TotemManager.getInstance().getMode().next());
                        btn.setMessage(getModeButtonText());
                    }
            ).dimensions(startX + (btnWidth + btnSpacing) * 3, topBarY, btnWidth, 20).build();
            this.addDrawableChild(modeButton);

            // Button 5: Display Settings
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Display"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new DisplaySettingsScreen(this));
                        }
                    }
            ).dimensions(startX + (btnWidth + btnSpacing) * 4, topBarY, btnWidth, 20).build());

            // Button 6: Inventory GUI Background Settings
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Inventory"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new InventoryGuiSettingsScreen(this));
                        }
                    }
            ).dimensions(startX + (btnWidth + btnSpacing) * 5, topBarY, btnWidth, 20).build());

            folderLabelY = 46;
            listY = 58;
        }

        // Center List Widget
        int listHeight = Math.max(40, this.height - listY - 36);
        this.listWidget = new TotemListWidget(this.client, this.width - 34, listHeight, listY, 32, this);
        this.listWidget.setX(10);
        this.addDrawableChild(listWidget);

        // Scroll helper buttons on the right side
        int scrollBtnX = this.width - 28;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Up"),
                btn -> listWidget.scroll(-96)
        ).dimensions(scrollBtnX, listY + 2, 24, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Down"),
                btn -> listWidget.scroll(96)
        ).dimensions(scrollBtnX, listY + listHeight - 22, 24, 20).build());

        // Clean RAM and Done buttons on the bottom
        int bottomBtnW = 90;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Clean RAM"),
                btn -> {
                    TotemManager.getInstance().unloadInactiveTotems();
                    refreshList();
                }
        ).dimensions(this.width / 2 - bottomBtnW - 4, this.height - 25, bottomBtnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                btn -> this.close()
        ).dimensions(this.width / 2 + 4, this.height - 25, bottomBtnW, 20).build());
    }

    private Text getModeButtonText() {
        TotemMode mode = TotemManager.getInstance().getMode();
        String color = switch (mode) {
            case SINGLE -> "§e";
            case SEQUENTIAL -> "§b";
            case RANDOM -> "§d";
        };
        return Text.literal("Mode: " + color + mode.getDisplayName());
    }

    private void openFileDialog() {
        if (com.customtotem.util.PlatformUtil.isAndroid()) {
            if (this.client != null) {
                this.client.setScreen(new FolderBrowserScreen(this));
            }
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Totem PNG Image",
                        TotemManager.getInstance().getImagesDir().toString(),
                        null,
                        "PNG Images (*.png)",
                        false
                );
            } catch (Throwable t) {
                return "__FALLBACK__";
            }
        }).thenAccept(filePath -> {
            if ("__FALLBACK__".equals(filePath)) {
                MinecraftClient.getInstance().execute(() -> {
                    if (this.client != null) {
                        this.client.setScreen(new FolderBrowserScreen(this));
                    }
                });
            } else if (filePath != null && !filePath.trim().isEmpty()) {
                Path path = Path.of(filePath);
                MinecraftClient.getInstance().execute(() -> {
                    TotemManager.getInstance().addTotem(path);
                    refreshList();
                });
            }
        });
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;
        boolean added = false;
        for (Path path : paths) {
            if (path.getFileName().toString().toLowerCase().endsWith(".png")) {
                TotemManager.getInstance().addTotem(path);
                added = true;
            }
        }
        if (added) {
            refreshList();
        }
    }

    public void refreshList() {
        if (listWidget != null) {
            listWidget.refreshEntries();
        }
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

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);

        // Active Folder Label
        Path activeFolder = TotemManager.getInstance().getImagesDir();
        String folderName = activeFolder != null ? activeFolder.toString() : "Default";
        if (this.textRenderer.getWidth("Folder: " + folderName) > this.width - 40) {
            folderName = "..." + folderName.substring(Math.max(0, folderName.length() - 40));
        }
        String tag = TotemManager.getInstance().isUsingCustomImagesDir() ? "§e[Custom] " : "§7[Default] ";
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Folder: " + tag + "§f" + folderName),
                this.width / 2,
                folderLabelY,
                0xFFAAAAAA
        );

        // Auto-refresh list when background loading completes
        if (!TotemManager.getInstance().isReloading() && listWidget != null && listWidget.children().size() != TotemManager.getInstance().getEntries().size()) {
            refreshList();
        }

        // Background loading indicator
        if (TotemManager.getInstance().isReloading()) {
            int cx = this.width / 2;
            int cy = this.height / 2;
            int boxW = 240;
            int boxH = 50;
            int bx1 = cx - boxW / 2;
            int by1 = cy - boxH / 2;
            int bx2 = cx + boxW / 2;
            int by2 = cy + boxH / 2;
            context.fill(bx1, by1, bx2, by2, 0xEE141414);
            // Border
            context.fill(bx1, by1, bx2, by1 + 1, 0xFF44AA77);
            context.fill(bx1, by2 - 1, bx2, by2, 0xFF44AA77);
            context.fill(bx1, by1, bx1 + 1, by2, 0xFF44AA77);
            context.fill(bx2 - 1, by1, bx2, by2, 0xFF44AA77);

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§a" + TotemManager.getInstance().getReloadStatus()),
                    cx,
                    cy - 14,
                    0xFFFFFFFF
            );

            // Progress bar
            int total = TotemManager.getInstance().getReloadTotal();
            int current = TotemManager.getInstance().getReloadCurrent();
            int barW = boxW - 24;
            int barH = 6;
            int barX = cx - barW / 2;
            int barY = cy + 2;
            context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
            if (total > 0) {
                int fillW = Math.min(barW, (int) ((float) current / (float) total * barW));
                context.fill(barX, barY, barX + fillW, barY + barH, 0xFF00DD88);
            }

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§7(Running in background, game is not frozen)"),
                    cx,
                    cy + 12,
                    0xFF888888
            );
        } else if (TotemManager.getInstance().getEntries().isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§7No Totem images found in current folder."),
                    this.width / 2,
                    this.height / 2 - 12,
                    0xFFAAAAAA
            );
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§eClick 'Folder' to browse files, or 'Download' to enter URL"),
                    this.width / 2,
                    this.height / 2 + 2,
                    0xFFFFFF55
            );
        }

        // Hint at the bottom
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§8[Tip: Drag & drop PNG files directly into this window to add them]"),
                this.width / 2,
                this.height - 36,
                0xFF888888
        );
    }
}
