package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemEntry;
import com.customtotem.model.TotemTextureTransform;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TextureAdjustScreen extends Screen {
    private static final int[] RESOLUTION_OPTIONS = {0, 16, 32, 64, 128, 256, 512, 1024};

    private final Screen parent;
    private final TotemEntry entry;
    private final TotemTextureTransform transform;

    private SettingsListWidget settingsList;
    private ButtonWidget fitModeButton;
    private ButtonWidget flipHButton;
    private ButtonWidget flipVButton;
    private ButtonWidget animButton;

    private int previewX;
    private int previewY;
    private int boxSize;
    private boolean isSideBySide;

    public TextureAdjustScreen(Screen parent, TotemEntry entry) {
        super(Text.literal("Adjust Canvas: " + entry.getDisplayName()));
        this.parent = parent;
        this.entry = entry;
        this.transform = entry.getTransform();
    }

    private int getResolutionIndex(int res) {
        for (int i = 0; i < RESOLUTION_OPTIONS.length; i++) {
            if (RESOLUTION_OPTIONS[i] == res) {
                return i;
            }
        }
        return 0;
    }

    private Text getResolutionMessage(int res) {
        float vram = entry.calculateVramMb(res);
        String vramStr;
        if (vram > 32.0f) {
            vramStr = String.format("§c(%.1fMB VRAM - Cao!)", vram);
        } else if (vram > 16.0f) {
            vramStr = String.format("§e(%.1fMB VRAM)", vram);
        } else {
            vramStr = String.format("§a(%.1fMB)", vram);
        }

        if (res <= 0) {
            return Text.literal("Resolution: §eNative " + vramStr);
        }
        return Text.literal("Resolution: §b" + res + "x" + res + " " + vramStr);
    }

    private Text getFpsButtonMessage() {
        int currentFps = entry.getFps();
        int baseFps = entry.getBaseFps();
        if (currentFps < baseFps) {
            return Text.literal("Speed: §b" + currentFps + " FPS §e(Step)");
        }
        return Text.literal("Speed: §a" + currentFps + " FPS");
    }

    private void cycleGridPreset() {
        int w = entry.getWidth();
        int h = entry.getHeight();
        int currentCols = entry.getColumns();
        int currentRows = entry.getRows();

        int stripFrames = (w > 0 && h >= w && h % w == 0) ? (h / w) : 1;

        int[][] presets = new int[][]{
                {1, 1, 1},
                {16, 16, 256},
                {17, 17, 276},
                {15, 15, 214},
                {18, 18, 318},
                {14, 13, 172},
                {16, 15, 240},
                {8, 8, 64},
                {4, 4, 16},
                {1, stripFrames, stripFrames}
        };

        // If currently 1x1, try smart detection from filename first
        TotemManager.GridDetectionResult detected = TotemManager.detectGrid(entry.getFileName(), null, w, h);
        if (currentCols == 1 && currentRows == 1 && detected.frameCount() > 1) {
            entry.setGrid(detected.cols(), detected.rows(), detected.frameCount());
            if (detected.fps() > 0) entry.setFps(detected.fps());
            if (detected.baseFps() > 0) entry.setBaseFps(detected.baseFps());
            TotemManager.getInstance().saveConfig();
            this.clearAndInit();
            return;
        }

        // Cycle through presets
        int nextIdx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i][0] == currentCols && presets[i][1] == currentRows) {
                nextIdx = (i + 1) % presets.length;
                break;
            }
        }
        int[] chosen = presets[nextIdx];
        entry.setGrid(chosen[0], chosen[1], chosen[2]);
        TotemManager.getInstance().saveConfig();
        this.clearAndInit();
    }

    @Override
    protected void init() {
        TotemManager.getInstance().ensureTextureRegistered(entry);
        isSideBySide = this.width >= 350;

        int listX;
        int listY;
        int listW;
        int listH;

        if (isSideBySide) {
            int leftW = Math.min(145, (this.width - 30) / 3);
            boxSize = (this.height < 240) ? 64 : 80;
            previewX = 12 + (leftW - boxSize) / 2;
            previewY = Math.max(22, (this.height - 30 - boxSize - 28) / 2);

            listX = leftW + 20;
            listY = 22;
            listW = this.width - listX - 12;
            listH = Math.max(40, this.height - listY - 32);

            // Reset button placed on the bottom-left under preview
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Reset"),
                    btn -> {
                        transform.reset();
                        TotemManager.getInstance().applyResolution(entry);
                        TotemManager.getInstance().saveConfig();
                        this.clearAndInit();
                    }
            ).dimensions(12, this.height - 25, leftW, 20).build());

            // Done button placed under the controls list
            int doneW = Math.min(140, listW - 10);
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Done"),
                    btn -> this.close()
            ).dimensions(listX + (listW - doneW) / 2, this.height - 25, doneW, 20).build());
        } else {
            boxSize = (this.height < 250) ? 54 : 68;
            previewX = (this.width - boxSize) / 2;
            previewY = 18;

            int previewBottom = previewY + boxSize + 24;
            listX = 10;
            listY = previewBottom;
            listW = this.width - 20;
            listH = Math.max(40, this.height - listY - 32);

            int btnW = Math.min(110, (this.width - 26) / 2);
            int centerX = this.width / 2;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Reset"),
                    btn -> {
                        transform.reset();
                        TotemManager.getInstance().applyResolution(entry);
                        TotemManager.getInstance().saveConfig();
                        this.clearAndInit();
                    }
            ).dimensions(centerX - btnW - 3, this.height - 25, btnW, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Done"),
                    btn -> this.close()
            ).dimensions(centerX + 3, this.height - 25, btnW, 20).build());
        }

        // Scrollable settings list
        this.settingsList = new SettingsListWidget(this.client, listW, listH, listY, 24);
        this.settingsList.setX(listX);
        this.settingsList.setCustomRowWidth(listW - 14);

        // Row 1: Fit Mode Button
        fitModeButton = ButtonWidget.builder(
                Text.literal("Fit: " + transform.getFitMode().getDisplayName()),
                btn -> {
                    transform.setFitMode(transform.getFitMode().next());
                    btn.setMessage(Text.literal("Fit: " + transform.getFitMode().getDisplayName()));
                    TotemManager.getInstance().saveConfig();
                }
        ).dimensions(0, 0, 240, 20).build();
        this.settingsList.addWidget(fitModeButton);

        // Row 2: Resolution Slider
        int initialResIdx = getResolutionIndex(transform.getResolution());
        double initialResVal = (double) initialResIdx / (double) (RESOLUTION_OPTIONS.length - 1);
        this.settingsList.addWidget(new SliderWidget(
                0, 0, 240, 20,
                getResolutionMessage(transform.getResolution()),
                initialResVal
        ) {
            @Override
            protected void updateMessage() {
                int idx = (int) Math.round(this.value * (RESOLUTION_OPTIONS.length - 1));
                setMessage(getResolutionMessage(RESOLUTION_OPTIONS[idx]));
            }

            @Override
            protected void applyValue() {
                int idx = (int) Math.round(this.value * (RESOLUTION_OPTIONS.length - 1));
                int chosenRes = RESOLUTION_OPTIONS[idx];
                if (transform.getResolution() != chosenRes) {
                    transform.setResolution(chosenRes);
                    TotemManager.getInstance().applyResolution(entry);
                    TotemManager.getInstance().saveConfig();
                }
            }
        });

        // Row 3: Zoom / Scale Slider
        this.settingsList.addWidget(new SliderWidget(
                0, 0, 240, 20,
                Text.literal(String.format("Zoom: %.2fx", transform.getScale())),
                (transform.getScale() - 0.2) / (3.0 - 0.2)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("Zoom: %.2fx", transform.getScale())));
            }

            @Override
            protected void applyValue() {
                float val = (float) (0.2 + this.value * (3.0 - 0.2));
                transform.setScale(Math.round(val * 100.0f) / 100.0f);
                TotemManager.getInstance().saveConfig();
            }
        });

        // Row 4: Offset X and Offset Y Sliders (Side-by-side)
        SliderWidget offX = new SliderWidget(
                0, 0, 118, 20,
                Text.literal(String.format("Offset X: %.2f", transform.getOffsetX())),
                (transform.getOffsetX() - (-1.0)) / (1.0 - (-1.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("Offset X: %.2f", transform.getOffsetX())));
            }

            @Override
            protected void applyValue() {
                float val = (float) (-1.0 + this.value * 2.0);
                transform.setOffsetX(Math.round(val * 100.0f) / 100.0f);
                TotemManager.getInstance().saveConfig();
            }
        };

        SliderWidget offY = new SliderWidget(
                0, 0, 118, 20,
                Text.literal(String.format("Offset Y: %.2f", transform.getOffsetY())),
                (transform.getOffsetY() - (-1.0)) / (1.0 - (-1.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(String.format("Offset Y: %.2f", transform.getOffsetY())));
            }

            @Override
            protected void applyValue() {
                float val = (float) (-1.0 + this.value * 2.0);
                transform.setOffsetY(Math.round(val * 100.0f) / 100.0f);
                TotemManager.getInstance().saveConfig();
            }
        };
        this.settingsList.addWidgets(offX, offY);

        // Row 5: Flip Horizontal and Flip Vertical
        flipHButton = ButtonWidget.builder(
                Text.literal("Flip H: " + (transform.isFlipX() ? "§aON" : "§7OFF")),
                btn -> {
                    transform.setFlipX(!transform.isFlipX());
                    btn.setMessage(Text.literal("Flip H: " + (transform.isFlipX() ? "§aON" : "§7OFF")));
                    TotemManager.getInstance().saveConfig();
                }
        ).dimensions(0, 0, 118, 20).build();

        flipVButton = ButtonWidget.builder(
                Text.literal("Flip V: " + (transform.isFlipY() ? "§aON" : "§7OFF")),
                btn -> {
                    transform.setFlipY(!transform.isFlipY());
                    btn.setMessage(Text.literal("Flip V: " + (transform.isFlipY() ? "§aON" : "§7OFF")));
                    TotemManager.getInstance().saveConfig();
                }
        ).dimensions(0, 0, 118, 20).build();
        this.settingsList.addWidgets(flipHButton, flipVButton);

        // Row 6: Grid Preset / Manual Split
        Text gridLabel = Text.literal("Grid: " + (entry.getFrameCount() > 1
                ? ("§b" + entry.getColumns() + "x" + entry.getRows() + " (" + entry.getFrameCount() + "f)")
                : "§71x1 (Static)"));
        ButtonWidget gridButton = ButtonWidget.builder(
                gridLabel,
                btn -> cycleGridPreset()
        ).dimensions(0, 0, 240, 20).build();
        this.settingsList.addWidget(gridButton);

        // Row 7: Animation & Speed FPS (if spritesheet detected)
        if (entry.getFrameCount() > 1) {
            animButton = ButtonWidget.builder(
                    Text.literal("Anim: " + (transform.isAnimated() ? "§aON" : "§7OFF")),
                    btn -> {
                        transform.setAnimated(!transform.isAnimated());
                        btn.setMessage(Text.literal("Anim: " + (transform.isAnimated() ? "§aON" : "§7OFF")));
                        TotemManager.getInstance().saveConfig();
                    }
            ).dimensions(0, 0, 118, 20).build();

            ButtonWidget fpsButton = ButtonWidget.builder(
                    getFpsButtonMessage(),
                    btn -> {
                        int[] fpsOptions = {10, 15, 20, 30, 45, 60};
                        int current = entry.getFps();
                        int nextFps = fpsOptions[0];
                        for (int i = 0; i < fpsOptions.length; i++) {
                            if (fpsOptions[i] == current) {
                                nextFps = fpsOptions[(i + 1) % fpsOptions.length];
                                break;
                            } else if (fpsOptions[i] > current) {
                                nextFps = fpsOptions[i];
                                break;
                            }
                        }
                        entry.setFps(nextFps);
                        btn.setMessage(getFpsButtonMessage());
                        TotemManager.getInstance().saveConfig();
                    }
            ).dimensions(0, 0, 118, 20).build();

            this.settingsList.addWidgets(animButton, fpsButton);
        }

        this.addDrawableChild(settingsList);

        // Scroll helper buttons if there's horizontal room
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

        // Background and border for Preview Box
        context.fill(previewX - 2, previewY - 2, previewX + boxSize + 2, previewY + boxSize + 2, 0xFF555555);
        context.fill(previewX, previewY, previewX + boxSize, previewY + boxSize, 0xFF141414);

        // Draw Totem Texture inside preview box
        Identifier drawTex = entry.isTextureRegistered() ? entry.getTextureId() : entry.getThumbnailTextureId();
        if (drawTex != null) {
            float[] bounds = entry.getQuadBounds();
            int qx1 = (int) (previewX + bounds[0] * boxSize);
            int qx2 = (int) (previewX + bounds[1] * boxSize);
            int qy1 = (int) (previewY + (1.0f - bounds[3]) * boxSize);
            int qy2 = (int) (previewY + (1.0f - bounds[2]) * boxSize);

            float u0, u1, v0, v1;
            if (entry.isTextureRegistered()) {
                long nowMs = net.minecraft.util.Util.getMeasuringTimeMs();
                float[] uvs = entry.getRenderUVs(nowMs);
                u0 = uvs[0];
                u1 = uvs[1];
                v0 = uvs[2];
                v1 = uvs[3];
            } else {
                TotemTextureTransform t = entry.getTransform();
                u0 = t.isFlipX() ? 1.0f : 0.0f;
                u1 = t.isFlipX() ? 0.0f : 1.0f;
                v0 = t.isFlipY() ? 1.0f : 0.0f;
                v1 = t.isFlipY() ? 0.0f : 1.0f;
            }

            context.enableScissor(previewX, previewY, previewX + boxSize, previewY + boxSize);
            context.drawTexturedQuad(drawTex, qx1, qy1, qx2, qy2, u0, u1, v0, v1);
            context.disableScissor();

            if (!entry.isTextureRegistered()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§eLoading..."), previewX + boxSize / 2, previewY + boxSize / 2 - 4, 0xFFFFFFFF);
            }
        }

        // Draw outline 1:1 canvas frame
        context.fill(previewX, previewY, previewX + boxSize, previewY + 1, 0xFF888888);
        context.fill(previewX, previewY + boxSize - 1, previewX + boxSize, previewY + boxSize, 0xFF888888);
        context.fill(previewX, previewY, previewX + 1, previewY + boxSize, 0xFF888888);
        context.fill(previewX + boxSize - 1, previewY, previewX + boxSize, previewY + boxSize, 0xFF888888);

        // Resolution label below preview
        int textCenterX = previewX + boxSize / 2;
        int textY = previewY + boxSize + 4;

        if (isSideBySide) {
            String line1 = String.format("%dx%d %s", entry.getWidth(), entry.getHeight(),
                    entry.getFrameCount() > 1 ? "(" + entry.getFrameCount() + "f)" : "");
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + line1), textCenterX, textY, 0xFFAAAAAA);

            String speedDesc = (entry.getFrameCount() > 1)
                    ? (entry.getFps() < entry.getBaseFps() ? String.format(" | %dfps", entry.getFps()) : String.format(" | %dfps", entry.getFps()))
                    : "";
            String line2 = entry.getVramBadge() + speedDesc;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(line2), textCenterX, textY + 10, 0xFFAAAAAA);

            if (entry.isVramCritical()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c⚠ High VRAM!"), textCenterX, textY + 20, 0xFFFF5555);
            } else if (entry.isVramHigh()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e⚠ Moderate VRAM"), textCenterX, textY + 20, 0xFFFFFF55);
            }
        } else {
            String currentResStr = (transform.getResolution() <= 0) ? "Native" : (transform.getResolution() + "x" + transform.getResolution());
            String speedDesc = (entry.getFrameCount() > 1)
                    ? (entry.getFps() < entry.getBaseFps() ? String.format(" | %d/%dfps", entry.getFps(), entry.getBaseFps()) : String.format(" | %dfps", entry.getFps()))
                    : "";
            String resText = String.format("%dx%d | %s%s", entry.getWidth(), entry.getHeight(), entry.getVramBadge(), speedDesc);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + resText), textCenterX, textY, 0xFFAAAAAA);

            if (entry.isVramCritical()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c⚠ High VRAM (>32MB)!"), textCenterX, textY + 10, 0xFFFF5555);
            }
        }
    }
}
