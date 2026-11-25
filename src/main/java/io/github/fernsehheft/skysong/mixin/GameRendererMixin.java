package io.github.fernsehheft.skysong.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.fernsehheft.skysong.SkysongClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft skips the whole game HUD while it's hidden with F1, Fabric's HUD callback
 * included. People hide the HUD to record videos, which is exactly when the lyrics
 * should stay, so we draw ours at the point where the HUD would have been drawn.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0))
    private void skysong$drawHudWhileHidden(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (!renderLevel || minecraft.level == null || !minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        SkysongClient.hud().ifPresent(hud -> {
            // The world's depth is still in the buffer and would hide parts of the 2D drawing.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            hud.renderWhileHidden(graphics);
            graphics.flush();
        });
    }
}
