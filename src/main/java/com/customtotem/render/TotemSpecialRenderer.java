package com.customtotem.render;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class TotemSpecialRenderer implements SpecialModelRenderer<TotemEntry> {
    public static final TotemSpecialRenderer INSTANCE = new TotemSpecialRenderer();

    private TotemSpecialRenderer() {
    }

    @Override
    public void render(TotemEntry entry, ItemDisplayContext displayContext, MatrixStack matrices,
                       OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int seed) {
        if (entry == null) {
            entry = TotemManager.getInstance().getCurrentActiveTotem();
        }
        if (entry == null || entry.getTextureId() == null) {
            return;
        }

        long tick = TotemManager.getInstance().getClientTicks();
        float[] uvs = entry.getRenderUVs(tick);
        final float u0 = uvs[0];
        final float u1 = uvs[1];
        final float v0 = uvs[2]; // Top
        final float v1 = uvs[3]; // Bottom

        float[] bounds = entry.getQuadBounds();
        final float x0 = bounds[0];
        final float x1 = bounds[1];
        final float y0 = bounds[2];
        final float y1 = bounds[3];

        final float southZ = 0.501f;
        final float northZ = 0.499f;

        RenderLayer layer = RenderLayers.entityTranslucent(entry.getTextureId());

        queue.submitCustom(matrices, layer, (matrixEntry, consumer) -> {
            // South Face (Front, Normal: 0, 0, 1 at southZ)
            // Winding: Top-Left -> Bottom-Left -> Bottom-Right -> Top-Right
            vertex(consumer, matrixEntry, x0, y1, southZ, u0, v0, 0.0f, 0.0f, 1.0f, light, overlay);
            vertex(consumer, matrixEntry, x0, y0, southZ, u0, v1, 0.0f, 0.0f, 1.0f, light, overlay);
            vertex(consumer, matrixEntry, x1, y0, southZ, u1, v1, 0.0f, 0.0f, 1.0f, light, overlay);
            vertex(consumer, matrixEntry, x1, y1, southZ, u1, v0, 0.0f, 0.0f, 1.0f, light, overlay);

            // North Face (Back, Normal: 0, 0, -1 at northZ)
            // Winding viewed from behind: Top-Left (x1) -> Bottom-Left (x1) -> Bottom-Right (x0) -> Top-Right (x0)
            vertex(consumer, matrixEntry, x1, y1, northZ, u1, v0, 0.0f, 0.0f, -1.0f, light, overlay);
            vertex(consumer, matrixEntry, x1, y0, northZ, u1, v1, 0.0f, 0.0f, -1.0f, light, overlay);
            vertex(consumer, matrixEntry, x0, y0, northZ, u0, v1, 0.0f, 0.0f, -1.0f, light, overlay);
            vertex(consumer, matrixEntry, x0, y1, northZ, u0, v0, 0.0f, 0.0f, -1.0f, light, overlay);
        });

        if (glint) {
            RenderLayer glintLayer = RenderLayers.glintTranslucent();
            queue.submitCustom(matrices, glintLayer, (matrixEntry, consumer) -> {
                vertex(consumer, matrixEntry, x0, y1, southZ, u0, v0, 0.0f, 0.0f, 1.0f, light, overlay);
                vertex(consumer, matrixEntry, x0, y0, southZ, u0, v1, 0.0f, 0.0f, 1.0f, light, overlay);
                vertex(consumer, matrixEntry, x1, y0, southZ, u1, v1, 0.0f, 0.0f, 1.0f, light, overlay);
                vertex(consumer, matrixEntry, x1, y1, southZ, u1, v0, 0.0f, 0.0f, 1.0f, light, overlay);

                vertex(consumer, matrixEntry, x1, y1, northZ, u1, v0, 0.0f, 0.0f, -1.0f, light, overlay);
                vertex(consumer, matrixEntry, x1, y0, northZ, u1, v1, 0.0f, 0.0f, -1.0f, light, overlay);
                vertex(consumer, matrixEntry, x0, y0, northZ, u0, v1, 0.0f, 0.0f, -1.0f, light, overlay);
                vertex(consumer, matrixEntry, x0, y1, northZ, u0, v0, 0.0f, 0.0f, -1.0f, light, overlay);
            });
        }
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int light, int overlay) {
        consumer.vertex(entry, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(entry, nx, ny, nz);
    }

    @Override
    public void collectVertices(Consumer<Vector3fc> consumer) {
        consumer.accept(new Vector3f(0.0f, 0.0f, 0.5f));
        consumer.accept(new Vector3f(1.0f, 0.0f, 0.5f));
        consumer.accept(new Vector3f(1.0f, 1.0f, 0.5f));
        consumer.accept(new Vector3f(0.0f, 1.0f, 0.5f));
    }

    @Override
    public TotemEntry getData(ItemStack stack) {
        if (TotemManager.getInstance().isRenderingFloatingItem()) {
            return TotemManager.getInstance().getPoppingTotem();
        }
        return TotemManager.getInstance().getCurrentActiveTotem();
    }
}
