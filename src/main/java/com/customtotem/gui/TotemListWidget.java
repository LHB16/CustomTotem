package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemEntry;
import com.customtotem.model.TotemTextureTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class TotemListWidget extends ElementListWidget<TotemListWidget.TotemEntryWidget> {
    private final TotemManagerScreen screen;

    public TotemListWidget(MinecraftClient client, int width, int height, int y, int itemHeight, TotemManagerScreen screen) {
        super(client, width, height, y, itemHeight);
        this.screen = screen;
        refreshEntries();
    }

    public void scroll(double amount) {
        setScrollY(getScrollY() + amount);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (super.mouseDragged(click, deltaX, deltaY)) {
            return true;
        }
        if (overflows()) {
            setScrollY(getScrollY() - deltaY);
            return true;
        }
        return false;
    }

    public void refreshEntries() {
        this.clearEntries();
        for (TotemEntry entry : TotemManager.getInstance().getEntries()) {
            this.addEntry(new TotemEntryWidget(entry));
        }
    }

    @Override
    public int getRowWidth() {
        return Math.min(this.width - 20, 560);
    }

    public class TotemEntryWidget extends ElementListWidget.Entry<TotemEntryWidget> {
        private final TotemEntry entry;
        private final ButtonWidget toggleButton;
        private final ButtonWidget selectButton;
        private final ButtonWidget unloadButton;
        private final ButtonWidget editButton;
        private final ButtonWidget upButton;
        private final ButtonWidget downButton;
        private final ButtonWidget deleteButton;
        private final List<ClickableWidget> children = new ArrayList<>();

        public TotemEntryWidget(TotemEntry entry) {
            this.entry = entry;

            // Toggle active button
            this.toggleButton = ButtonWidget.builder(
                    Text.literal(entry.isEnabled() ? "§aActive" : "§7Off"),
                    btn -> {
                        entry.setEnabled(!entry.isEnabled());
                        TotemManager.getInstance().onEntryToggle(entry);
                        screen.refreshList();
                    }
            ).dimensions(0, 0, 44, 20).build();

            // Select button
            boolean isCurrent = TotemManager.getInstance().getCurrentActiveTotem() == entry;
            this.selectButton = ButtonWidget.builder(
                    Text.literal(isCurrent ? "§2In Use" : "Select"),
                    btn -> {
                        TotemManager.getInstance().setCurrentTotem(entry);
                        screen.refreshList();
                    }
            ).dimensions(0, 0, 50, 20).build();
            if (isCurrent) {
                this.selectButton.active = false;
            }

            // Unload texture from RAM button
            boolean isLoaded = entry.isLoadedInRam();
            this.unloadButton = ButtonWidget.builder(
                    Text.literal("Unload"),
                    btn -> {
                        TotemManager.getInstance().unloadTotemTexture(entry);
                        screen.refreshList();
                    }
            ).dimensions(0, 0, 44, 20).build();
            this.unloadButton.active = isLoaded;

            // Edit / Adjust Canvas button
            this.editButton = ButtonWidget.builder(
                    Text.literal("✎"),
                    btn -> {
                        if (MinecraftClient.getInstance() != null) {
                            MinecraftClient.getInstance().setScreen(new TextureAdjustScreen(screen, entry));
                        }
                    }
            ).dimensions(0, 0, 20, 20).build();

            // Move up button
            this.upButton = ButtonWidget.builder(
                    Text.literal("▲"),
                    btn -> {
                        TotemManager.getInstance().moveUp(entry);
                        screen.refreshList();
                    }
            ).dimensions(0, 0, 20, 20).build();

            // Move down button
            this.downButton = ButtonWidget.builder(
                    Text.literal("▼"),
                    btn -> {
                        TotemManager.getInstance().moveDown(entry);
                        screen.refreshList();
                    }
            ).dimensions(0, 0, 20, 20).build();

            // Delete / Disable button (renames file to .disable instead of deleting)
            this.deleteButton = ButtonWidget.builder(
                    Text.literal("§c✕"),
                    btn -> {
                        TotemManager.getInstance().removeTotem(entry);
                        screen.refreshList();
                    }
            ).dimensions(0, 0, 20, 20)
            .tooltip(Tooltip.of(Text.literal("Disable totem (renames to .disable)")))
            .build();

            this.children.add(toggleButton);
            this.children.add(selectButton);
            this.children.add(unloadButton);
            this.children.add(editButton);
            this.children.add(upButton);
            this.children.add(downButton);
            this.children.add(deleteButton);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();

            // Viewport Culling: Skip rendering if item is completely outside visible scroll viewport
            if (y + height < TotemListWidget.this.getY() || y > TotemListWidget.this.getBottom()) {
                return;
            }

            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            boolean isCurrent = TotemManager.getInstance().getCurrentActiveTotem() == entry;

            // Highlight background if selected or hovered
            if (isCurrent) {
                context.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x4000AA00);
            } else if (hovered) {
                context.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x20FFFFFF);
            }

            // Thumbnail background box
            context.fill(x, y + 1, x + 26, y + 27, 0xFF1E1E1E);

            // Draw Thumbnail: Use dedicated lightweight thumbnail by default; animate if current active totem
            Identifier drawTex = (isCurrent && entry.isTextureRegistered() && entry.getTextureId() != null)
                    ? entry.getTextureId()
                    : entry.getThumbnailTextureId();

            if (drawTex != null) {
                float u0, u1, v0, v1;
                if (drawTex == entry.getTextureId()) {
                    long nowMs = net.minecraft.util.Util.getMeasuringTimeMs();
                    float[] uvs = entry.getRenderUVs(nowMs);
                    u0 = uvs[0];
                    u1 = uvs[1];
                    v0 = uvs[2];
                    v1 = uvs[3];
                } else {
                    // Frame 0 thumbnail texture (128x128 max)
                    TotemTextureTransform t = entry.getTransform();
                    u0 = t.isFlipX() ? 1.0f : 0.0f;
                    u1 = t.isFlipX() ? 0.0f : 1.0f;
                    v0 = t.isFlipY() ? 1.0f : 0.0f;
                    v1 = t.isFlipY() ? 0.0f : 1.0f;
                }

                float[] bounds = entry.getQuadBounds();
                float boxSize = 24.0f;
                int qx1 = (int) (x + 2 + bounds[0] * boxSize);
                int qx2 = (int) (x + 2 + bounds[1] * boxSize);
                int qy1 = (int) (y + 2 + (1.0f - bounds[3]) * boxSize);
                int qy2 = (int) (y + 2 + (1.0f - bounds[2]) * boxSize);

                context.enableScissor(x + 2, y + 2, x + 26, y + 26);
                context.drawTexturedQuad(drawTex, qx1, qy1, qx2, qy2, u0, u1, v0, v1);
                context.disableScissor();
            }

            // Text info
            String name = entry.getDisplayName();
            if (entry.isVramCritical()) {
                name = "§c⚠ " + name;
            } else if (entry.isVramHigh()) {
                name = "§e⚠ " + name;
            }
            int maxNameW = Math.max(45, width - 240 - 34);
            if (textRenderer.getWidth(name) > maxNameW) {
                name = textRenderer.trimToWidth(name, Math.max(30, maxNameW - 8)) + "...";
            }
            context.drawTextWithShadow(textRenderer, Text.literal(name), x + 30, y + 4, isCurrent ? 0xFF55FF55 : 0xFFFFFFFF);

            String info = entry.getWidth() + "x" + entry.getHeight();
            if (entry.getTransform().isAnimated() && entry.getFrameCount() > 1) {
                info += " (" + entry.getFrameCount() + "f, " + entry.getFps() + "fps)";
            }
            info += " " + entry.getVramBadge();
            if (entry.isLoadedInRam()) {
                info += " §a[RAM]";
            }
            if (entry.getTransform().getResolution() > 0) {
                info += " §b[" + entry.getTransform().getResolution() + "x]";
            } else if (entry.getTransform().getScale() != 1.0f || entry.getTransform().getOffsetX() != 0.0f || entry.getTransform().getOffsetY() != 0.0f) {
                info += " §e[Custom]";
            }
            context.drawTextWithShadow(textRenderer, Text.literal(info), x + 30, y + 16, 0xFF888888);

            // Position and render buttons on the right side
            int currentRight = x + width;
            int btnY = y + 4;

            deleteButton.setX(currentRight - 20);
            deleteButton.setY(btnY);
            deleteButton.render(context, mouseX, mouseY, tickDelta);
            currentRight -= 23;

            downButton.setX(currentRight - 20);
            downButton.setY(btnY);
            downButton.render(context, mouseX, mouseY, tickDelta);
            currentRight -= 23;

            upButton.setX(currentRight - 20);
            upButton.setY(btnY);
            upButton.render(context, mouseX, mouseY, tickDelta);
            currentRight -= 23;

            editButton.setX(currentRight - 20);
            editButton.setY(btnY);
            editButton.render(context, mouseX, mouseY, tickDelta);
            currentRight -= 23;

            unloadButton.setX(currentRight - 44);
            unloadButton.setY(btnY);
            unloadButton.active = entry.isLoadedInRam();
            unloadButton.render(context, mouseX, mouseY, tickDelta);
            currentRight -= 47;

            selectButton.setX(currentRight - 50);
            selectButton.setY(btnY);
            selectButton.render(context, mouseX, mouseY, tickDelta);
            currentRight -= 53;

            toggleButton.setX(currentRight - 44);
            toggleButton.setY(btnY);
            toggleButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends Element> children() {
            return this.children;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return this.children;
        }
    }
}
