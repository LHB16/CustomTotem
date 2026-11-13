package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemDisplaySettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.List;

public class DisplaySettingsScreen extends Screen {
    private final Screen parent;
    private final TotemDisplaySettings settings;
    private ButtonWidget presetButton;
    private SettingsListWidget settingsList;

    private static class ResOption {
        final int resolution;
        final String label;
        ButtonWidget widget;

        ResOption(int resolution, String label) {
            this.resolution = resolution;
            this.label = label;
        }
    }

    private final List<ResOption> resOptions = List.of(
            new ResOption(0, "Auto"),
            new ResOption(64, "64x64"),
            new ResOption(128, "128x128"),
            new ResOption(256, "256x256"),
            new ResOption(512, "512x512"),
            new ResOption(1024, "1024x1024")
    );

    public DisplaySettingsScreen(Screen parent) {
        super(Text.literal("Display Settings"));
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
                Text.literal("Fullbright: " + (settings.isFullbright() ? "§aON" : "§cOFF")),
                btn -> {
                    settings.setFullbright(!settings.isFullbright());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(Text.literal("Fullbright: " + (settings.isFullbright() ? "§aON" : "§cOFF")));
                }
        ).dimensions(0, 0, 240, 20).build();
        this.settingsList.addWidget(fullbrightBtn);

        // Section: Global Texture & Animation
        this.settingsList.addHeader(Text.literal("§e--- Global Texture & Animation ---"), 0xFFFFFF55);

        // Texture Resolution Sub-header
        this.settingsList.addHeader(Text.literal("§7Texture Resolution:"), 0xFFAAAAAA);

        // Resolution buttons (Row 1: Auto, 64x64, 128x128)
        ResOption opt0 = resOptions.get(0);
        ResOption opt1 = resOptions.get(1);
        ResOption opt2 = resOptions.get(2);
        opt0.widget = createResButton(opt0);
        opt1.widget = createResButton(opt1);
        opt2.widget = createResButton(opt2);
        this.settingsList.addWidgets(opt0.widget, opt1.widget, opt2.widget);

        // Resolution buttons (Row 2: 256x256, 512x512, 1024x1024)
        ResOption opt3 = resOptions.get(3);
        ResOption opt4 = resOptions.get(4);
        ResOption opt5 = resOptions.get(5);
        opt3.widget = createResButton(opt3);
        opt4.widget = createResButton(opt4);
        opt5.widget = createResButton(opt5);
        this.settingsList.addWidgets(opt3.widget, opt4.widget, opt5.widget);

        // Global Animation & Global FPS (side-by-side)
        ButtonWidget globalAnimBtn = ButtonWidget.builder(
                getGlobalAnimButtonMessage(),
                btn -> {
                    int current = settings.getGlobalAnimation();
                    int next = (current + 1) % 3;
                    settings.setGlobalAnimation(next);
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getGlobalAnimButtonMessage());
                }
        ).dimensions(0, 0, 118, 20).build();

        ButtonWidget globalFpsBtn = ButtonWidget.builder(
                getGlobalFpsButtonMessage(),
                btn -> {
                    int[] fpsOptions = {0, 15, 20, 30, 60};
                    int current = settings.getGlobalFps();
                    int nextIdx = 0;
                    for (int i = 0; i < fpsOptions.length; i++) {
                        if (fpsOptions[i] == current) {
                            nextIdx = (i + 1) % fpsOptions.length;
                            break;
                        }
                    }
                    settings.setGlobalFps(fpsOptions[nextIdx]);
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(getGlobalFpsButtonMessage());
                }
        ).dimensions(0, 0, 118, 20).build();
        this.settingsList.addWidgets(globalAnimBtn, globalFpsBtn);

        // Section: First Person
        this.settingsList.addHeader(Text.literal("§6--- First Person View ---"), 0xFFFFAA00);

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

        // Section: Third Person
        this.settingsList.addHeader(Text.literal("§b--- Third Person View ---"), 0xFF55FFFF);

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
        this.settingsList.addHeader(Text.literal("§a--- Additional Settings ---"), 0xFF55FF55);

        // Hand Outline Settings button
        this.settingsList.addWidget(ButtonWidget.builder(
                Text.literal("Item Glow Outline Settings"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new HandOutlineSettingsScreen(this));
                    }
                }
        ).dimensions(0, 0, 240, 20).build());

        // Inventory GUI Settings button
        this.settingsList.addWidget(ButtonWidget.builder(
                Text.literal("Inventory Background Settings"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new InventoryGuiSettingsScreen(this));
                    }
                }
        ).dimensions(0, 0, 240, 20).build());

        // Add the scrollable list to screen
        this.addDrawableChild(settingsList);

        // Scroll helper buttons for touch / PojavLauncher
        if (this.width >= listWidth + 28) {
            int scrollBtnX = settingsList.getX() + listWidth + 2;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Up"),
                    btn -> settingsList.scroll(-60)
            ).dimensions(scrollBtnX, listY + 2, 24, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Down"),
                    btn -> settingsList.scroll(60)
            ).dimensions(scrollBtnX, listY + listHeight - 22, 24, 20).build());
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
                Text.literal("Back"),
                btn -> this.close()
        ).dimensions(centerX + 3, bottomY, btnW, 20).build());
    }

    private ButtonWidget createResButton(ResOption opt) {
        return ButtonWidget.builder(
                getResButtonText(opt.resolution, opt.label),
                btn -> {
                    if (settings.getGlobalResolution() != opt.resolution) {
                        settings.setGlobalResolution(opt.resolution);
                        TotemManager.getInstance().saveConfig();
                        TotemManager.getInstance().applyGlobalResolution();
                        updateResButtons();
                    }
                }
        ).dimensions(0, 0, 100, 20).build();
    }

    private Text getResButtonText(int res, String label) {
        if (settings.getGlobalResolution() == res) {
            return Text.literal("§a✔ " + label);
        }
        return Text.literal("§7" + label);
    }

    private void updateResButtons() {
        for (ResOption opt : resOptions) {
            if (opt.widget != null) {
                opt.widget.setMessage(getResButtonText(opt.resolution, opt.label));
            }
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private Text getGlobalAnimButtonMessage() {
        int anim = settings.getGlobalAnimation();
        if (anim == 1) return Text.literal("Global Anim: §bForce ON");
        if (anim == 2) return Text.literal("Global Anim: §cForce OFF");
        return Text.literal("Global Anim: §aDefault");
    }

    private Text getGlobalFpsButtonMessage() {
        int fps = settings.getGlobalFps();
        return fps <= 0 ? Text.literal("Global FPS: §aDefault") : Text.literal("Global FPS: §b" + fps + " FPS");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        super.render(context, mouseX, mouseY, tickDelta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);
    }
}
