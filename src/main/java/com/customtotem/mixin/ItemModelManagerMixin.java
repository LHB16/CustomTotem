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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {

    @Inject(method = "clearAndUpdate", at = @At("HEAD"), cancellable = true)
    private void customtotem$onClearAndUpdate(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext,
                                             World world, HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
        if (stack != null && stack.isOf(Items.TOTEM_OF_UNDYING) && TotemManager.getInstance().hasCustomTotem()) {
            TotemEntry entry = TotemManager.getInstance().isRenderingFloatingItem()
                    ? TotemManager.getInstance().getPoppingTotem()
                    : TotemManager.getInstance().getCurrentActiveTotem();

            if (entry != null && entry.getTextureId() != null) {
                renderState.clear();
                ItemRenderState.LayerRenderState layer = renderState.newLayer();
                layer.setSpecialModel(TotemSpecialRenderer.INSTANCE, entry);
                layer.setTransform(TotemManager.getInstance().getDisplaySettings().getTransformation(displayContext));
                layer.setUseLight(true);
                ci.cancel();
            }
        }
    }
}
