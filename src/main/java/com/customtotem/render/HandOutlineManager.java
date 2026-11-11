package com.customtotem.render;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.HandOutlineSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class HandOutlineManager {
    private static final MethodHandle IRIS_SHADER_PACK_IN_USE = probeIris();

    private HandOutlineManager() {
    }

    private static MethodHandle probeIris() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isIrisShaderPackActive() {
        if (IRIS_SHADER_PACK_IN_USE == null) {
            return false;
        }
        try {
            return (boolean) IRIS_SHADER_PACK_IN_USE.invokeExact();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldCapture(ClientPlayerEntity player) {
        HandOutlineSettings settings = TotemManager.getInstance().getOutlineSettings();
        if (settings == null || !settings.isEnabled()) {
            return false;
        }
        if (isIrisShaderPackActive()) {
            return false;
        }
        if (settings.isTotemOnly() && player != null) {
            ItemStack main = player.getMainHandStack();
            ItemStack off = player.getOffHandStack();
            boolean isTotem = (main != null && main.isOf(Items.TOTEM_OF_UNDYING))
                    || (off != null && off.isOf(Items.TOTEM_OF_UNDYING));
            if (!isTotem) {
                return false;
            }
        }
        return true;
    }
}
