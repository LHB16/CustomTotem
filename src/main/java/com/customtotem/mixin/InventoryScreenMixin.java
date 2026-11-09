package com.customtotem.mixin;

import com.customtotem.manager.TotemManager;
import com.customtotem.model.InventoryGuiSettings;
import com.customtotem.render.InventoryGuiRenderer;
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
                int imgW = TotemManager.getInstance().getInventoryGuiImageWidth();
                int imgH = TotemManager.getInstance().getInventoryGuiImageHeight();

                if (settings.getMode() == InventoryGuiSettings.Mode.FULLSCREEN) {
                    InventoryGuiRenderer.renderBackground(context, customTexture, imgW, imgH,
                            0, 0, this.width, this.height, settings);
                } else {
                    InventoryGuiRenderer.renderBackground(context, customTexture, imgW, imgH,
                            this.x, this.y, this.backgroundWidth, this.backgroundHeight, settings);
                }

                if (settings.isShowVanillaSlots()) {
                    // Draw vanilla frame/slots with user-configured opacity
                    int slotAlpha = Math.max(1, Math.min(255, Math.round(settings.getVanillaSlotsOpacity() * 255)));
                    int slotColor = (slotAlpha << 24) | 0x00FFFFFF;
                    context.drawTexture(RenderPipelines.GUI_TEXTURED,
                            Identifier.ofVanilla("textures/gui/container/inventory.png"),
                            this.x, this.y, 0.0f, 0.0f, this.backgroundWidth, this.backgroundHeight,
                            176, 166, 256, 256, slotColor);
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
