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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class InventoryGuiSettingsScreen extends Screen {
    private static final int INV_WIDTH = 176;
    private static final int INV_HEIGHT = 166;

    public enum MobileTab {
        SETTINGS,
        PREVIEW
    }

    private final Screen parent;
    private final InventoryGuiSettings settings;

    private SettingsListWidget settingsList;
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
    private boolean sideBySide = true;
    private MobileTab currentTab = MobileTab.SETTINGS;

    public InventoryGuiSettingsScreen(Screen parent) {
        super(Text.literal("Inventory Background Settings"));
        this.parent = parent;
        this.settings = TotemManager.getInstance().getInventoryGuiSettings();
    }

    @Override
    protected void init() {
        TotemManager.getInstance().scanInventoryImages();

        sideBySide = this.width >= 390;

        int listX;
        int listY;
        int listW;
        int listH;

        if (sideBySide) {
            listX = 10;
            listY = 22;
            listW = Math.min(235, this.width - INV_WIDTH - 28);
            listH = Math.max(50, this.height - listY - 32);

            previewBoxX = listX + listW + (this.width - (listX + listW) - INV_WIDTH) / 2;
            previewBoxY = Math.max(22, (this.height - INV_HEIGHT) / 2);

            // Back button fixed at bottom-left
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Back"),
                    btn -> this.close()
            ).dimensions(listX, this.height - 25, listW, 20).build());
        } else {
            // Narrow screen mode with Tab Switcher
            int tabW = (this.width - 24) / 2;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(currentTab == MobileTab.SETTINGS ? "§aSettings" : "Settings"),
                    btn -> {
                        currentTab = MobileTab.SETTINGS;
                        this.clearAndInit();
                    }
            ).dimensions(10, 18, tabW, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(currentTab == MobileTab.PREVIEW ? "§aPreview" : "Preview"),
                    btn -> {
                        currentTab = MobileTab.PREVIEW;
                        this.clearAndInit();
                    }
            ).dimensions(14 + tabW, 18, tabW, 20).build());

            listX = 10;
            listY = 42;
            listW = this.width - 20;
            listH = Math.max(50, this.height - listY - 32);

            previewBoxX = (this.width - INV_WIDTH) / 2;
            previewBoxY = Math.max(44, (this.height - INV_HEIGHT) / 2);

            // Back button fixed at bottom
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Back"),
                    btn -> this.close()
            ).dimensions(this.width / 2 - 75, this.height - 25, 150, 20).build());
        }

        if (sideBySide || currentTab == MobileTab.SETTINGS) {
            buildSettingsList(listX, listY, listW, listH);
        }
    }

    private void buildSettingsList(int listX, int listY, int listW, int listH) {
        this.settingsList = new SettingsListWidget(this.client, listW, listH, listY, 24);
        this.settingsList.setX(listX);
        this.settingsList.setCustomRowWidth(listW - 14);

        // Row 0: Toggle Enabled
        toggleButton = ButtonWidget.builder(
                getToggleText(),
                btn -> {
                    settings.setEnabled(!settings.isEnabled());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getToggleText());
                }
        ).dimensions(0, 0, 200, 20).build();
        this.settingsList.addWidget(toggleButton);

        // Row 1: Image Selection (◀, [Tên], ▶)
        ButtonWidget btnPrev = ButtonWidget.builder(Text.literal("◀"), btn -> cycleImage(-1))
                .dimensions(0, 0, 22, 20).build();
        selectImageButton = ButtonWidget.builder(getImageButtonText(), btn -> cycleImage(1))
                .dimensions(0, 0, 140, 20).build();
        ButtonWidget btnNext = ButtonWidget.builder(Text.literal("▶"), btn -> cycleImage(1))
                .dimensions(0, 0, 22, 20).build();
        this.settingsList.addCycleRow(btnPrev, selectImageButton, btnNext, 22);

        // Row 2: Fit / Crop Mode (CROP, STRETCH, FIT)
        fitModeButton = ButtonWidget.builder(
                getFitModeText(),
                btn -> {
                    settings.setFitMode(settings.getFitMode().next());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getFitModeText());
                }
        ).dimensions(0, 0, 200, 20).build();
        this.settingsList.addWidget(fitModeButton);

        // Row 3: Display Mode (FRAME / FULLSCREEN)
        modeButton = ButtonWidget.builder(
                getModeText(),
                btn -> {
                    settings.setMode(settings.getMode().next());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getModeText());
                }
        ).dimensions(0, 0, 200, 20).build();
        this.settingsList.addWidget(modeButton);

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
        ).dimensions(0, 0, 200, 20).build();
        this.settingsList.addWidget(slotsOverlayButton);

        // Row 5: Side-by-side Opacity Sliders (Image Opacity & Slots Opacity)
        opacitySlider = new CustomSliderWidget(
                0, 0, 100, 20,
                getOpacityText(),
                (settings.getOpacity() - 0.1) / 0.9,
                this::getOpacityText,
                val -> {
                    float opacity = (float) (0.1 + val * 0.9);
                    settings.setOpacity(Math.round(opacity * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        );

        slotsOpacitySlider = new CustomSliderWidget(
                0, 0, 100, 20,
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
        this.settingsList.addWidgets(opacitySlider, slotsOpacitySlider);

        // Row 6: Zoom / Scale Slider (0.5x to 3.0x)
        zoomSlider = new CustomSliderWidget(
                0, 0, 200, 20,
                getZoomText(),
                (settings.getScale() - 0.5) / 2.5,
                this::getZoomText,
                val -> {
                    float scale = (float) (0.5 + val * 2.5);
                    settings.setScale(Math.round(scale * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        );
        this.settingsList.addWidget(zoomSlider);

        // Row 7: Offset X and Offset Y Sliders (Side-by-side, -1.0 to 1.0)
        offsetXSlider = new CustomSliderWidget(
                0, 0, 100, 20,
                getOffsetXText(),
                (settings.getOffsetX() - (-1.0)) / 2.0,
                this::getOffsetXText,
                val -> {
                    float offX = (float) (-1.0 + val * 2.0);
                    settings.setOffsetX(Math.round(offX * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        );

        offsetYSlider = new CustomSliderWidget(
                0, 0, 100, 20,
                getOffsetYText(),
                (settings.getOffsetY() - (-1.0)) / 2.0,
                this::getOffsetYText,
                val -> {
                    float offY = (float) (-1.0 + val * 2.0);
                    settings.setOffsetY(Math.round(offY * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        );
        this.settingsList.addWidgets(offsetXSlider, offsetYSlider);

        // Row 8: Action Buttons (Reset, File Browser, Download URL, Quét Lại)
        ButtonWidget btnReset = ButtonWidget.builder(
                Text.literal("Reset"),
                btn -> {
                    settings.resetTransform();
                    TotemManager.getInstance().saveConfig();
                    updateSliders();
                    if (fitModeButton != null) fitModeButton.setMessage(getFitModeText());
                    if (slotsOverlayButton != null) slotsOverlayButton.setMessage(getSlotsOverlayText());
                }
        ).dimensions(0, 0, 48, 20).build();

        ButtonWidget btnFile = ButtonWidget.builder(
                Text.literal("File"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new FolderBrowserScreen(this, TotemManager.getInstance().getInventoryGuiDir()));
                    }
                }
        ).dimensions(0, 0, 48, 20).build();

        ButtonWidget btnLink = ButtonWidget.builder(
                Text.literal("Link"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new DownloadUrlScreen(this, DownloadUrlScreen.TargetType.INVENTORY_GUI));
                    }
                }
        ).dimensions(0, 0, 48, 20).build();

        ButtonWidget btnScan = ButtonWidget.builder(
                Text.literal("Rescan"),
                btn -> {
                    TotemManager.getInstance().scanInventoryImages();
                    TotemManager.getInstance().loadInventoryGuiTexture();
                    if (selectImageButton != null) {
                        selectImageButton.setMessage(getImageButtonText());
                    }
                }
        ).dimensions(0, 0, 48, 20).build();

        this.settingsList.addWidgets(btnReset, btnFile, btnLink, btnScan);

        this.addDrawableChild(settingsList);

        // Scroll helper buttons if horizontal space permits
        if (this.width >= listX + listW + 28) {
            int scrollBtnX = listX + listW + 2;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Up"),
                    btn -> settingsList.scroll(-60)
            ).dimensions(scrollBtnX, listY + 2, 24, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Down"),
                    btn -> settingsList.scroll(60)
            ).dimensions(scrollBtnX, listY + listH - 22, 24, 20).build());
        }
    }

    private void updateSliders() {
        if (opacitySlider != null) {
            opacitySlider.setNormalizedValue((settings.getOpacity() - 0.1) / 0.9);
        }
        if (slotsOpacitySlider != null) {
            slotsOpacitySlider.setNormalizedValue(settings.getVanillaSlotsOpacity());
        }
        if (zoomSlider != null) {
            zoomSlider.setNormalizedValue((settings.getScale() - 0.5) / 2.5);
        }
        if (offsetXSlider != null) {
            offsetXSlider.setNormalizedValue((settings.getOffsetX() - (-1.0)) / 2.0);
        }
        if (offsetYSlider != null) {
            offsetYSlider.setNormalizedValue((settings.getOffsetY() - (-1.0)) / 2.0);
        }
    }

    private void cycleImage(int direction) {
        List<String> list = TotemManager.getInstance().getAvailableInventoryImages();
        if (list.isEmpty()) return;

        int currentIndex = list.indexOf(settings.getImageFileName());
        int newIndex;
        if (currentIndex == -1) {
            newIndex = 0;
        } else {
            newIndex = (currentIndex + direction + list.size()) % list.size();
        }

        settings.setImageFileName(list.get(newIndex));
        TotemManager.getInstance().loadInventoryGuiTexture();
        TotemManager.getInstance().saveConfig();

        if (selectImageButton != null) {
            selectImageButton.setMessage(getImageButtonText());
        }
    }

    private Text getToggleText() {
        return Text.literal("Custom Inventory: " + (settings.isEnabled() ? "§aON" : "§cOFF"));
    }

    private Text getFitModeText() {
        return Text.literal("Crop Mode: " + settings.getFitMode().getDisplayName());
    }

    private Text getModeText() {
        return Text.literal("Display: " + settings.getMode().getDisplayName());
    }

    private Text getSlotsOverlayText() {
        return Text.literal("Vanilla Slots: " + (settings.isShowVanillaSlots() ? "§aON" : "§7OFF"));
    }

    private Text getOpacityText() {
        int pct = Math.round(settings.getOpacity() * 100);
        return Text.literal("Image: §e" + pct + "%");
    }

    private Text getSlotsOpacityText() {
        int pct = Math.round(settings.getVanillaSlotsOpacity() * 100);
        return Text.literal("Slots: §e" + pct + "%");
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
                return Text.literal("§7[No images]");
            }
            return Text.literal("§eSelect image (" + list.size() + ")");
        }
        if (name.length() > 16) {
            name = name.substring(0, 13) + "...";
        }
        return Text.literal("§e" + name);
    }

    private boolean shouldShowPreview() {
        return sideBySide || currentTab == MobileTab.PREVIEW;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (shouldShowPreview() && click.button() == 0 && isInsidePreview(click.x(), click.y())) {
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
        if (shouldShowPreview() && isInsidePreview(mouseX, mouseY)) {
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

        if (sideBySide) {
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        }

        if (shouldShowPreview()) {
            drawPreview(context, mouseX, mouseY);
        }
    }

    private void drawPreview(DrawContext context, int mouseX, int mouseY) {
        int px = previewBoxX;
        int py = previewBoxY;

        // Label above preview
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§6Live Inventory (176x166)"),
                px + INV_WIDTH / 2, py - 12, 0xFFFFD700);

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
                    ? Text.literal("§c[Custom Inventory is OFF]")
                    : Text.literal("§7[No image selected]");
            context.drawCenteredTextWithShadow(this.textRenderer, statusText,
                    px + INV_WIDTH / 2, py + INV_HEIGHT / 2 - 4, 0xFFAAAAAA);
        }

        // Subtitle / Drag hint below preview
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§8Drag to pan • Scroll to zoom"),
                px + INV_WIDTH / 2, py + INV_HEIGHT + 4, 0xFF888888);
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
