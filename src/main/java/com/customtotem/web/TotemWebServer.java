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
    <title>Custom Totem - Thư Viện Ảnh</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: #121214; color: #e1e1e6; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
        .card { background: #202024; border: 1px solid #323238; border-radius: 16px; padding: 24px; max-width: 440px; width: 100%; box-shadow: 0 10px 25px rgba(0,0,0,0.5); text-align: center; }
        h1 { font-size: 20px; margin-bottom: 8px; color: #00b37e; }
        p.subtitle { font-size: 14px; color: #8d8d99; margin-bottom: 20px; }
        .preview-box { width: 140px; height: 140px; border: 2px dashed #323238; border-radius: 12px; margin: 0 auto 20px; display: flex; align-items: center; justify-content: center; background: #121214; overflow: hidden; position: relative; }
        .preview-box img { max-width: 100%; max-height: 100%; object-fit: contain; }
        .preview-placeholder { font-size: 13px; color: #7c7c8a; }
        .btn-choose { display: block; width: 100%; padding: 14px; border-radius: 8px; background: #00875f; color: #fff; font-size: 16px; font-weight: 600; cursor: pointer; border: none; margin-bottom: 12px; transition: 0.2s; }
        .btn-choose:hover { background: #00b37e; }
        .btn-upload { display: none; width: 100%; padding: 14px; border-radius: 8px; background: #8257e5; color: #fff; font-size: 16px; font-weight: 600; cursor: pointer; border: none; }
        .btn-upload:hover { background: #996dff; }
        .status { margin-top: 16px; font-size: 14px; min-height: 24px; font-weight: 500; }
        .status.success { color: #04d361; }
        .status.error { color: #f75a68; }
        input[type="file"] { display: none; }
        .tip { font-size: 12px; color: #666; margin-top: 16px; }
    </style>
</head>
<body>
    <div class="card">
        <h1>📱 Chọn Ảnh Từ Thư Viện</h1>
        <p class="subtitle">Chạm vào nút bên dưới để mở Album / Thư viện ảnh của điện thoại và nạp vào Minecraft</p>

        <div class="preview-box" id="previewBox">
            <span class="preview-placeholder" id="placeholder">Xem trước ảnh</span>
            <img id="previewImg" style="display:none;" />
        </div>

        <input type="file" id="fileInput" accept="image/png,image/jpeg,image/webp" />
        
        <button class="btn-choose" id="chooseBtn" onclick="document.getElementById('fileInput').click()">
            🖼 Mở Thư Viện Ảnh (Gallery)
        </button>

        <button class="btn-upload" id="uploadBtn" onclick="uploadImage()">
            ⬆ Tải Lên Minecraft
        </button>

        <div class="status" id="status"></div>
        <div class="tip">Sau khi tải lên thành công, hãy chuyển lại vào game để thấy totem mới ngay lập tức!</div>
    </div>

    <script>
        let selectedFile = null;
        const fileInput = document.getElementById('fileInput');
        const previewImg = document.getElementById('previewImg');
        const placeholder = document.getElementById('placeholder');
        const uploadBtn = document.getElementById('uploadBtn');
        const chooseBtn = document.getElementById('chooseBtn');
        const status = document.getElementById('status');

        fileInput.addEventListener('change', function(e) {
            if (e.target.files && e.target.files[0]) {
                selectedFile = e.target.files[0];
                const reader = new FileReader();
                reader.onload = function(evt) {
                    previewImg.src = evt.target.result;
                    previewImg.style.display = 'block';
                    placeholder.style.display = 'none';
                    uploadBtn.style.display = 'block';
                    chooseBtn.innerText = '🔄 Chọn Ảnh Khác';
                    status.className = 'status';
                    status.innerText = '';
                };
                reader.readAsDataURL(selectedFile);
            }
        });

        async function uploadImage() {
            if (!selectedFile) return;
            status.className = 'status';
            status.innerText = 'Đang tải lên...';
            uploadBtn.disabled = true;

            try {
                const formData = new FormData();
                formData.append('file', selectedFile, selectedFile.name);
                const resp = await fetch('/upload', {
                    method: 'POST',
                    headers: { 'X-Filename': encodeURIComponent(selectedFile.name) },
                    body: selectedFile
                });
                const res = await resp.json();
                if (res.success) {
                    status.className = 'status success';
                    status.innerHTML = '✔ Đã nạp thành công: <b>' + res.name + '</b>!<br>Hãy quay lại game để xem Totem.';
                    uploadBtn.style.display = 'none';
                } else {
                    status.className = 'status error';
                    status.innerText = '✗ Thất bại: ' + (res.error || 'Lỗi không xác định');
                    uploadBtn.disabled = false;
                }
            } catch (err) {
                status.className = 'status error';
                status.innerText = '✗ Lỗi kết nối: ' + err.message;
                uploadBtn.disabled = false;
            }
        }
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

                Path targetDir = TotemManager.getInstance().getImagesDir();
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
                    client.execute(() -> TotemManager.getInstance().rescanImages());
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
