package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.InventoryGuiSettings;
import com.customtotem.render.InventoryGuiRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class InventoryGuiSettingsScreen extends Screen {
    private static final int INV_WIDTH = 176;
    private static final int INV_HEIGHT = 166;

    private final Screen parent;
    private final InventoryGuiSettings settings;

    private ButtonWidget toggleButton;
    private ButtonWidget selectImageButton;
    private ButtonWidget fitModeButton;
    private ButtonWidget modeButton;
    private ButtonWidget slotsOverlayButton;
    private CustomSliderWidget opacitySlider;
    private CustomSliderWidget slotsOpacitySlider;
    private CustomSliderWidget zoomSlider;
    private CustomSliderWidget offsetXSlider;
    private CustomSliderWidget offsetYSlider;

    private int previewBoxX = 0;
    private int previewBoxY = 0;
    private boolean isDraggingPreview = false;

    public InventoryGuiSettingsScreen(Screen parent) {
        super(Text.literal("Cài Đặt Ảnh Nền Túi Đồ (Inventory GUI)"));
        this.parent = parent;
        this.settings = TotemManager.getInstance().getInventoryGuiSettings();
    }

    @Override
    protected void init() {
        TotemManager.getInstance().scanInventoryImages();

        int minSideBySideWidth = 390;
        boolean sideBySide = this.width >= minSideBySideWidth;

        int controlsW = sideBySide ? 190 : Math.min(220, this.width - 20);
        int totalW = sideBySide ? (controlsW + 16 + INV_WIDTH) : Math.max(controlsW, INV_WIDTH);
        int startX = (this.width - totalW) / 2;

        int controlsX = startX;
        int controlsY = 24;
        int rowGap = 21;

        previewBoxX = sideBySide ? (startX + controlsW + 16) : ((this.width - INV_WIDTH) / 2);
        previewBoxY = sideBySide ? Math.max(24, (this.height - INV_HEIGHT) / 2) : (controlsY + (rowGap * 9) + 4);

        // Row 0: Toggle Enabled
        toggleButton = ButtonWidget.builder(
                getToggleText(),
                btn -> {
                    settings.setEnabled(!settings.isEnabled());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getToggleText());
                }
        ).dimensions(controlsX, controlsY, controlsW, 20).build();
        this.addDrawableChild(toggleButton);

        // Row 1: Image Selection (◀, [Tên], ▶)
        int cycleBtnWidth = 20;
        int imgSelectWidth = controlsW - (cycleBtnWidth * 2) - 4;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("◀"),
                btn -> cycleImage(-1)
        ).dimensions(controlsX, controlsY + rowGap, cycleBtnWidth, 20).build());

        selectImageButton = ButtonWidget.builder(
                getImageButtonText(),
                btn -> cycleImage(1)
        ).dimensions(controlsX + cycleBtnWidth + 2, controlsY + rowGap, imgSelectWidth, 20).build();
        this.addDrawableChild(selectImageButton);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▶"),
                btn -> cycleImage(1)
        ).dimensions(controlsX + controlsW - cycleBtnWidth, controlsY + rowGap, cycleBtnWidth, 20).build());

        // Row 2: Fit / Crop Mode (CROP, STRETCH, FIT)
        fitModeButton = ButtonWidget.builder(
                getFitModeText(),
                btn -> {
                    settings.setFitMode(settings.getFitMode().next());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getFitModeText());
                }
        ).dimensions(controlsX, controlsY + rowGap * 2, controlsW, 20).build();
        this.addDrawableChild(fitModeButton);

        // Row 3: Display Mode (FRAME / FULLSCREEN)
        modeButton = ButtonWidget.builder(
                getModeText(),
                btn -> {
                    settings.setMode(settings.getMode().next());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getModeText());
                }
        ).dimensions(controlsX, controlsY + rowGap * 3, controlsW, 20).build();
        this.addDrawableChild(modeButton);

        // Row 4: Vanilla Slots Overlay
        slotsOverlayButton = ButtonWidget.builder(
                getSlotsOverlayText(),
                btn -> {
                    settings.setShowVanillaSlots(!settings.isShowVanillaSlots());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getSlotsOverlayText());
                    if (slotsOpacitySlider != null) {
                        slotsOpacitySlider.setNormalizedValue(settings.getVanillaSlotsOpacity());
                    }
                }
        ).dimensions(controlsX, controlsY + rowGap * 4, controlsW, 20).build();
        this.addDrawableChild(slotsOverlayButton);

        // Row 5: Side-by-side Opacity Sliders (Image Opacity & Slots Opacity)
        int halfW = (controlsW - 4) / 2;
        opacitySlider = new CustomSliderWidget(
                controlsX, controlsY + rowGap * 5, halfW, 20,
                getOpacityText(),
                (settings.getOpacity() - 0.1) / 0.9,
                this::getOpacityText,
                val -> {
                    float opacity = (float) (0.1 + val * 0.9);
                    settings.setOpacity(Math.round(opacity * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        );
        this.addDrawableChild(opacitySlider);

        slotsOpacitySlider = new CustomSliderWidget(
                controlsX + halfW + 4, controlsY + rowGap * 5, halfW, 20,
                getSlotsOpacityText(),
                settings.getVanillaSlotsOpacity(),
                this::getSlotsOpacityText,
                val -> {
                    settings.setVanillaSlotsOpacity(Math.round(val.floatValue() * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                    if (slotsOverlayButton != null) {
                        slotsOverlayButton.setMessage(getSlotsOverlayText());
                    }
                }
        );
        this.addDrawableChild(slotsOpacitySlider);

        // Row 6: Zoom / Scale Slider (0.5x to 3.0x)
        zoomSlider = new CustomSliderWidget(
                controlsX, controlsY + rowGap * 6, controlsW, 20,
                getZoomText(),
                (settings.getScale() - 0.5) / 2.5,
                this::getZoomText,
                val -> {
                    float scale = (float) (0.5 + val * 2.5);
                    settings.setScale(Math.round(scale * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        );
        this.addDrawableChild(zoomSlider);

        // Row 7: Offset X and Offset Y Sliders (Side-by-side, -1.0 to 1.0)
        offsetXSlider = new CustomSliderWidget(
                controlsX, controlsY + rowGap * 7, halfW, 20,
                getOffsetXText(),
                (settings.getOffsetX() - (-1.0)) / 2.0,
                this::getOffsetXText,
                val -> {
                    float offX = (float) (-1.0 + val * 2.0);
                    settings.setOffsetX(Math.round(offX * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        );
        this.addDrawableChild(offsetXSlider);

        offsetYSlider = new CustomSliderWidget(
                controlsX + halfW + 4, controlsY + rowGap * 7, halfW, 20,
                getOffsetYText(),
                (settings.getOffsetY() - (-1.0)) / 2.0,
                this::getOffsetYText,
                val -> {
                    float offY = (float) (-1.0 + val * 2.0);
                    settings.setOffsetY(Math.round(offY * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        );
        this.addDrawableChild(offsetYSlider);

        // Row 8: Action Buttons (Reset, Thư Mục, Quét Lại)
        int btn3W = (controlsW - 8) / 3;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🔄 Reset"),
                btn -> {
                    settings.resetTransform();
                    TotemManager.getInstance().saveConfig();
                    updateSliders();
                    if (fitModeButton != null) fitModeButton.setMessage(getFitModeText());
                    if (slotsOverlayButton != null) slotsOverlayButton.setMessage(getSlotsOverlayText());
                }
        ).dimensions(controlsX, controlsY + rowGap * 8, btn3W, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📁 Mở"),
                btn -> Util.getOperatingSystem().open(TotemManager.getInstance().getInventoryGuiDir())
        ).dimensions(controlsX + btn3W + 4, controlsY + rowGap * 8, btn3W, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🔃 Quét"),
                btn -> {
                    TotemManager.getInstance().scanInventoryImages();
                    TotemManager.getInstance().loadInventoryGuiTexture();
                    if (selectImageButton != null) {
                        selectImageButton.setMessage(getImageButtonText());
                    }
                }
        ).dimensions(controlsX + (btn3W + 4) * 2, controlsY + rowGap * 8, btn3W, 20).build());

        // Row 9: Back Button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Quay lại (Back)"),
                btn -> this.close()
        ).dimensions(controlsX, controlsY + rowGap * 9, controlsW, 20).build());
    }

    private void updateSliders() {
        if (zoomSlider != null) {
            zoomSlider.setNormalizedValue((settings.getScale() - 0.5) / 2.5);
        }
        if (offsetXSlider != null) {
            offsetXSlider.setNormalizedValue((settings.getOffsetX() - (-1.0)) / 2.0);
        }
        if (offsetYSlider != null) {
            offsetYSlider.setNormalizedValue((settings.getOffsetY() - (-1.0)) / 2.0);
        }
        if (opacitySlider != null) {
            opacitySlider.setNormalizedValue((settings.getOpacity() - 0.1) / 0.9);
        }
        if (slotsOpacitySlider != null) {
            slotsOpacitySlider.setNormalizedValue(settings.getVanillaSlotsOpacity());
        }
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
        return Text.literal("Custom Túi Đồ: " + (settings.isEnabled() ? "§aBẬT" : "§cTẮT"));
    }

    private Text getFitModeText() {
        return Text.literal("Căn ảnh: §e" + settings.getFitMode().getDisplayName());
    }

    private Text getModeText() {
        return Text.literal("Chế độ: §b" + settings.getMode().getDisplayName());
    }

    private Text getSlotsOverlayText() {
        return Text.literal("Khung Ô: " + (settings.isShowVanillaSlots() ? "§aHIỆN (" + Math.round(settings.getVanillaSlotsOpacity() * 100) + "%)" : "§7ẨN"));
    }

    private Text getOpacityText() {
        return Text.literal(String.format("Mờ ảnh: §a%d%%", Math.round(settings.getOpacity() * 100)));
    }

    private Text getSlotsOpacityText() {
        return Text.literal(String.format("Mờ ô: §a%d%%", Math.round(settings.getVanillaSlotsOpacity() * 100)));
    }

    private Text getZoomText() {
        return Text.literal(String.format("Zoom: §b%.2fx", settings.getScale()));
    }

    private Text getOffsetXText() {
        return Text.literal(String.format("X: §b%.2f", settings.getOffsetX()));
    }

    private Text getOffsetYText() {
        return Text.literal(String.format("Y: §b%.2f", settings.getOffsetY()));
    }

    private Text getImageButtonText() {
        String name = settings.getImageFileName();
        if (name == null || name.trim().isEmpty()) {
            List<String> list = TotemManager.getInstance().getAvailableInventoryImages();
            if (list.isEmpty()) {
                return Text.literal("§7[Chưa có ảnh]");
            }
            return Text.literal("§eChọn ảnh (" + list.size() + ")");
        }
        if (name.length() > 16) {
            name = name.substring(0, 13) + "...";
        }
        return Text.literal("§e" + name);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0 && isInsidePreview(click.x(), click.y())) {
            isDraggingPreview = true;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && isDraggingPreview) {
            isDraggingPreview = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (isDraggingPreview) {
            float stepX = (float) deltaX / (INV_WIDTH * 0.5f);
            float stepY = (float) deltaY / (INV_HEIGHT * 0.5f);
            settings.setOffsetX(settings.getOffsetX() + stepX);
            settings.setOffsetY(settings.getOffsetY() + stepY);
            updateSliders();
            TotemManager.getInstance().saveConfig();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInsidePreview(mouseX, mouseY)) {
            float zoomDelta = (float) (verticalAmount * 0.1);
            settings.setScale(Math.round((settings.getScale() + zoomDelta) * 20.0f) / 20.0f);
            updateSliders();
            TotemManager.getInstance().saveConfig();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean isInsidePreview(double x, double y) {
        return x >= previewBoxX && x <= previewBoxX + INV_WIDTH && y >= previewBoxY && y <= previewBoxY + INV_HEIGHT;
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        // Preview Box (176 x 166 - Actual Minecraft Inventory Size)
        int px = previewBoxX;
        int py = previewBoxY;

        // Label above preview
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§6Túi Đồ Thật (176x166)"),
                px + INV_WIDTH / 2, py - 12, 0xFFD700);

        // Frame Border and Background
        context.fill(px - 2, py - 2, px + INV_WIDTH + 2, py + INV_HEIGHT + 2, 0xFF444444);
        context.fill(px, py, px + INV_WIDTH, py + INV_HEIGHT, 0xFF141414);

        Identifier texture = TotemManager.getInstance().getInventoryGuiTexture();
        int imgW = TotemManager.getInstance().getInventoryGuiImageWidth();
        int imgH = TotemManager.getInstance().getInventoryGuiImageHeight();

        if (texture != null && settings.isEnabled()) {
            // Render custom texture with selected crop/fit mode, scale & offsets
            InventoryGuiRenderer.renderBackground(context, texture, imgW, imgH, px, py, INV_WIDTH, INV_HEIGHT, settings);

            if (settings.isShowVanillaSlots()) {
                int slotAlpha = Math.max(1, Math.min(255, Math.round(settings.getVanillaSlotsOpacity() * 255)));
                int slotColor = (slotAlpha << 24) | 0x00FFFFFF;
                context.drawTexture(RenderPipelines.GUI_TEXTURED,
                        Identifier.ofVanilla("textures/gui/container/inventory.png"),
                        px, py, 0.0f, 0.0f, INV_WIDTH, INV_HEIGHT,
                        INV_WIDTH, INV_HEIGHT, 256, 256, slotColor);
            }

            // Real player model preview
            if (this.client != null && this.client.player != null) {
                InventoryScreen.drawEntity(context, px + 26, py + 8, px + 75, py + 78,
                        30, 0.0625f, (float) mouseX, (float) mouseY, this.client.player);
            }
        } else {
            // Fallback preview when disabled or no texture selected
            if (settings.isShowVanillaSlots()) {
                int slotAlpha = Math.max(1, Math.min(255, Math.round(settings.getVanillaSlotsOpacity() * 255)));
                int slotColor = (slotAlpha << 24) | 0x00FFFFFF;
                context.drawTexture(RenderPipelines.GUI_TEXTURED,
                        Identifier.ofVanilla("textures/gui/container/inventory.png"),
                        px, py, 0.0f, 0.0f, INV_WIDTH, INV_HEIGHT,
                        INV_WIDTH, INV_HEIGHT, 256, 256, slotColor);

                if (this.client != null && this.client.player != null) {
                    InventoryScreen.drawEntity(context, px + 26, py + 8, px + 75, py + 78,
                            30, 0.0625f, (float) mouseX, (float) mouseY, this.client.player);
                }
            }

            Text statusText = !settings.isEnabled()
                    ? Text.literal("§c[Túi đồ custom đang TẮT]")
                    : Text.literal("§7[Chưa chọn hình ảnh]");
            context.drawCenteredTextWithShadow(this.textRenderer, statusText,
                    px + INV_WIDTH / 2, py + INV_HEIGHT / 2 - 4, 0xAAAAAA);
        }

        // Subtitle / Drag hint below preview
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§8🖱 Kéo chuột để dịch • Lăn để Zoom"),
                px + INV_WIDTH / 2, py + INV_HEIGHT + 4, 0x888888);
    }

    private static class CustomSliderWidget extends SliderWidget {
        private final Consumer<Double> onApply;
        private final Supplier<Text> messageSupplier;

        public CustomSliderWidget(int x, int y, int width, int height, Text text, double value,
                                  Supplier<Text> messageSupplier, Consumer<Double> onApply) {
            super(x, y, width, height, text, value);
            this.messageSupplier = messageSupplier;
            this.onApply = onApply;
        }

        @Override
        protected void updateMessage() {
            if (messageSupplier != null) {
                setMessage(messageSupplier.get());
            }
        }

        @Override
        protected void applyValue() {
            if (onApply != null) {
                onApply.accept(this.value);
            }
        }

        public void setNormalizedValue(double val) {
            this.value = Math.max(0.0, Math.min(1.0, val));
            updateMessage();
        }
    }
}
