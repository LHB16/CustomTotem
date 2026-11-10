package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.util.ImageDownloadUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.nio.file.Path;

public class DownloadUrlScreen extends Screen {
    public enum TargetType {
        TOTEM("Totem Cầm Tay"),
        INVENTORY_GUI("Giao Diện Túi Đồ");

        private final String displayName;

        TargetType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Screen parent;
    private TargetType targetType;

    private TextFieldWidget urlField;
    private TextFieldWidget nameField;
    private ButtonWidget targetButton;
    private ButtonWidget downloadButton;

    private boolean isDownloading = false;
    private String statusMessage = "";
    private int statusColor = 0xFFAAAAAA;

    public DownloadUrlScreen(Screen parent) {
        this(parent, TargetType.TOTEM);
    }

    public DownloadUrlScreen(Screen parent, TargetType targetType) {
        super(Text.literal("Tải Ảnh Từ Link URL Trực Tiếp"));
        this.parent = parent;
        this.targetType = targetType != null ? targetType : TargetType.TOTEM;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int cardWidth = Math.min(360, this.width - 30);
        int startX = centerX - cardWidth / 2;
        int currentY = 46;

        // Target Folder Switcher
        this.targetButton = ButtonWidget.builder(
                getTargetButtonText(),
                btn -> {
                    targetType = (targetType == TargetType.TOTEM) ? TargetType.INVENTORY_GUI : TargetType.TOTEM;
                    btn.setMessage(getTargetButtonText());
                }
        ).dimensions(startX, currentY, cardWidth, 20).build();
        this.addDrawableChild(targetButton);
        currentY += 28;

        // URL Label & Input Field
        this.urlField = new TextFieldWidget(
                this.textRenderer,
                startX,
                currentY + 12,
                cardWidth - 64,
                20,
                Text.literal("URL ảnh")
        );
        this.urlField.setMaxLength(2048);
        this.urlField.setPlaceholder(Text.literal("Dán link ảnh (.png/.jpg/.webp) vào đây..."));
        this.addDrawableChild(urlField);

        // Paste Button right next to URL field
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📋 Dán"),
                btn -> {
                    if (this.client != null) {
                        String clip = this.client.keyboard.getClipboard();
                        if (clip != null && !clip.trim().isEmpty()) {
                            urlField.setText(clip.trim());
                            setStatus("§aĐã dán link từ bộ nhớ tạm!", 0xFF55FF55);
                        }
                    }
                }
        ).dimensions(startX + cardWidth - 60, currentY + 12, 60, 20).build());
        currentY += 40;

        // Optional Name Field
        this.nameField = new TextFieldWidget(
                this.textRenderer,
                startX,
                currentY + 12,
                cardWidth,
                20,
                Text.literal("Tên file (Tùy chọn)")
        );
        this.nameField.setMaxLength(128);
        this.nameField.setPlaceholder(Text.literal("Tên gợi nhớ (để trống sẽ tự lấy tên ảnh)..."));
        this.addDrawableChild(nameField);
        currentY += 44;

        // Download Action Button
        this.downloadButton = ButtonWidget.builder(
                Text.literal("📥 Tải Về & Nạp Ngay"),
                btn -> startDownload()
        ).dimensions(startX, currentY, cardWidth, 22).build();
        this.addDrawableChild(downloadButton);
        currentY += 30;

        // Bottom Navigation Buttons
        int halfW = (cardWidth - 6) / 2;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📁 Mở Thư Mục"),
                btn -> {
                    Path target = getTargetDirectory();
                    if (this.client != null) {
                        this.client.setScreen(new FolderBrowserScreen(this, target));
                    }
                }
        ).dimensions(startX, currentY, halfW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Quay Lại"),
                btn -> this.close()
        ).dimensions(startX + halfW + 6, currentY, halfW, 20).build());
    }

    private Text getTargetButtonText() {
        String color = (targetType == TargetType.TOTEM) ? "§6" : "§b";
        return Text.literal("Mục tiêu lưu ảnh: " + color + targetType.getDisplayName());
    }

    private Path getTargetDirectory() {
        if (targetType == TargetType.INVENTORY_GUI) {
            return TotemManager.getInstance().getInventoryGuiDir();
        } else {
            return TotemManager.getInstance().getImagesDir();
        }
    }

    private void startDownload() {
        if (isDownloading) return;
        String url = (urlField != null) ? urlField.getText().trim() : "";
        if (url.isEmpty()) {
            setStatus("§c✗ Vui lòng dán link URL ảnh trước khi bấm tải!", 0xFFFF5555);
            return;
        }

        String customName = (nameField != null) ? nameField.getText().trim() : "";
        Path targetDir = getTargetDirectory();

        isDownloading = true;
        if (downloadButton != null) {
            downloadButton.active = false;
        }
        setStatus("§e⏳ Đang tải ảnh về máy, vui lòng đợi...", 0xFFFFFF55);

        ImageDownloadUtil.downloadImageAsync(url, targetDir, customName).thenAccept(result -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> {
                    isDownloading = false;
                    if (downloadButton != null) {
                        downloadButton.active = true;
                    }

                    if (result.success()) {
                        setStatus("§a✔ Tải thành công: " + result.fileName() + "! Đã nạp vào game.", 0xFF55FF55);
                        if (urlField != null) {
                            urlField.setText("");
                        }
                        if (nameField != null) {
                            nameField.setText("");
                        }

                        // Rescan in game
                        if (targetType == TargetType.INVENTORY_GUI) {
                            TotemManager.getInstance().scanInventoryImages();
                            TotemManager.getInstance().loadInventoryGuiTexture();
                        } else {
                            TotemManager.getInstance().rescanImages();
                        }

                        if (parent instanceof TotemManagerScreen tms) {
                            tms.refreshList();
                        }
                    } else {
                        setStatus("§c✗ " + result.errorMessage(), 0xFFFF5555);
                    }
                });
            }
        });
    }

    private void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.key() == 257) { // Enter
            startDownload();
            return true;
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

        // Header
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§7Tải trực tiếp ảnh từ Discord, Imgur, Pinterest, Facebook... mà không cần mở trình duyệt"),
                this.width / 2,
                28,
                0xFFAAAAAA
        );

        int centerX = this.width / 2;
        int cardWidth = Math.min(360, this.width - 30);
        int startX = centerX - cardWidth / 2;

        // Field Labels
        context.drawTextWithShadow(this.textRenderer, Text.literal("§fĐường link ảnh trực tiếp (URL):"), startX, 74, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§7Tên file muốn lưu (tùy chọn):"), startX, 114, 0xFFAAAAAA);

        // Status Message
        if (!statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(statusMessage),
                    this.width / 2,
                    this.height - 34,
                    statusColor
            );
        }
    }
}
