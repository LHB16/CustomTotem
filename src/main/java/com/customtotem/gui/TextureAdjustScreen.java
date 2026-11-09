package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemEntry;
import com.customtotem.model.TotemTextureTransform;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class TextureAdjustScreen extends Screen {
    private final Screen parent;
    private final TotemEntry entry;
    private final TotemTextureTransform transform;

    private ButtonWidget fitModeButton;
    private ButtonWidget flipHButton;
    private ButtonWidget flipVButton;
    private ButtonWidget animButton;

    public TextureAdjustScreen(Screen parent, TotemEntry entry) {
        super(Text.literal("Adjust Canvas: " + entry.getDisplayName()));
        this.parent = parent;
        this.entry = entry;
        this.transform = entry.getTransform();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int controlsY = 145;
        int btnWidth = 115;

        // Button: Fit Mode (Fit, Stretch, Fill)
        fitModeButton = ButtonWidget.builder(
                Text.literal("Fit: " + transform.getFitMode().getDisplayName()),
                btn -> {
                    transform.setFitMode(transform.getFitMode().next());
                    btn.setMessage(Text.literal("Fit: " + transform.getFitMode().getDisplayName()));
                    TotemManager.getInstance().saveConfig();
                }
        ).dimensions(centerX - 120, controlsY, 240, 20).build();
        this.addDrawableChild(fitModeButton);

        // Zoom / Scale Slider: range 0.2f to 3.0f
        this.addDrawableChild(new SliderWidget(
                centerX - 120, controlsY + 25, 240, 20,
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

        // Offset X Slider: range -1.0f to 1.0f
        this.addDrawableChild(new SliderWidget(
                centerX - 120, controlsY + 50, 240, 20,
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
        });

        // Offset Y Slider: range -1.0f to 1.0f
        this.addDrawableChild(new SliderWidget(
                centerX - 120, controlsY + 75, 240, 20,
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
        });

        // Toggle Flip Horizontal
        flipHButton = ButtonWidget.builder(
                Text.literal("Flip H: " + (transform.isFlipX() ? "§aON" : "§7OFF")),
                btn -> {
                    transform.setFlipX(!transform.isFlipX());
                    btn.setMessage(Text.literal("Flip H: " + (transform.isFlipX() ? "§aON" : "§7OFF")));
                    TotemManager.getInstance().saveConfig();
                }
        ).dimensions(centerX - 120, controlsY + 100, btnWidth, 20).build();
        this.addDrawableChild(flipHButton);

        // Toggle Flip Vertical
        flipVButton = ButtonWidget.builder(
                Text.literal("Flip V: " + (transform.isFlipY() ? "§aON" : "§7OFF")),
                btn -> {
                    transform.setFlipY(!transform.isFlipY());
                    btn.setMessage(Text.literal("Flip V: " + (transform.isFlipY() ? "§aON" : "§7OFF")));
                    TotemManager.getInstance().saveConfig();
                }
        ).dimensions(centerX + 5, controlsY + 100, btnWidth, 20).build();
        this.addDrawableChild(flipVButton);

        // Toggle Animation (if spritesheet detected)
        if (entry.getFrameCount() > 1) {
            animButton = ButtonWidget.builder(
                    Text.literal("Anim: " + (transform.isAnimated() ? "§aON (" + entry.getFrameCount() + "f)" : "§7OFF (Static)")),
                    btn -> {
                        transform.setAnimated(!transform.isAnimated());
                        btn.setMessage(Text.literal("Anim: " + (transform.isAnimated() ? "§aON (" + entry.getFrameCount() + "f)" : "§7OFF (Static)")));
                        TotemManager.getInstance().saveConfig();
                    }
            ).dimensions(centerX - 120, controlsY + 125, 240, 20).build();
            this.addDrawableChild(animButton);
        }

        int bottomY = controlsY + (entry.getFrameCount() > 1 ? 150 : 125);

        // Reset Button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Reset"),
                btn -> {
                    transform.reset();
                    TotemManager.getInstance().saveConfig();
                    this.clearAndInit();
                }
        ).dimensions(centerX - 120, bottomY, btnWidth, 20).build());

        // Back / Save Button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                btn -> this.close()
        ).dimensions(centerX + 5, bottomY, btnWidth, 20).build());
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        // Preview Box (84x84 pixels)
        int boxSize = 84;
        int previewX = this.width / 2 - boxSize / 2;
        int previewY = 24;

        // Background and border
        context.fill(previewX - 2, previewY - 2, previewX + boxSize + 2, previewY + boxSize + 2, 0xFF555555);
        context.fill(previewX, previewY, previewX + boxSize, previewY + boxSize, 0xFF141414);

        // Draw Totem Texture inside preview box
        if (entry.getTextureId() != null) {
            float[] bounds = entry.getQuadBounds();
            int qx1 = (int) (previewX + bounds[0] * boxSize);
            int qx2 = (int) (previewX + bounds[1] * boxSize);
            int qy1 = (int) (previewY + (1.0f - bounds[3]) * boxSize);
            int qy2 = (int) (previewY + (1.0f - bounds[2]) * boxSize);

            long tick = TotemManager.getInstance().getClientTicks();
            float[] uvs = entry.getRenderUVs(tick);

            context.enableScissor(previewX, previewY, previewX + boxSize, previewY + boxSize);
            context.drawTexturedQuad(entry.getTextureId(), qx1, qy1, qx2, qy2, uvs[0], uvs[1], uvs[2], uvs[3]);
            context.disableScissor();
        }

        // Draw outline 1:1 canvas frame
        context.fill(previewX, previewY, previewX + boxSize, previewY + 1, 0xFF888888);
        context.fill(previewX, previewY + boxSize - 1, previewX + boxSize, previewY + boxSize, 0xFF888888);
        context.fill(previewX, previewY, previewX + 1, previewY + boxSize, 0xFF888888);
        context.fill(previewX + boxSize - 1, previewY, previewX + boxSize, previewY + boxSize, 0xFF888888);

        // Resolution label below preview
        String resText = String.format("Original: %dx%d (%s)", entry.getWidth(), entry.getHeight(),
                entry.getFrameCount() > 1 ? entry.getFrameCount() + " frames" : "Single image");
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + resText), this.width / 2, previewY + boxSize + 5, 0xAAAAAA);
    }
}
