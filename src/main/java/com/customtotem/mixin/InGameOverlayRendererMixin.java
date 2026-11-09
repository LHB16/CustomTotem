package com.customtotem.mixin;

import com.customtotem.manager.TotemManager;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    @Inject(method = "setFloatingItem", at = @At("HEAD"))
    private void customtotem$onSetFloatingItem(ItemStack stack, Random random, CallbackInfo ci) {
        if (stack != null && stack.isOf(Items.TOTEM_OF_UNDYING)) {
            TotemManager.getInstance().onTotemPop();
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"))
    private void customtotem$beforeRenderFloatingItem(MatrixStack matrices, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        TotemManager.getInstance().setRenderingFloatingItem(true);
    }

    @Inject(method = "renderFloatingItem", at = @At("RETURN"))
    private void customtotem$afterRenderFloatingItem(MatrixStack matrices, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        TotemManager.getInstance().setRenderingFloatingItem(false);
    }
}
