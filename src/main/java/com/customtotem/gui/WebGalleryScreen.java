package com.customtotem.gui;

import com.customtotem.manager.TotemManager;
import com.customtotem.web.TotemWebServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class WebGalleryScreen extends Screen {
    private final Screen parent;
    private String statusMessage = "";
    private long statusExpireTime = 0;

    public WebGalleryScreen(Screen parent) {
        super(Text.literal("Web Gallery & WiFi Uploader"));
        this.parent = parent;
        TotemWebServer.getInstance().start();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 90;
        int btnWidth = 240;

        // Button 1: Open browser directly
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Open Browser"),
                btn -> {
                    String url = TotemWebServer.getInstance().getUrl();
                    try {
                        Util.getOperatingSystem().open(url);
                        setStatus("§aOpening browser: " + url);
                    } catch (Exception e) {
                        setStatus("§cCannot open browser. Please visit: " + url);
                    }
                }
        ).dimensions(centerX - btnWidth / 2, startY, btnWidth, 20).build());

        // Button 2: Copy Link to clipboard
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Copy URL"),
                btn -> {
                    String url = TotemWebServer.getInstance().getUrl();
                    MinecraftClient.getInstance().keyboard.setClipboard(url);
                    setStatus("§aCopied link: " + url + " to clipboard!");
                }
        ).dimensions(centerX - btnWidth / 2, startY + 26, btnWidth, 20).build());

        // Button 3: Rescan and Return
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Refresh & Return"),
                btn -> {
                    TotemManager.getInstance().rescanImages();
                    if (parent instanceof TotemManagerScreen tms) {
                        tms.refreshList();
                    }
                    this.close();
                }
        ).dimensions(centerX - btnWidth / 2, startY + 56, btnWidth, 20).build());

        // Button 4: Back
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> this.close()
        ).dimensions(centerX - btnWidth / 2, startY + 84, btnWidth, 20).build());
    }

    public void setStatus(String message) {
        this.statusMessage = message;
        this.statusExpireTime = System.currentTimeMillis() + 4000;
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

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFF55FF55);

        String localUrl = TotemWebServer.getInstance().getUrl();
        String lanUrl = TotemWebServer.getInstance().getLanUrl();
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§7Local: §e" + localUrl + " §7| Other Devices (Same WiFi): §a" + lanUrl),
                this.width / 2,
                34,
                0xFFFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§eTip: Access the green link from your phone or PC browser on the same WiFi"),
                this.width / 2,
                50,
                0xFFFFFF55
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§fOr use the 'Download' or 'Folder' buttons in-game!"),
                this.width / 2,
                64,
                0xFFDDDDDD
        );

        if (System.currentTimeMillis() < statusExpireTime && !statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusMessage), this.width / 2, this.height - 24, 0xFF55FF55);
        }
    }
}
