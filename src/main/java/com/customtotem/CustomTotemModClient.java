package com.customtotem;

import com.customtotem.gui.TotemManagerScreen;
import com.customtotem.manager.TotemManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CustomTotemModClient implements ClientModInitializer {
    public static final String MOD_ID = "customtotem";
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // Defer manager and texture loading to CLIENT_STARTED after RenderSystem & graphics device are initialized
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            TotemManager.getInstance().init();
        });

        // Register KeyBinding (Default: Key K)
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.customtotem.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.MISC
        ));

        // Client Tick Event for animation frame progression and key press detection
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TotemManager.getInstance().tick();

            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new TotemManagerScreen(null));
                }
            }
        });
    }
}
