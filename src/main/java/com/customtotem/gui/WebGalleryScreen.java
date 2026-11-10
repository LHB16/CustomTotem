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
        super(Text.literal("Chọn Ảnh Từ Thư Viện (Mobile Gallery)"));
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
                Text.literal("🌐 Mở Trình Duyệt Điện Thoại"),
                btn -> {
                    String url = TotemWebServer.getInstance().getUrl();
                    try {
                        Util.getOperatingSystem().open(url);
                        setStatus("§aĐang mở trình duyệt: " + url);
                    } catch (Exception e) {
                        setStatus("§cKhông thể tự mở. Hãy tự vào: " + url);
                    }
                }
        ).dimensions(centerX - btnWidth / 2, startY, btnWidth, 20).build());

        // Button 2: Copy Link to clipboard
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📋 Sao Chép Link (Copy URL)"),
                btn -> {
                    String url = TotemWebServer.getInstance().getUrl();
                    MinecraftClient.getInstance().keyboard.setClipboard(url);
                    setStatus("§aĐã sao chép link: " + url + " vào bộ nhớ tạm!");
                }
        ).dimensions(centerX - btnWidth / 2, startY + 26, btnWidth, 20).build());

        // Button 3: Rescan and Return
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🔄 Đã Tải Ảnh Xong - Cập Nhật Ngay"),
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
                Text.literal("Quay Lại"),
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
                Text.literal("§7Trên máy này: §e" + localUrl + " §7| Thiết bị khác (cùng WiFi): §a" + lanUrl),
                this.width / 2,
                34,
                0xFFFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§e💡 Mẹo tránh văng game: Dùng máy tính hoặc điện thoại khác truy cập link xanh ở trên để nạp ảnh"),
                this.width / 2,
                50,
                0xFFFFFF55
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§fHoặc sử dụng tính năng '🌐 Tải Link' hay '📁 Thư Mục' để chọn ảnh trực tiếp trong game!"),
                this.width / 2,
                64,
                0xFFDDDDDD
        );

        if (System.currentTimeMillis() < statusExpireTime && !statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusMessage), this.width / 2, this.height - 24, 0xFF55FF55);
        }
    }
}
