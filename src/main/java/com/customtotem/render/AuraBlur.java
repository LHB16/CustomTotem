package com.customtotem.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import net.minecraft.client.gl.SamplerCache;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;
import java.util.function.Supplier;

public final class AuraBlur {
    private static final int CONFIG_SIZE = 16;
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("customtotem", "pipeline/aura_spread"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.of("customtotem", "core/aura_spread"))
            .withSampler("InSampler")
            .withUniform("AuraSpread", UniformType.UNIFORM_BUFFER)
            .withoutBlend()
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
            .build();

    private final String name;
    private final Supplier<String> passLabel;
    private SimpleFramebuffer across;
    private SimpleFramebuffer down;
    private GpuBuffer acrossConfig;
    private GpuBuffer downConfig;
    private GpuBufferSlice acrossSlice;
    private GpuBufferSlice downSlice;
    private ByteBuffer configData;

    public AuraBlur(String name) {
        this.name = name;
        this.passLabel = () -> name + "_pass";
    }

    public static boolean compiled(GpuDevice device) {
        return device.precompilePipeline(PIPELINE).isValid();
    }

    public GpuTextureView spread(CommandEncoder encoder, GpuTextureView inView, int width, int height, float spread) {
        int halfW = Math.max(1, (width + 1) / 2);
        int halfH = Math.max(1, (height + 1) / 2);

        if (this.across == null) {
            allocate(halfW, halfH);
        } else if (this.across.textureWidth != halfW || this.across.textureHeight != halfH) {
            this.across.resize(halfW, halfH);
            this.down.resize(halfW, halfH);
        }

        float blurStep = Math.max(0.75f, spread / 6.0f);
        run(encoder, inView, this.across, this.acrossSlice, blurStep / (float) width, 0.0f);
        run(encoder, this.across.getColorAttachmentView(), this.down, this.downSlice, 0.0f, blurStep / (float) height);
        return this.down.getColorAttachmentView();
    }

    private void run(CommandEncoder encoder, GpuTextureView inView, SimpleFramebuffer target, GpuBufferSlice slice, float stepX, float stepY) {
        this.configData.putFloat(0, stepX);
        this.configData.putFloat(4, stepY);
        encoder.writeToBuffer(slice, this.configData);

        try (RenderPass pass = encoder.createRenderPass(this.passLabel, target.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setUniform("AuraSpread", slice);
            SamplerCache cache = RenderSystem.getSamplerCache();
            pass.bindTexture("InSampler", inView, cache.get(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    private void allocate(int width, int height) {
        GpuDevice device = RenderSystem.getDevice();
        this.across = new SimpleFramebuffer(name + "_across", width, height, false);
        this.down = new SimpleFramebuffer(name + "_down", width, height, false);

        this.acrossConfig = device.createBuffer(() -> name + "_across_config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_SIZE);
        this.downConfig = device.createBuffer(() -> name + "_down_config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_SIZE);
        this.acrossSlice = this.acrossConfig.slice(0, CONFIG_SIZE);
        this.downSlice = this.downConfig.slice(0, CONFIG_SIZE);
        this.configData = ByteBuffer.allocateDirect(CONFIG_SIZE).order(ByteOrder.nativeOrder());
    }

    public void close() {
        if (this.across != null) {
            this.across.delete();
            this.across = null;
        }
        if (this.down != null) {
            this.down.delete();
            this.down = null;
        }
        if (this.acrossConfig != null) {
            this.acrossConfig.close();
            this.acrossConfig = null;
        }
        if (this.downConfig != null) {
            this.downConfig.close();
            this.downConfig = null;
        }
    }
}
