package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.InventoryGuiSettings;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.List;

public class InventoryGuiSettingsScreen extends Screen {
    private final Screen parent;
    private final InventoryGuiSettings settings;

    private ButtonWidget toggleButton;
    private ButtonWidget modeButton;
    private ButtonWidget slotsOverlayButton;
    private ButtonWidget selectImageButton;

    public InventoryGuiSettingsScreen(Screen parent) {
        super(Text.literal("Cài Đặt Ảnh Nền Túi Đồ (Inventory GUI)"));
        this.parent = parent;
        this.settings = TotemManager.getInstance().getInventoryGuiSettings();
    }

    @Override
    protected void init() {
        TotemManager.getInstance().scanInventoryImages();

        int centerX = this.width / 2;
        int startY = 36;
        int fullWidth = 240;
        int rowGap = 24;

        // Button 1: Toggle Enabled
        toggleButton = ButtonWidget.builder(
                getToggleText(),
                btn -> {
                    settings.setEnabled(!settings.isEnabled());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getToggleText());
                }
        ).dimensions(centerX - 120, startY, fullWidth, 20).build();
        this.addDrawableChild(toggleButton);

        // Button 2: Select / Cycle Image
        List<String> images = TotemManager.getInstance().getAvailableInventoryImages();
        int cycleBtnWidth = 24;
        int imgSelectWidth = fullWidth - (cycleBtnWidth * 2) - 4;

        // Prev Image Button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("◀"),
                btn -> cycleImage(-1)
        ).dimensions(centerX - 120, startY + rowGap, cycleBtnWidth, 20).build());

        // Image Name Button (Click to next)
        selectImageButton = ButtonWidget.builder(
                getImageButtonText(),
                btn -> cycleImage(1)
        ).dimensions(centerX - 120 + cycleBtnWidth + 2, startY + rowGap, imgSelectWidth, 20).build();
        this.addDrawableChild(selectImageButton);

        // Next Image Button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▶"),
                btn -> cycleImage(1)
        ).dimensions(centerX + 120 - cycleBtnWidth, startY + rowGap, cycleBtnWidth, 20).build());

        // Button 3: Display Mode (FRAME / FULLSCREEN)
        modeButton = ButtonWidget.builder(
                Text.literal("Chế độ: §b" + settings.getMode().getDisplayName()),
                btn -> {
                    settings.setMode(settings.getMode().next());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(Text.literal("Chế độ: §b" + settings.getMode().getDisplayName()));
                }
        ).dimensions(centerX - 120, startY + (rowGap * 2), fullWidth, 20).build();
        this.addDrawableChild(modeButton);

        // Button 4: Vanilla Slots Overlay
        slotsOverlayButton = ButtonWidget.builder(
                getSlotsOverlayText(),
                btn -> {
                    settings.setShowVanillaSlots(!settings.isShowVanillaSlots());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getSlotsOverlayText());
                }
        ).dimensions(centerX - 120, startY + (rowGap * 3), fullWidth, 20).build();
        this.addDrawableChild(slotsOverlayButton);

        // Slider: Opacity (10% to 100%)
        this.addDrawableChild(new SliderWidget(
                centerX - 120, startY + (rowGap * 4), fullWidth, 20,
                Text.literal(String.format("Độ mờ: §a%d%%", Math.round(settings.getOpacity() * 100))),
                (settings.getOpacity() - 0.1) / (1.0 - 0.1)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("Độ mờ: §a%d%%", Math.round(settings.getOpacity() * 100))));
            }

            @Override
            protected void applyValue() {
                float val = (float) (0.1 + this.value * 0.9);
                settings.setOpacity(Math.round(val * 100.0f) / 100.0f);
                TotemManager.getInstance().saveConfig();
            }
        });

        // Row 5: Open Folder & Rescan
        int halfWidth = 118;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📁 Mở Thư Mục"),
                btn -> Util.getOperatingSystem().open(TotemManager.getInstance().getInventoryGuiDir())
        ).dimensions(centerX - 120, startY + (rowGap * 5), halfWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🔄 Quét Lại"),
                btn -> {
                    TotemManager.getInstance().scanInventoryImages();
                    TotemManager.getInstance().loadInventoryGuiTexture();
                    selectImageButton.setMessage(getImageButtonText());
                }
        ).dimensions(centerX + 2, startY + (rowGap * 5), halfWidth, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Quay lại (Back)"),
                btn -> this.close()
        ).dimensions(centerX - 120, this.height - 28, fullWidth, 20).build());
    }

    private void cycleImage(int delta) {
        List<String> images = TotemManager.getInstance().getAvailableInventoryImages();
        if (images.isEmpty()) {
            return;
        }

        String current = settings.getImageFileName();
        int currentIdx = current != null ? images.indexOf(current) : -1;
        int nextIdx;
        if (currentIdx == -1) {
            nextIdx = delta > 0 ? 0 : images.size() - 1;
        } else {
            nextIdx = (currentIdx + delta + images.size()) % images.size();
        }

        String nextImg = images.get(nextIdx);
        TotemManager.getInstance().setInventoryGuiImage(nextImg);
        if (selectImageButton != null) {
            selectImageButton.setMessage(getImageButtonText());
        }
    }

    private Text getToggleText() {
        return Text.literal("Custom GUI Túi Đồ: " + (settings.isEnabled() ? "§aBẬT" : "§cTẮT"));
    }

    private Text getSlotsOverlayText() {
        return Text.literal("Khung Ô Đồ (Vanilla Slots): " + (settings.isShowVanillaSlots() ? "§aHIỆN" : "§7ẨN"));
    }

    private Text getImageButtonText() {
        String name = settings.getImageFileName();
        if (name == null || name.trim().isEmpty()) {
            List<String> list = TotemManager.getInstance().getAvailableInventoryImages();
            if (list.isEmpty()) {
                return Text.literal("§7[Chưa có ảnh trong thư mục]");
            }
            return Text.literal("§eBấm để chọn ảnh (" + list.size() + ")");
        }
        if (name.length() > 22) {
            name = name.substring(0, 19) + "...";
        }
        return Text.literal("Ảnh: §e" + name);
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);

        // Preview box
        int centerX = this.width / 2;
        int previewY = 36 + (24 * 6) + 4;
        int previewH = Math.max(20, this.height - previewY - 36);
        int previewW = Math.round(previewH * (176.0f / 166.0f));
        int previewX = centerX - (previewW / 2);

        if (previewH >= 30) {
            // Background box
            context.fill(previewX - 2, previewY - 2, previewX + previewW + 2, previewY + previewH + 2, 0x88000000);

            Identifier texture = TotemManager.getInstance().getInventoryGuiTexture();
            if (texture != null && settings.isEnabled()) {
                int alpha = Math.max(25, Math.min(255, (int) (settings.getOpacity() * 255)));
                int color = (alpha << 24) | 0x00FFFFFF;
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        previewX, previewY, 0.0f, 0.0f, previewW, previewH,
                        previewW, previewH, color);

                if (settings.isShowVanillaSlots()) {
                    int slotColor = (150 << 24) | 0x00FFFFFF;
                    context.drawTexture(RenderPipelines.GUI_TEXTURED,
                            Identifier.ofVanilla("textures/gui/container/inventory.png"),
                            previewX, previewY, 0.0f, 0.0f, previewW, previewH,
                            256, 256, slotColor);
                }
            } else {
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("§7[Xem trước nền túi đồ]"),
                        centerX, previewY + (previewH / 2) - 4, 0xAAAAAA);
            }
        }
    }
}
