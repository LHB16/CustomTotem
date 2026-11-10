package com.customtotem.web;

import com.customtotem.manager.TotemManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class TotemWebServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotemMod");
    private static final TotemWebServer INSTANCE = new TotemWebServer();

    private HttpServer server = null;
    private int port = 8080;
    private boolean running = false;

    private TotemWebServer() {}

    public static TotemWebServer getInstance() {
        return INSTANCE;
    }

    public synchronized boolean start() {
        if (running && server != null) {
            return true;
        }

        int[] ports = {8080, 8081, 8082, 8888, 9090};
        for (int p : ports) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                server.createContext("/", new WebPageHandler());
                server.createContext("/upload", new UploadHandler());
                server.setExecutor(null);
                server.start();
                this.port = p;
                this.running = true;
                LOGGER.info("Totem Web Server started on port {}", p);
                return true;
            } catch (Exception e) {
                LOGGER.warn("Port {} is unavailable, trying next...", p);
            }
        }
        return false;
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Exception ignored) {}
            server = null;
        }
        running = false;
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    public String getUrl() {
        return "http://localhost:" + port;
    }

    public String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public String getLanUrl() {
        return "http://" + getLocalIpAddress() + ":" + port;
    }

    private static class WebPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String html = """
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Custom Totem - Nạp Ảnh Không Lo Văng Game</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: #121214; color: #e1e1e6; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
        .card { background: #202024; border: 1px solid #323238; border-radius: 16px; padding: 24px; max-width: 460px; width: 100%; box-shadow: 0 10px 25px rgba(0,0,0,0.5); text-align: center; }
        h1 { font-size: 20px; margin-bottom: 8px; color: #00b37e; }
        p.subtitle { font-size: 13px; color: #8d8d99; margin-bottom: 18px; line-height: 1.5; }
        .target-selector { display: flex; gap: 8px; margin-bottom: 16px; }
        .target-btn { flex: 1; padding: 10px; border-radius: 8px; border: 1px solid #323238; background: #18181b; color: #a1a1aa; font-size: 13px; font-weight: 600; cursor: pointer; transition: 0.2s; }
        .target-btn.active { background: #00875f; color: #fff; border-color: #00b37e; }
        .btn-choose { display: block; width: 100%; padding: 15px; border-radius: 10px; background: #8257e5; color: #fff; font-size: 16px; font-weight: 600; cursor: pointer; border: none; margin-bottom: 12px; transition: 0.2s; box-shadow: 0 4px 12px rgba(130,87,229,0.3); }
        .btn-choose:hover { background: #996dff; }
        .status { margin-top: 14px; font-size: 14px; min-height: 24px; font-weight: 500; line-height: 1.5; }
        .status.success { color: #04d361; }
        .status.error { color: #f75a68; }
        .status.info { color: #38bdf8; }
        input[type="file"] { display: none; }
        .tip { font-size: 12px; color: #71717a; margin-top: 16px; border-top: 1px solid #27272a; padding-top: 12px; }
    </style>
</head>
<body>
    <div class="card">
        <h1>📱 Nạp Ảnh Totem & Túi Đồ</h1>
        <p class="subtitle">Có thể nạp từ bất kỳ thiết bị nào (máy tính, điện thoại khác kết nối cùng WiFi) sang máy đang chơi Minecraft</p>

        <div class="target-selector">
            <button class="target-btn active" id="btnTotem" onclick="setTarget('totem')">🗡 Totem Cầm Tay</button>
            <button class="target-btn" id="btnInv" onclick="setTarget('inv')">🎒 Giao Diện Túi Đồ</button>
        </div>

        <input type="file" id="fileInput" accept="image/png,image/jpeg,image/webp" multiple />
        
        <button class="btn-choose" onclick="document.getElementById('fileInput').click()">
            🖼 Chọn Ảnh Từ Thư Viện (Hỗ Trợ Nhiều Ảnh)
        </button>

        <div class="status" id="status"></div>
        <div class="tip">Ảnh được tải lên sẽ tự động nạp vào game ngay lập tức mà không cần bấm thêm nút!</div>
    </div>

    <script>
        let currentTarget = 'totem';
        const fileInput = document.getElementById('fileInput');
        const status = document.getElementById('status');

        function setTarget(t) {
            currentTarget = t;
            document.getElementById('btnTotem').className = 'target-btn' + (t === 'totem' ? ' active' : '');
            document.getElementById('btnInv').className = 'target-btn' + (t === 'inv' ? ' active' : '');
        }

        fileInput.addEventListener('change', async function(e) {
            const files = Array.from(e.target.files);
            if (!files.length) return;

            status.className = 'status info';
            status.innerText = 'Đang tự động nạp ' + files.length + ' ảnh...';

            let successCount = 0;
            let lastError = '';

            for (let i = 0; i < files.length; i++) {
                const f = files[i];
                status.innerText = 'Đang nạp ' + (i + 1) + '/' + files.length + ': ' + f.name;
                try {
                    const resp = await fetch('/upload', {
                        method: 'POST',
                        headers: {
                            'X-Filename': encodeURIComponent(f.name),
                            'X-Target': currentTarget
                        },
                        body: f
                    });
                    const res = await resp.json();
                    if (res.success) {
                        successCount++;
                    } else {
                        lastError = res.error || 'Lỗi server';
                    }
                } catch (err) {
                    lastError = err.message;
                }
            }

            if (successCount === files.length) {
                status.className = 'status success';
                status.innerHTML = '✔ Đã nạp thành công <b>' + successCount + ' ảnh</b>!<br>Minecraft đã tự động cập nhật ngay lập tức.';
            } else if (successCount > 0) {
                status.className = 'status info';
                status.innerHTML = '✔ Đã nạp thành công ' + successCount + '/' + files.length + ' ảnh.<br>Lỗi: ' + lastError;
            } else {
                status.className = 'status error';
                status.innerText = '✗ Thất bại: ' + lastError;
            }
            fileInput.value = '';
        });
    </script>
</body>
</html>
""";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String rawFileName = exchange.getRequestHeaders().getFirst("X-Filename");
            String fileName = "mobile_totem_" + System.currentTimeMillis() + ".png";
            if (rawFileName != null && !rawFileName.trim().isEmpty()) {
                try {
                    fileName = java.net.URLDecoder.decode(rawFileName, StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }

            // Read image bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = exchange.getRequestBody()) {
                is.transferTo(baos);
            }
            byte[] rawBytes = baos.toByteArray();

            if (rawBytes.length == 0) {
                sendJson(exchange, 400, "{\"success\":false,\"error\":\"Dữ liệu rỗng\"}");
                return;
            }

            try {
                // Decode image to ensure valid format and convert to PNG if needed
                BufferedImage bImg = ImageIO.read(new ByteArrayInputStream(rawBytes));
                if (bImg == null) {
                    sendJson(exchange, 400, "{\"success\":false,\"error\":\"Định dạng ảnh không hợp lệ\"}");
                    return;
                }

                // Clean filename
                String baseName = fileName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                if (baseName.toLowerCase(Locale.ROOT).endsWith(".png")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                if (baseName.startsWith(".") || baseName.startsWith("_")) {
                    baseName = "totem_" + baseName.replaceAll("^[_.]+", "");
                }

                String targetParam = exchange.getRequestHeaders().getFirst("X-Target");
                boolean isInv = "inv".equalsIgnoreCase(targetParam);
                Path targetDir = isInv ? TotemManager.getInstance().getInventoryGuiDir() : TotemManager.getInstance().getImagesDir();
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }

                Path targetFile = targetDir.resolve(baseName + ".png");
                int c = 1;
                while (Files.exists(targetFile)) {
                    targetFile = targetDir.resolve(baseName + "_" + c + ".png");
                    c++;
                }

                // Save as PNG
                ImageIO.write(bImg, "PNG", targetFile.toFile());

                // Trigger reload in Minecraft
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.execute(() -> {
                        if (isInv) {
                            TotemManager.getInstance().scanInventoryImages();
                            TotemManager.getInstance().loadInventoryGuiTexture();
                        } else {
                            TotemManager.getInstance().rescanImages();
                        }
                    });
                }

                String finalName = targetFile.getFileName().toString();
                sendJson(exchange, 200, "{\"success\":true,\"name\":\"" + finalName + "\"}");
                LOGGER.info("Successfully received and saved mobile image: {}", targetFile);
            } catch (Exception e) {
                LOGGER.error("Failed to process uploaded image", e);
                sendJson(exchange, 500, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
