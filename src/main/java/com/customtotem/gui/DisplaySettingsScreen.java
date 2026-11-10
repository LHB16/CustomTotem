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

    public DisplaySettingsScreen(Screen parent) {
        super(Text.translatable("gui.customtotem.display_settings"));
        this.parent = parent;
        this.settings = TotemManager.getInstance().getDisplaySettings();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 32;

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
        ).dimensions(centerX - 120, startY, 240, 20).build();
        this.addDrawableChild(presetButton);

        // Fullbright toggle button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Fullbright: " + (settings.isFullbright() ? "§aBẬT" : "§cTẮT")),
                btn -> {
                    settings.setFullbright(!settings.isFullbright());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(Text.literal("Fullbright: " + (settings.isFullbright() ? "§aBẬT" : "§cTẮT")));
                }
        ).dimensions(centerX - 120, startY + 24, 240, 20).build());

        // First person scale slider: range 0.1f to 1.5f
        this.addDrawableChild(new SliderWidget(
                centerX - 120, startY + 48, 240, 20,
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
                presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                TotemManager.getInstance().saveConfig();
            }
        });

        // First person Translation X: range -5.0 to 5.0
        this.addDrawableChild(new SliderWidget(
                centerX - 120, startY + 72, 240, 20,
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
                presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                TotemManager.getInstance().saveConfig();
            }
        });

        // First person Translation Y: range -2.0 to 8.0
        this.addDrawableChild(new SliderWidget(
                centerX - 120, startY + 96, 240, 20,
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
                presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                TotemManager.getInstance().saveConfig();
            }
        });

        // Third person scale slider: range 0.1 to 1.5
        this.addDrawableChild(new SliderWidget(
                centerX - 120, startY + 124, 240, 20,
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
                presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                TotemManager.getInstance().saveConfig();
            }
        });

        // Third person Translation Y: range -2.0 to 8.0
        this.addDrawableChild(new SliderWidget(
                centerX - 120, startY + 148, 240, 20,
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
                presetButton.setMessage(Text.literal("Preset: " + settings.getPreset().getDisplayName()));
                TotemManager.getInstance().saveConfig();
            }
        });

        // Inventory GUI Settings button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🎒 Cài Đặt Ảnh Nền Túi Đồ"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new InventoryGuiSettingsScreen(this));
                    }
                }
        ).dimensions(centerX - 120, startY + 172, 240, 20).build());

        // Reset to Mini Totem button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Reset to Mini Totem"),
                btn -> {
                    settings.setPreset(TotemDisplaySettings.Preset.MINI_TOTEM);
                    TotemManager.getInstance().saveConfig();
                    this.clearAndInit();
                }
        ).dimensions(centerX - 120, startY + 196, 115, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> this.close()
        ).dimensions(centerX + 5, startY + 196, 115, 20).build());
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFFFF);
    }
}
