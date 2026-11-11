package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.HandOutlineSettings;
import com.customtotem.model.HandOutlineSettings.OutlineColorMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class HandOutlineSettingsScreen extends Screen {
    private final Screen parent;
    private final HandOutlineSettings settings;
    private SettingsListWidget settingsList;

    private static final int[] COLOR_PRESETS = new int[]{
            0xFF00FFCC, // Aqua / Cyan
            0xFFFF1493, // Neon Pink
            0xFFFF3333, // Crimson Red
            0xFFFFD700, // Golden Yellow
            0xFF00FF66, // Emerald Green
            0xFF9933FF, // Electric Purple
            0xFFFFFFFF, // Pure White
            0xFFFF8800  // Sunset Orange
    };
    private static final String[] COLOR_NAMES = new String[]{
            "Aqua",
            "Neon Pink",
            "Crimson Red",
            "Gold",
            "Emerald Green",
            "Purple",
            "White",
            "Sunset Orange"
    };

    public HandOutlineSettingsScreen(Screen parent) {
        super(Text.literal("Item Glow Outline Settings"));
        this.parent = parent;
        this.settings = TotemManager.getInstance().getOutlineSettings();
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

        // 1. Toggle Outline
        ButtonWidget toggleBtn = ButtonWidget.builder(
                Text.literal("Outline Glow: " + (settings.isEnabled() ? "§aON" : "§cOFF")),
                btn -> {
                    settings.setEnabled(!settings.isEnabled());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(Text.literal("Outline Glow: " + (settings.isEnabled() ? "§aON" : "§cOFF")));
                }
        ).dimensions(0, 0, 240, 20).build();
        this.settingsList.addWidget(toggleBtn);

        // 2. Target
        ButtonWidget targetBtn = ButtonWidget.builder(
                Text.literal("Target: " + (settings.isTotemOnly() ? "§6Totem Only" : "§eAll Items / Hands")),
                btn -> {
                    settings.setTotemOnly(!settings.isTotemOnly());
                    TotemManager.getInstance().saveConfig();
                    btn.setMessage(Text.literal("Target: " + (settings.isTotemOnly() ? "§6Totem Only" : "§eAll Items / Hands")));
                }
        ).dimensions(0, 0, 240, 20).build();
        this.settingsList.addWidget(targetBtn);

        // Header Color & Mode
        this.settingsList.addHeader(Text.literal("§d--- Color & Mode ---"), 0xFFFF77DD);

        // 3. Mode
        ButtonWidget modeBtn = ButtonWidget.builder(
                Text.literal("Mode: §b" + settings.getMode().getDisplayName()),
                btn -> {
                    OutlineColorMode[] modes = OutlineColorMode.values();
                    int nextIdx = (settings.getMode().ordinal() + 1) % modes.length;
                    settings.setMode(modes[nextIdx]);
                    TotemManager.getInstance().saveConfig();
                    this.clearAndInit();
                }
        ).dimensions(0, 0, 240, 20).build();
        this.settingsList.addWidget(modeBtn);

        // 4. Static Color
        if (settings.getMode() == OutlineColorMode.STATIC) {
            int curColor = settings.getStaticColor();
            String curName = "Custom";
            for (int i = 0; i < COLOR_PRESETS.length; i++) {
                if (COLOR_PRESETS[i] == curColor) {
                    curName = COLOR_NAMES[i];
                    break;
                }
            }
            ButtonWidget colorBtn = ButtonWidget.builder(
                    Text.literal("Static Color: §a" + curName),
                    btn -> {
                        int nextIdx = 0;
                        for (int i = 0; i < COLOR_PRESETS.length; i++) {
                            if (COLOR_PRESETS[i] == settings.getStaticColor()) {
                                nextIdx = (i + 1) % COLOR_PRESETS.length;
                                break;
                            }
                        }
                        settings.setStaticColor(COLOR_PRESETS[nextIdx]);
                        TotemManager.getInstance().saveConfig();
                        this.clearAndInit();
                    }
            ).dimensions(0, 0, 240, 20).build();
            this.settingsList.addWidget(colorBtn);
        }

        // 5. Speed Slider
        double speedVal = (settings.getSpeed() - 0.1) / (5.0 - 0.1);
        this.settingsList.addWidget(new CustomSliderWidget(
                0, 0, 240, 20,
                Text.literal(String.format("Cycle Speed: %.2fx", settings.getSpeed())),
                speedVal,
                () -> Text.literal(String.format("Cycle Speed: %.2fx", settings.getSpeed())),
                val -> {
                    float s = (float) (0.1 + val * (5.0 - 0.1));
                    settings.setSpeed(Math.round(s * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        ));

        // Header Size & Glow
        this.settingsList.addHeader(Text.literal("§6--- Size & Glow Intensity ---"), 0xFFFFAA00);

        // 6. Outline Width
        double widthVal = (settings.getWidth() - 1.0) / (6.0 - 1.0);
        this.settingsList.addWidget(new CustomSliderWidget(
                0, 0, 240, 20,
                Text.literal(String.format("Outline Width: %.1f px", settings.getWidth())),
                widthVal,
                () -> Text.literal(String.format("Outline Width: %.1f px", settings.getWidth())),
                val -> {
                    float w = (float) (1.0 + val * (6.0 - 1.0));
                    settings.setWidth(Math.round(w * 10.0f) / 10.0f);
                    TotemManager.getInstance().saveConfig();
                }
        ));

        // 7. Aura Glow
        this.settingsList.addWidget(new CustomSliderWidget(
                0, 0, 240, 20,
                Text.literal(String.format("Aura Glow: %d%%", Math.round(settings.getAuraIntensity() * 100))),
                settings.getAuraIntensity(),
                () -> Text.literal(String.format("Aura Glow: %d%%", Math.round(settings.getAuraIntensity() * 100))),
                val -> {
                    settings.setAuraIntensity(val.floatValue());
                    TotemManager.getInstance().saveConfig();
                }
        ));

        // 8. Rim Strength
        double rimVal = (settings.getOutlineStrength() - 0.1) / (1.0 - 0.1);
        this.settingsList.addWidget(new CustomSliderWidget(
                0, 0, 240, 20,
                Text.literal(String.format("Rim Strength: %d%%", Math.round(settings.getOutlineStrength() * 100))),
                rimVal,
                () -> Text.literal(String.format("Rim Strength: %d%%", Math.round(settings.getOutlineStrength() * 100))),
                val -> {
                    float rim = (float) (0.1 + val * (1.0 - 0.1));
                    settings.setOutlineStrength(Math.round(rim * 100.0f) / 100.0f);
                    TotemManager.getInstance().saveConfig();
                }
        ));

        // 9. Sheen Glint
        this.settingsList.addWidget(new CustomSliderWidget(
                0, 0, 240, 20,
                Text.literal(String.format("Sheen Glint: %d%%", Math.round(settings.getSheen() * 100))),
                settings.getSheen(),
                () -> Text.literal(String.format("Sheen Glint: %d%%", Math.round(settings.getSheen() * 100))),
                val -> {
                    settings.setSheen(val.floatValue());
                    TotemManager.getInstance().saveConfig();
                }
        ));

        // Add settingsList
        this.addDrawableChild(settingsList);

        // Touch scroll helper buttons
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

        // Bottom bar
        int bottomY = this.height - 25;
        int btnW = Math.min(135, (this.width - 24) / 2);
        int centerX = this.width / 2;

        // Reset to default
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Reset Default"),
                btn -> {
                    settings.setEnabled(true);
                    settings.setTotemOnly(false);
                    settings.setMode(OutlineColorMode.RAINBOW_SPIRAL);
                    settings.setStaticColor(0xFF00FFCC);
                    settings.setSpeed(1.0f);
                    settings.setWidth(2.5f);
                    settings.setAuraIntensity(0.5f);
                    settings.setOutlineStrength(1.0f);
                    settings.setSheen(0.35f);
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
    }
}
