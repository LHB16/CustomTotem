package com.customtotem.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class SettingsListWidget extends ElementListWidget<SettingsListWidget.SettingEntry> {
    private int customRowWidth = -1;
    private boolean renderBackground = true;

    public SettingsListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    public void setCustomRowWidth(int customRowWidth) {
        this.customRowWidth = customRowWidth;
    }

    public void setRenderBackground(boolean renderBackground) {
        this.renderBackground = renderBackground;
    }

    @Override
    public int getRowWidth() {
        if (this.customRowWidth > 0) {
            return Math.min(this.customRowWidth, this.width - 16);
        }
        return Math.min(this.width - 24, 340);
    }

    @Override
    protected int getScrollbarX() {
        return Math.min(super.getScrollbarX(), this.getX() + this.width - 6);
    }

    @Override
    protected boolean isEntrySelectionAllowed() {
        return false;
    }

    @Override
    protected void drawMenuListBackground(DrawContext context) {
        if (this.renderBackground) {
            super.drawMenuListBackground(context);
        }
    }

    @Override
    protected void drawHeaderAndFooterSeparators(DrawContext context) {
        if (this.renderBackground) {
            super.drawHeaderAndFooterSeparators(context);
        }
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

    public void addWidget(ClickableWidget widget) {
        this.addEntry(new SettingEntry(List.of(widget)));
    }

    public void addWidgets(ClickableWidget left, ClickableWidget right) {
        this.addEntry(new SettingEntry(List.of(left, right)));
    }

    public void addWidgets(ClickableWidget... widgets) {
        this.addEntry(new SettingEntry(List.of(widgets)));
    }

    public void addWidgets(List<ClickableWidget> widgets) {
        this.addEntry(new SettingEntry(widgets));
    }

    public void addCycleRow(ClickableWidget left, ClickableWidget middle, ClickableWidget right, int sideBtnWidth) {
        this.addEntry(new CycleRowEntry(left, middle, right, sideBtnWidth));
    }

    public void addHeader(Text text) {
        this.addHeader(text, 0xFFFFAA00);
    }

    public void addHeader(Text text, int color) {
        this.addEntry(new HeaderEntry(text, color));
    }

    public static class SettingEntry extends ElementListWidget.Entry<SettingEntry> {
        protected final List<ClickableWidget> children;

        public SettingEntry(List<ClickableWidget> widgets) {
            this.children = new ArrayList<>(widgets);
        }

        @Override
        public List<? extends Element> children() {
            return this.children;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return this.children;
        }

        protected void updateChildBounds() {
            if (children.isEmpty()) return;

            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int widgetHeight = Math.min(20, height);
            int widgetY = y + (height - widgetHeight) / 2;

            if (children.size() == 1) {
                ClickableWidget w = children.get(0);
                w.setX(x);
                w.setY(widgetY);
                w.setWidth(width);
            } else if (children.size() == 2) {
                int gap = 4;
                int halfW = (width - gap) / 2;
                ClickableWidget left = children.get(0);
                ClickableWidget right = children.get(1);

                left.setX(x);
                left.setY(widgetY);
                left.setWidth(halfW);

                right.setX(x + halfW + gap);
                right.setY(widgetY);
                right.setWidth(width - halfW - gap);
            } else {
                int gap = 4;
                int totalGaps = gap * (children.size() - 1);
                int itemW = Math.max(16, (width - totalGaps) / children.size());
                int curX = x;
                for (int i = 0; i < children.size(); i++) {
                    ClickableWidget w = children.get(i);
                    w.setX(curX);
                    w.setY(widgetY);
                    if (i == children.size() - 1) {
                        w.setWidth(x + width - curX);
                    } else {
                        w.setWidth(itemW);
                    }
                    curX += itemW + gap;
                }
            }
        }

        @Override
        public boolean mouseClicked(Click click, boolean bl) {
            updateChildBounds();
            return super.mouseClicked(click, bl);
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            updateChildBounds();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            updateChildBounds();
        }

        @Override
        public void setWidth(int width) {
            super.setWidth(width);
            updateChildBounds();
        }

        @Override
        public void setHeight(int height) {
            super.setHeight(height);
            updateChildBounds();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            updateChildBounds();
            for (ClickableWidget w : children) {
                w.render(context, mouseX, mouseY, tickDelta);
            }
        }
    }

    public static class CycleRowEntry extends SettingEntry {
        private final ClickableWidget left;
        private final ClickableWidget middle;
        private final ClickableWidget right;
        private final int sideBtnWidth;

        public CycleRowEntry(ClickableWidget left, ClickableWidget middle, ClickableWidget right, int sideBtnWidth) {
            super(List.of(left, middle, right));
            this.left = left;
            this.middle = middle;
            this.right = right;
            this.sideBtnWidth = sideBtnWidth;
        }

        @Override
        protected void updateChildBounds() {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int widgetHeight = Math.min(20, height);
            int widgetY = y + (height - widgetHeight) / 2;

            int gap = 3;
            int midW = Math.max(40, width - (sideBtnWidth * 2) - (gap * 2));

            left.setX(x);
            left.setY(widgetY);
            left.setWidth(sideBtnWidth);

            middle.setX(x + sideBtnWidth + gap);
            middle.setY(widgetY);
            middle.setWidth(midW);

            right.setX(x + sideBtnWidth + gap + midW + gap);
            right.setY(widgetY);
            right.setWidth(sideBtnWidth);
        }
    }

    public static class HeaderEntry extends SettingEntry {
        private final Text text;
        private final int color;

        public HeaderEntry(Text text, int color) {
            super(List.of());
            this.text = text;
            this.color = color;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int cx = getX() + getWidth() / 2;
            int cy = getY() + (getHeight() - 9) / 2;
            context.drawCenteredTextWithShadow(tr, this.text, cx, cy, this.color);
        }
    }
}
