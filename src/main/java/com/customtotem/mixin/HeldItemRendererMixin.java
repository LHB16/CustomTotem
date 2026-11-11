package com.customtotem.mixin;

import com.customtotem.manager.TotemManager;
import com.customtotem.render.HandOutlineManager;
import com.customtotem.render.HandOutlinePass;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/RenderDispatcher;render()V"
            )
    )
    private void customtotem$beforeRenderHands(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (HandOutlineManager.shouldCapture(player)) {
            HandOutlinePass.begin();
        }
    }

    @Inject(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("RETURN")
    )
    private void customtotem$afterRenderHands(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (HandOutlinePass.isCapturing()) {
            try {
                HandOutlinePass.endAndCompose(TotemManager.getInstance().getOutlineSettings());
            } catch (Throwable t) {
                HandOutlinePass.end();
            }
        }
    }
}
