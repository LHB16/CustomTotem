package com.customtotem.baker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Bakes large PNG spritesheets into raw flat RGBA8 .bin cache files.
 * Streams row strips (width x frameHeight) to keep peak RAM <= ~70 MB.
 */
public class TotemBaker {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotem/Baker");
    public static final int HEADER_MAGIC = 0x544F544D; // "TOTM"
    public static final int HEADER_SIZE = 20;

    public record BakeResult(Path binPath, Path thumbPath, int frameWidth, int frameHeight, int frameCount, int fps) {}

    public static String computeCacheKey(Path sourcePng, int cols, int rows, int frameCount, int fps) {
        try {
            long size = Files.size(sourcePng);
            long mtime = Files.getLastModifiedTime(sourcePng).toMillis();
            return String.format("%s_%d_%d_%dx%d_%df_%dfps",
                    sourcePng.getFileName().toString().replace(".png", ""),
                    size, mtime, cols, rows, frameCount, fps)
                    .replaceAll("[^a-zA-Z0-9_-]", "_");
        } catch (IOException e) {
            return sourcePng.getFileName().toString().replace(".png", "");
        }
    }

    public static BakeResult bake(
            Path sourcePng,
            Path outputDir,
            int cols,
            int rows,
            int frameCount,
            int fps,
            AtomicBoolean cancelToken,
            Consumer<Float> progressCallback
    ) throws IOException {
        Files.createDirectories(outputDir);
        String cacheKey = computeCacheKey(sourcePng, cols, rows, frameCount, fps);
        Path targetBin = outputDir.resolve(cacheKey + ".bin");
        Path targetThumb = outputDir.resolve(cacheKey + ".thumb.png");

        if (Files.exists(targetBin) && Files.exists(targetThumb) && Files.size(targetBin) > HEADER_SIZE) {
            int frameW = 1024;
            int frameH = 1024;
            try (FileChannel fc = FileChannel.open(targetBin, StandardOpenOption.READ)) {
                ByteBuffer hdr = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
                fc.read(hdr);
                hdr.flip();
                if (hdr.getInt() == HEADER_MAGIC) {
                    frameW = hdr.getInt();
                    frameH = hdr.getInt();
                    int fcCount = hdr.getInt();
                    int fileFps = hdr.getInt();
                    return new BakeResult(targetBin, targetThumb, frameW, frameH, fcCount, fileFps);
                }
            } catch (Exception ignored) {}
        }

        Path tempBin = outputDir.resolve(cacheKey + ".bin.tmp");
        Path tempThumb = outputDir.resolve(cacheKey + ".thumb.png.tmp");

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new IOException("No PNG ImageReader available in runtime!");
        }
        ImageReader reader = readers.next();

        try (ImageInputStream iis = ImageIO.createImageInputStream(sourcePng.toFile())) {
            reader.setInput(iis, true, true);
            int sheetW = reader.getWidth(0);
            int sheetH = reader.getHeight(0);

            int safeCols = Math.max(1, cols);
            int safeRows = Math.max(1, rows);
            int frameW = sheetW / safeCols;
            int frameH = sheetH / safeRows;
            int totalFrames = Math.min(frameCount, safeCols * safeRows);
            int frameSizeBytes = frameW * frameH * 4;

            ByteBuffer frameBuffer = ByteBuffer.allocateDirect(frameSizeBytes).order(ByteOrder.LITTLE_ENDIAN);

            try (FileChannel outChannel = FileChannel.open(tempBin,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                // 1. Write Header (20 Bytes Little-Endian)
                ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
                header.putInt(HEADER_MAGIC);
                header.putInt(frameW);
                header.putInt(frameH);
                header.putInt(totalFrames);
                header.putInt(fps);
                header.flip();
                outChannel.write(header);

                ImageReadParam param = reader.getDefaultReadParam();
                int writtenFrames = 0;
                boolean thumbGenerated = false;

                // 2. Read by row strip (Height = frameH, Width = sheetW) -> Peak RAM <= 70 MB
                for (int r = 0; r < safeRows && writtenFrames < totalFrames; r++) {
                    if (cancelToken != null && cancelToken.get()) {
                        throw new IOException("Bake operation cancelled by user");
                    }

                    int stripY = r * frameH;
                    param.setSourceRegion(new Rectangle(0, stripY, sheetW, frameH));
                    BufferedImage stripImage = reader.read(0, param);

                    // Generate thumbnail 128x128 from frame 0
                    if (!thumbGenerated && r == 0) {
                        BufferedImage thumbImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2 = thumbImg.createGraphics();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2.drawImage(stripImage, 0, 0, 128, 128, 0, 0, frameW, frameH, null);
                        g2.dispose();
                        ImageIO.write(thumbImg, "png", tempThumb.toFile());
                        thumbGenerated = true;
                    }

                    // Extract each tile in this row strip
                    int[] argbRowPixels = new int[frameW * frameH];
                    for (int c = 0; c < safeCols && writtenFrames < totalFrames; c++) {
                        stripImage.getRGB(c * frameW, 0, frameW, frameH, argbRowPixels, 0, frameW);

                        frameBuffer.clear();
                        for (int argb : argbRowPixels) {
                            byte a = (byte) ((argb >> 24) & 0xFF);
                            byte red = (byte) ((argb >> 16) & 0xFF);
                            byte g = (byte) ((argb >> 8) & 0xFF);
                            byte b = (byte) (argb & 0xFF);
                            // Store as raw RGBA8 byte order
                            frameBuffer.put(red).put(g).put(b).put(a);
                        }
                        frameBuffer.flip();
                        outChannel.write(frameBuffer);

                        writtenFrames++;
                        if (progressCallback != null) {
                            progressCallback.accept((float) writtenFrames / (float) totalFrames);
                        }
                    }
                }
            }

            Files.move(tempBin, targetBin, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(tempThumb)) {
                Files.move(tempThumb, targetThumb, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }

            return new BakeResult(targetBin, targetThumb, frameW, frameH, totalFrames, fps);
        } catch (Throwable t) {
            try { Files.deleteIfExists(tempBin); } catch (Exception ignored) {}
            try { Files.deleteIfExists(tempThumb); } catch (Exception ignored) {}
            throw (t instanceof IOException e) ? e : new IOException("Bake failed: " + t.getMessage(), t);
        } finally {
            reader.dispose();
        }
    }
}
