package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemDisplaySettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class DisplaySettingsScreen extends Screen {
    private final Screen parent;
    private final TotemDisplaySettings settings;
    private ButtonWidget presetButton;
    private SettingsListWidget settingsList;

    public DisplaySettingsScreen(Screen parent) {
        super(Text.translatable("gui.customtotem.display_settings"));
        this.parent = parent;
        this.settings = TotemManager.getInstance().getDisplaySettings();
    }

    @Override
    protected void init() {
        int listY = 28;
        int bottomBarHeight = 32;
        int listHeight = Math.max(40, this.height - listY - bottomBarHeight);
        int listWidth = Math.min(this.width - 24, 380);

        this.settingsList = new SettingsListWidget(this.client, listWidth, listHeight, listY, 24);
        this.settingsList.setX((this.width - listWidth) / 2);
        this.settingsList.setCustomRowWidth(Math.min(listWidth - 16, 320));

        // Preset button
        presetButton = ButtonWidget.builder(
                Text.literal("Preset: " + settings.getPreset().getDisplayName()),
                btn -> {
                    TotemDisplaySettings.Preset[] presets = TotemDisplaySettings.Preset.values();
                    int nextIdx = (settings.getPreset().ordinal() + 1) % presets.length;
                    settings.setPreset(presets[nextIdx]);
                    TotemManager.getInstance().saveConfig();
                    this.clearAndInit();
                }
        ).dimensions(0, 0, 240, 20).build();
        this.settingsList.addWidget(presetButton);

        // Fullbright toggle button
        ButtonWidget fullbrightBtn = ButtonWidget.builder(
                Text.literal("Fullbright: " + (settings.isFullbright() ? "§aBẬT" : "§cTẮT")),
                btn -> {
                    settings.setFullbright(!settings.isFullbright());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(Text.literal("Fullbright: " + (settings.isFullbright() ? "§aBẬT" : "§cTẮT")));
                }
        ).dimensions(0, 0, 240, 20).build();
        this.settingsList.addWidget(fullbrightBtn);

        // Section: First Person (Góc Nhìn Thứ Nhất)
        this.settingsList.addHeader(Text.literal("§6--- Góc Nhìn Thứ Nhất (First Person) ---"), 0xFFFFAA00);

        // FP Scale slider: range 0.1f to 1.5f
        this.settingsList.addWidget(new SliderWidget(
                0, 0, 240, 20,
                Text.literal(String.format("FP Scale: %.2f", settings.getFpScale())),
                (settings.getFpScale() - 0.1) / (1.5 - 0.1)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("FP Scale: %.2f", settings.getFpScale())));
            }

            @Override
            protected void applyValue() {
                float val = (float) (0.1 + this.value * (1.5 - 0.1));
                settings.setFpScale(Math.round(val * 100.0f) / 100.0f);
                settings.setPreset(TotemDisplaySettings.Preset.CUSTOM);
                if (presetButton != null) {
                    presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                }
                TotemManager.getInstance().saveConfig();
            }
        });

        // FP Translation X & Y sliders (Side-by-side)
        SliderWidget fpTransX = new SliderWidget(
                0, 0, 118, 20,
                Text.literal(String.format("FP Offset X: %.2f", settings.getFpTranslationX())),
                (settings.getFpTranslationX() - (-5.0)) / (5.0 - (-5.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("FP Offset X: %.2f", settings.getFpTranslationX())));
            }

            @Override
            protected void applyValue() {
                float val = (float) (-5.0 + this.value * 10.0);
                settings.setFpTranslationX(Math.round(val * 10.0f) / 10.0f);
                settings.setPreset(TotemDisplaySettings.Preset.CUSTOM);
                if (presetButton != null) {
                    presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                }
                TotemManager.getInstance().saveConfig();
            }
        };

        SliderWidget fpTransY = new SliderWidget(
                0, 0, 118, 20,
                Text.literal(String.format("FP Offset Y: %.2f", settings.getFpTranslationY())),
                (settings.getFpTranslationY() - (-2.0)) / (8.0 - (-2.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("FP Offset Y: %.2f", settings.getFpTranslationY())));
            }

            @Override
            protected void applyValue() {
                float val = (float) (-2.0 + this.value * 10.0);
                settings.setFpTranslationY(Math.round(val * 10.0f) / 10.0f);
                settings.setPreset(TotemDisplaySettings.Preset.CUSTOM);
                if (presetButton != null) {
                    presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                }
                TotemManager.getInstance().saveConfig();
            }
        };
        this.settingsList.addWidgets(fpTransX, fpTransY);

        // Section: Third Person (Góc Nhìn Thứ Ba)
        this.settingsList.addHeader(Text.literal("§b--- Góc Nhìn Thứ Ba (Third Person) ---"), 0xFF55FFFF);

        SliderWidget tpScale = new SliderWidget(
                0, 0, 118, 20,
                Text.literal(String.format("TP Scale: %.2f", settings.getTpScale())),
                (settings.getTpScale() - 0.1) / (1.5 - 0.1)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("TP Scale: %.2f", settings.getTpScale())));
            }

            @Override
            protected void applyValue() {
                float val = (float) (0.1 + this.value * (1.5 - 0.1));
                settings.setTpScale(Math.round(val * 100.0f) / 100.0f);
                settings.setPreset(TotemDisplaySettings.Preset.CUSTOM);
                if (presetButton != null) {
                    presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                }
                TotemManager.getInstance().saveConfig();
            }
        };

        SliderWidget tpTransY = new SliderWidget(
                0, 0, 118, 20,
                Text.literal(String.format("TP Offset Y: %.2f", settings.getTpTranslationY())),
                (settings.getTpTranslationY() - (-2.0)) / (8.0 - (-2.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("TP Offset Y: %.2f", settings.getTpTranslationY())));
            }

            @Override
            protected void applyValue() {
                float val = (float) (-2.0 + this.value * 10.0);
                settings.setTpTranslationY(Math.round(val * 10.0f) / 10.0f);
                settings.setPreset(TotemDisplaySettings.Preset.CUSTOM);
                if (presetButton != null) {
                    presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                }
                TotemManager.getInstance().saveConfig();
            }
        };
        this.settingsList.addWidgets(tpScale, tpTransY);

        // Section: Features
        this.settingsList.addHeader(Text.literal("§a--- Cài Đặt Giao Diện Khác ---"), 0xFF55FF55);

        // Inventory GUI Settings button
        this.settingsList.addWidget(ButtonWidget.builder(
                Text.literal("🎒 Cài Đặt Ảnh Nền Túi Đồ"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new InventoryGuiSettingsScreen(this));
                    }
                }
        ).dimensions(0, 0, 240, 20).build());

        // Add the scrollable list to screen
        this.addDrawableChild(settingsList);

        // Scroll helper buttons for touch / PojavLauncher
        if (this.width >= listWidth + 26) {
            int scrollBtnX = settingsList.getX() + listWidth + 2;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("▲"),
                    btn -> settingsList.scroll(-60)
            ).dimensions(scrollBtnX, listY + 2, 18, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("▼"),
                    btn -> settingsList.scroll(60)
            ).dimensions(scrollBtnX, listY + listHeight - 22, 18, 20).build());
        }

        // Fixed bottom buttons bar (ALWAYS reachable, never cut off!)
        int bottomY = this.height - 25;
        int btnW = Math.min(135, (this.width - 24) / 2);
        int centerX = this.width / 2;

        // Reset to Mini Totem button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Reset to Mini Totem"),
                btn -> {
                    settings.setPreset(TotemDisplaySettings.Preset.MINI_TOTEM);
                    TotemManager.getInstance().saveConfig();
                    this.clearAndInit();
                }
        ).dimensions(centerX - btnW - 3, bottomY, btnW, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Quay Lại (Back)"),
                btn -> this.close()
        ).dimensions(centerX + 3, bottomY, btnW, 20).build());
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);
    }
}
