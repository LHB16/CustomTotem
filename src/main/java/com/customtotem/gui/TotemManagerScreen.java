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

    @Override
    protected void init() {
        int topBarY = 28;
        int btnWidth = 105;
        int spacing = 6;
        int totalTopBarWidth = (btnWidth * 4) + (spacing * 3);
        int startX = (this.width - totalTopBarWidth) / 2;

        // Button 1: Upload Image (using TinyFileDialogs on background thread)
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📁 Upload Image"),
                btn -> openFileDialog()
        ).dimensions(startX, topBarY, btnWidth, 20).build());

        // Button 2: Switch Totem Pop Mode (SINGLE / SEQUENTIAL / RANDOM)
        modeButton = ButtonWidget.builder(
                getModeButtonText(),
                btn -> {
                    TotemManager.getInstance().setMode(TotemManager.getInstance().getMode().next());
                    btn.setMessage(getModeButtonText());
                }
        ).dimensions(startX + (btnWidth + spacing), topBarY, btnWidth, 20).build();
        this.addDrawableChild(modeButton);

        // Button 3: Display Settings (Model / Mini Totem)
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("⚙ Display Model"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new DisplaySettingsScreen(this));
                    }
                }
        ).dimensions(startX + (btnWidth + spacing) * 2, topBarY, btnWidth, 20).build());

        // Button 4: Open Images Folder in Windows Explorer
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📂 Open Folder"),
                btn -> Util.getOperatingSystem().open(TotemManager.getInstance().getImagesDir())
        ).dimensions(startX + (btnWidth + spacing) * 3, topBarY, btnWidth, 20).build());

        // Center List Widget
        int listY = 56;
        int listHeight = this.height - listY - 38;
        this.listWidget = new TotemListWidget(this.client, this.width, listHeight, listY, 32, this);
        this.addDrawableChild(listWidget);

        // Done button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                btn -> this.close()
        ).dimensions(this.width / 2 - 75, this.height - 26, 150, 20).build());
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
        CompletableFuture.supplyAsync(() -> {
            try {
                return TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Totem PNG Image",
                        "",
                        null,
                        "PNG Images (*.png)",
                        false
                );
            } catch (Exception e) {
                return null;
            }
        }).thenAccept(filePath -> {
            if (filePath != null && !filePath.trim().isEmpty()) {
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        // Empty state message
        if (TotemManager.getInstance().getEntries().isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§7No custom totems found. Click 'Upload Image' or drag PNG files here!"),
                    this.width / 2,
                    this.height / 2 - 10,
                    0xAAAAAA
            );
        }

        // Drag & drop hint at the bottom
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§8[Tip: Drag & Drop any .png image directly into this window!]"),
                this.width / 2,
                this.height - 36,
                0x888888
        );
    }
}
