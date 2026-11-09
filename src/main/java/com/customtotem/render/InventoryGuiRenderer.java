package com.customtotem.render;

import com.customtotem.model.InventoryGuiSettings;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class InventoryGuiRenderer {

    public static void renderBackground(
            DrawContext context,
            Identifier texture,
            int imgW,
            int imgH,
            int targetX,
            int targetY,
            int targetW,
            int targetH,
            InventoryGuiSettings settings
    ) {
        if (texture == null || settings == null) {
            return;
        }

        if (imgW <= 0) imgW = 176;
        if (imgH <= 0) imgH = 166;

        int alpha = Math.max(25, Math.min(255, (int) (settings.getOpacity() * 255)));
        int color = (alpha << 24) | 0x00FFFFFF;

        InventoryGuiSettings.FitMode fitMode = settings.getFitMode();
        float scale = settings.getScale();
        float offsetX = settings.getOffsetX();
        float offsetY = settings.getOffsetY();

        if (fitMode == InventoryGuiSettings.FitMode.FIT) {
            float aspectImg = (float) imgW / (float) imgH;
            float aspectTarget = (float) targetW / (float) targetH;

            int drawW, drawH;
            if (aspectImg >= aspectTarget) {
                drawW = targetW;
                drawH = Math.max(1, Math.round(targetW / aspectImg));
            } else {
                drawH = targetH;
                drawW = Math.max(1, Math.round(targetH * aspectImg));
            }

            int drawX = targetX + (targetW - drawW) / 2;
            int drawY = targetY + (targetH - drawH) / 2;

            if (scale != 1.0f || offsetX != 0.0f || offsetY != 0.0f) {
                float cropW = imgW / scale;
                float cropH = imgH / scale;
                float maxShiftX = Math.max(0.0f, (imgW - cropW) * 0.5f);
                float maxShiftY = Math.max(0.0f, (imgH - cropH) * 0.5f);
                float uCenter = (imgW * 0.5f) - (offsetX * maxShiftX);
                float vCenter = (imgH * 0.5f) - (offsetY * maxShiftY);
                float u = Math.max(0.0f, Math.min(imgW - cropW, uCenter - cropW * 0.5f));
                float v = Math.max(0.0f, Math.min(imgH - cropH, vCenter - cropH * 0.5f));

                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        drawX, drawY, u, v, drawW, drawH,
                        Math.max(1, Math.round(cropW)), Math.max(1, Math.round(cropH)),
                        imgW, imgH, color);
            } else {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        drawX, drawY, 0.0f, 0.0f, drawW, drawH,
                        imgW, imgH, imgW, imgH, color);
            }
        } else if (fitMode == InventoryGuiSettings.FitMode.STRETCH) {
            if (scale != 1.0f || offsetX != 0.0f || offsetY != 0.0f) {
                float cropW = imgW / scale;
                float cropH = imgH / scale;
                float maxShiftX = Math.max(0.0f, (imgW - cropW) * 0.5f);
                float maxShiftY = Math.max(0.0f, (imgH - cropH) * 0.5f);
                float uCenter = (imgW * 0.5f) - (offsetX * maxShiftX);
                float vCenter = (imgH * 0.5f) - (offsetY * maxShiftY);
                float u = Math.max(0.0f, Math.min(imgW - cropW, uCenter - cropW * 0.5f));
                float v = Math.max(0.0f, Math.min(imgH - cropH, vCenter - cropH * 0.5f));

                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        targetX, targetY, u, v, targetW, targetH,
                        Math.max(1, Math.round(cropW)), Math.max(1, Math.round(cropH)),
                        imgW, imgH, color);
            } else {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        targetX, targetY, 0.0f, 0.0f, targetW, targetH,
                        imgW, imgH, imgW, imgH, color);
            }
        } else {
            // Default: CROP (Fill entire target box while maintaining original aspect ratio)
            float aspectImg = (float) imgW / (float) imgH;
            float aspectTarget = (float) targetW / (float) targetH;

            float baseCropW, baseCropH;
            if (aspectImg >= aspectTarget) {
                // Image is wider than target box: match height, crop width
                baseCropH = imgH;
                baseCropW = imgH * aspectTarget;
            } else {
                // Image is taller than target box: match width, crop height
                baseCropW = imgW;
                baseCropH = imgW / aspectTarget;
            }

            float cropW = baseCropW / scale;
            float cropH = baseCropH / scale;

            float maxShiftX = Math.max(0.0f, (imgW - cropW) * 0.5f);
            float maxShiftY = Math.max(0.0f, (imgH - cropH) * 0.5f);
            float uCenter = (imgW * 0.5f) - (offsetX * (maxShiftX > 0 ? maxShiftX : baseCropW * 0.5f));
            float vCenter = (imgH * 0.5f) - (offsetY * (maxShiftY > 0 ? maxShiftY : baseCropH * 0.5f));

            float u = Math.max(0.0f, Math.min(Math.max(0.0f, imgW - cropW), uCenter - cropW * 0.5f));
            float v = Math.max(0.0f, Math.min(Math.max(0.0f, imgH - cropH), vCenter - cropH * 0.5f));

            int regionW = Math.max(1, Math.round(Math.min(imgW - u, cropW)));
            int regionH = Math.max(1, Math.round(Math.min(imgH - v, cropH)));

            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    targetX, targetY, u, v, targetW, targetH,
                    regionW, regionH, imgW, imgH, color);
        }
    }
}
