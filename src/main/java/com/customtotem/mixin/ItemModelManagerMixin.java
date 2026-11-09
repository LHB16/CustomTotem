package com.customtotem.mixin;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.TotemEntry;
import com.customtotem.render.TotemSpecialRenderer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {

    private static final Vector3fc[] BOUNDS_VERTICES = new Vector3fc[]{
            new Vector3f(0.0f, 0.0f, 0.0f),
            new Vector3f(1.0f, 1.0f, 1.0f)
    };

    @Inject(method = "clearAndUpdate", at = @At("HEAD"), cancellable = true)
    private void customtotem$onClearAndUpdate(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext,
                                             World world, HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
        if (stack != null && stack.isOf(Items.TOTEM_OF_UNDYING) && TotemManager.getInstance().hasCustomTotem()) {
            TotemEntry entry = TotemManager.getInstance().isRenderingFloatingItem()
                    ? TotemManager.getInstance().getPoppingTotem()
                    : TotemManager.getInstance().getCurrentActiveTotem();

            if (entry != null && entry.getTextureId() != null) {
                renderState.clear();
                ((ItemRenderStateAccessor) renderState).setDisplayContext(displayContext);
                ItemRenderState.LayerRenderState layer = renderState.newLayer();
                layer.setSpecialModel(TotemSpecialRenderer.INSTANCE, entry);
                layer.setTransform(TotemManager.getInstance().getDisplaySettings().getTransformation(displayContext));
                layer.setVertices(() -> BOUNDS_VERTICES);
                layer.setUseLight(true);
                ci.cancel();
            }
        }
    }
}
