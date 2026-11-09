package com.customtotem.mixin;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.InventoryGuiSettings;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends RecipeBookScreen<PlayerScreenHandler> {

    public InventoryScreenMixin(PlayerScreenHandler handler, RecipeBookWidget<?> recipeBook, PlayerInventory inventory, Text title) {
        super(handler, recipeBook, inventory, title);
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void customtotem$onDrawBackground(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        InventoryGuiSettings settings = TotemManager.getInstance().getInventoryGuiSettings();
        if (settings != null && settings.isEnabled()) {
            Identifier customTexture = TotemManager.getInstance().getInventoryGuiTexture();
            if (customTexture != null) {
                int alpha = Math.max(25, Math.min(255, (int) (settings.getOpacity() * 255)));
                int color = (alpha << 24) | 0x00FFFFFF;

                if (settings.getMode() == InventoryGuiSettings.Mode.FULLSCREEN) {
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, customTexture,
                            0, 0, 0.0f, 0.0f, this.width, this.height, this.width, this.height, color);
                } else {
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, customTexture,
                            this.x, this.y, 0.0f, 0.0f, this.backgroundWidth, this.backgroundHeight,
                            this.backgroundWidth, this.backgroundHeight, color);
                }

                if (settings.isShowVanillaSlots()) {
                    // Draw vanilla frame/slots with subtle transparency so slots remain clearly legible
                    int slotAlpha = 150;
                    int slotColor = (slotAlpha << 24) | 0x00FFFFFF;
                    context.drawTexture(RenderPipelines.GUI_TEXTURED,
                            Identifier.ofVanilla("textures/gui/container/inventory.png"),
                            this.x, this.y, 0.0f, 0.0f, this.backgroundWidth, this.backgroundHeight,
                            256, 256, slotColor);
                }

                // Render the player model preview
                if (this.client != null && this.client.player != null) {
                    InventoryScreen.drawEntity(context, this.x + 26, this.y + 8, this.x + 75, this.y + 78,
                            30, 0.0625f, (float) mouseX, (float) mouseY, this.client.player);
                }

                ci.cancel();
            }
        }
    }
}
