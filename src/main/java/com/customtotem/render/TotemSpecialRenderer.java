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

        final float minU = 0.0f;
        final float maxU = 1.0f;
        final float minV = entry.getMinV(TotemManager.getInstance().getClientTicks());
        final float maxV = entry.getMaxV(TotemManager.getInstance().getClientTicks());

        RenderLayer layer = RenderLayers.entityTranslucent(entry.getTextureId());

        queue.submitCustom(matrices, layer, (matrixEntry, consumer) -> {
            // South Face (Normal: 0, 0, 1 at z = 0.5f)
            vertex(consumer, matrixEntry, 0.0f, 0.0f, 0.5f, minU, maxV, 0.0f, 0.0f, 1.0f, light, overlay);
            vertex(consumer, matrixEntry, 1.0f, 0.0f, 0.5f, maxU, maxV, 0.0f, 0.0f, 1.0f, light, overlay);
            vertex(consumer, matrixEntry, 1.0f, 1.0f, 0.5f, maxU, minV, 0.0f, 0.0f, 1.0f, light, overlay);
            vertex(consumer, matrixEntry, 0.0f, 1.0f, 0.5f, minU, minV, 0.0f, 0.0f, 1.0f, light, overlay);

            // North Face (Normal: 0, 0, -1 at z = 0.5f)
            vertex(consumer, matrixEntry, 1.0f, 0.0f, 0.5f, minU, maxV, 0.0f, 0.0f, -1.0f, light, overlay);
            vertex(consumer, matrixEntry, 0.0f, 0.0f, 0.5f, maxU, maxV, 0.0f, 0.0f, -1.0f, light, overlay);
            vertex(consumer, matrixEntry, 0.0f, 1.0f, 0.5f, maxU, minV, 0.0f, 0.0f, -1.0f, light, overlay);
            vertex(consumer, matrixEntry, 1.0f, 1.0f, 0.5f, minU, minV, 0.0f, 0.0f, -1.0f, light, overlay);
        });

        if (glint) {
            RenderLayer glintLayer = RenderLayers.glintTranslucent();
            queue.submitCustom(matrices, glintLayer, (matrixEntry, consumer) -> {
                vertex(consumer, matrixEntry, 0.0f, 0.0f, 0.5f, minU, maxV, 0.0f, 0.0f, 1.0f, light, overlay);
                vertex(consumer, matrixEntry, 1.0f, 0.0f, 0.5f, maxU, maxV, 0.0f, 0.0f, 1.0f, light, overlay);
                vertex(consumer, matrixEntry, 1.0f, 1.0f, 0.5f, maxU, minV, 0.0f, 0.0f, 1.0f, light, overlay);
                vertex(consumer, matrixEntry, 0.0f, 1.0f, 0.5f, minU, minV, 0.0f, 0.0f, 1.0f, light, overlay);

                vertex(consumer, matrixEntry, 1.0f, 0.0f, 0.5f, minU, maxV, 0.0f, 0.0f, -1.0f, light, overlay);
                vertex(consumer, matrixEntry, 0.0f, 0.0f, 0.5f, maxU, maxV, 0.0f, 0.0f, -1.0f, light, overlay);
                vertex(consumer, matrixEntry, 0.0f, 1.0f, 0.5f, maxU, minV, 0.0f, 0.0f, -1.0f, light, overlay);
                vertex(consumer, matrixEntry, 1.0f, 1.0f, 0.5f, minU, minV, 0.0f, 0.0f, -1.0f, light, overlay);
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
