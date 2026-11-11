package com.customtotem.render;

import com.customtotem.model.HandOutlineSettings;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SamplerCache;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;
import java.util.function.Supplier;

public final class HandOutlinePass {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomTotemOutline");
    private static final int CONFIG_SIZE = 64; // 4 x vec4 in std140

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("customtotem", "pipeline/hand_outline"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.of("customtotem", "core/hand_outline"))
            .withSampler("CaptureSampler")
            .withSampler("HaloSampler")
            .withUniform("HandOutlineConfig", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withColorWrite(true, false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
            .build();

    private static final Supplier<String> PASS_LABEL = () -> "customtotem_hand_outline";
    private static final Supplier<String> CONFIG_LABEL = () -> "customtotem_hand_outline_config";

    private static SimpleFramebuffer hand;
    private static AuraBlur blur;
    private static GpuBuffer config;
    private static GpuBufferSlice configSlice;
    private static ByteBuffer configData;
    private static boolean capturing = false;
    private static boolean shaderErrorLogged = false;

    private HandOutlinePass() {
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static boolean begin() {
        if (capturing) {
            return false;
        }
        if (RenderSystem.outputColorTextureOverride != null || RenderSystem.outputDepthTextureOverride != null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.gameRenderer == null) {
            return false;
        }

        Framebuffer mainBuffer = client.getFramebuffer();
        if (mainBuffer == null || mainBuffer.getColorAttachment() == null || mainBuffer.textureWidth <= 0 || mainBuffer.textureHeight <= 0) {
            return false;
        }

        GpuDevice device = RenderSystem.getDevice();
        if (!isPipelineReady(device)) {
            return false;
        }

        int width = mainBuffer.textureWidth;
        int height = mainBuffer.textureHeight;

        if (hand == null) {
            allocate(width, height);
        } else if (hand.textureWidth != width || hand.textureHeight != height) {
            hand.resize(width, height);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.clearColorAndDepthTextures(hand.getColorAttachment(), 0, hand.getDepthAttachment(), 1.0);

        RenderSystem.outputColorTextureOverride = hand.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = hand.getDepthAttachmentView();
        capturing = true;
        return true;
    }

    public static boolean end() {
        if (!capturing) {
            return false;
        }
        capturing = false;
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
        return true;
    }

    public static void endAndCompose(HandOutlineSettings settings) {
        if (!end()) {
            return;
        }
        if (hand == null || settings == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer mainBuffer = client.getFramebuffer();
        if (mainBuffer == null) {
            return;
        }

        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();

        // Aura Halo pass
        float auraIntensity = settings.getAuraIntensity();
        float reach = settings.getWidth();
        GpuTextureView haloView;
        if (auraIntensity > 0.001f) {
            if (blur == null) {
                blur = new AuraBlur("customtotem_aura");
            }
            haloView = blur.spread(encoder, hand.getColorAttachmentView(), hand.textureWidth, hand.textureHeight, reach * 2.2f);
        } else {
            haloView = hand.getColorAttachmentView();
        }

        // Fill Uniform Buffer (64 bytes)
        float speed = settings.getSpeed();
        float timeSec = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0) * speed;

        // 1. Tint (offset 0): rgb = static color, a = aura intensity
        int color = settings.getStaticColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        configData.putFloat(0, r);
        configData.putFloat(4, g);
        configData.putFloat(8, b);
        configData.putFloat(12, auraIntensity);

        // 2. Sheen (offset 16): rgb = sheen color (white-cyan), a = sheen strength
        configData.putFloat(16, 1.0f);
        configData.putFloat(20, 1.0f);
        configData.putFloat(24, 1.0f);
        configData.putFloat(28, settings.getSheen());

        // 3. Shape (offset 32): x = rimStrength, y = reach width, z = timePhase, w = 0
        configData.putFloat(32, settings.getOutlineStrength());
        configData.putFloat(36, reach);
        configData.putFloat(40, timeSec);
        configData.putFloat(44, 0.0f);

        // 4. ModeParams (offset 48): x = modeId, y = speed, z = sat, w = val
        configData.putFloat(48, (float) settings.getMode().getModeId());
        configData.putFloat(52, speed);
        configData.putFloat(56, 0.95f);
        configData.putFloat(60, 1.0f);

        encoder.writeToBuffer(configSlice, configData);

        try (RenderPass pass = encoder.createRenderPass(PASS_LABEL, mainBuffer.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setUniform("HandOutlineConfig", configSlice);

            SamplerCache samplerCache = RenderSystem.getSamplerCache();
            pass.bindTexture("CaptureSampler", hand.getColorAttachmentView(), samplerCache.get(FilterMode.LINEAR));
            pass.bindTexture("HaloSampler", haloView, samplerCache.get(FilterMode.LINEAR));

            pass.draw(0, 3);
        }
    }

    private static boolean isPipelineReady(GpuDevice device) {
        try {
            if (!device.precompilePipeline(PIPELINE).isValid()) {
                if (!shaderErrorLogged) {
                    LOGGER.warn("CustomTotem hand outline shader pipeline failed to compile or is invalid.");
                    shaderErrorLogged = true;
                }
                return false;
            }
            if (blur != null && !AuraBlur.compiled(device)) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            if (!shaderErrorLogged) {
                LOGGER.warn("Hand outline shader check error: {}", t.getMessage());
                shaderErrorLogged = true;
            }
            return false;
        }
    }

    private static void allocate(int width, int height) {
        GpuDevice device = RenderSystem.getDevice();
        hand = new SimpleFramebuffer("customtotem_hand_capture", width, height, true);
        config = device.createBuffer(CONFIG_LABEL, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_SIZE);
        configSlice = config.slice(0, CONFIG_SIZE);
        configData = ByteBuffer.allocateDirect(CONFIG_SIZE).order(ByteOrder.nativeOrder());
    }
}
