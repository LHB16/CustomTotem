package com.customtotem.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class ImageDownloadUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotemMod");

    public record DownloadResult(boolean success, Path savedFile, String fileName, String errorMessage) {}

    public static CompletableFuture<DownloadResult> downloadImageAsync(String urlStr, Path targetDir, String customName) {
        return CompletableFuture.supplyAsync(() -> downloadImageSync(urlStr, targetDir, customName));
    }

    public static DownloadResult downloadImageSync(String urlStr, Path targetDir, String customName) {
        if (urlStr == null || urlStr.trim().isEmpty()) {
            return new DownloadResult(false, null, null, "Đường link URL không được để trống!");
        }

        urlStr = urlStr.trim();
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "https://" + urlStr;
        }

        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/*;q=0.8,*/*;q=0.5");

            int responseCode = conn.getResponseCode();
            // Handle HTTP 301, 302, 307, 308 manually if needed
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == 307 || responseCode == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null && !newUrl.isEmpty()) {
                    conn.disconnect();
                    url = URI.create(newUrl).toURL();
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/*;q=0.8,*/*;q=0.5");
                    responseCode = conn.getResponseCode();
                }
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return new DownloadResult(false, null, null, "Lỗi máy chủ HTTP " + responseCode + " (" + conn.getResponseMessage() + ")");
            }

            BufferedImage image;
            try (InputStream in = conn.getInputStream()) {
                image = ImageIO.read(in);
            }

            if (image == null) {
                return new DownloadResult(false, null, null, "Không thể đọc dữ liệu ảnh! Hãy kiểm tra lại link ảnh trực tiếp (.png/.jpg/.webp).");
            }

            String finalName = sanitizeFileName(customName);
            if (finalName == null || finalName.isEmpty()) {
                finalName = extractFileNameFromUrl(urlStr);
            }
            if (finalName == null || finalName.isEmpty()) {
                finalName = "downloaded_totem_" + System.currentTimeMillis() + ".png";
            }
            if (!finalName.toLowerCase(Locale.ROOT).endsWith(".png")) {
                finalName = finalName + ".png";
            }

            Path destination = targetDir.resolve(finalName);
            int counter = 1;
            while (Files.exists(destination)) {
                String base = finalName.substring(0, finalName.length() - 4);
                destination = targetDir.resolve(base + "_" + counter + ".png");
                counter++;
            }

            boolean written = ImageIO.write(image, "PNG", destination.toFile());
            if (!written) {
                return new DownloadResult(false, null, null, "Không thể lưu định dạng PNG vào ổ đĩa!");
            }

            LOGGER.info("Successfully downloaded and saved image to {}", destination);
            return new DownloadResult(true, destination, destination.getFileName().toString(), null);

        } catch (Exception e) {
            LOGGER.error("Failed to download image from {}", urlStr, e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new DownloadResult(false, null, null, "Lỗi kết nối: " + msg);
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return null;
        name = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return name.isEmpty() ? null : name;
    }

    private static String extractFileNameFromUrl(String urlStr) {
        try {
            int qIdx = urlStr.indexOf('?');
            if (qIdx != -1) {
                urlStr = urlStr.substring(0, qIdx);
            }
            int hIdx = urlStr.indexOf('#');
            if (hIdx != -1) {
                urlStr = urlStr.substring(0, hIdx);
            }
            int slashIdx = urlStr.lastIndexOf('/');
            if (slashIdx != -1 && slashIdx < urlStr.length() - 1) {
                String rawName = urlStr.substring(slashIdx + 1);
                rawName = java.net.URLDecoder.decode(rawName, java.nio.charset.StandardCharsets.UTF_8);
                return sanitizeFileName(rawName);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
