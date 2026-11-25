package io.github.fernsehheft.skysong.mixin;

import io.github.fernsehheft.skysong.render.BeatZoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Narrows the field of view a little for Skysong's beat zoom. */
@Mixin(GameRenderer.class)
abstract class BeatZoomMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void skysong$beatZoom(Camera camera, float partialTicks, boolean useFovSetting,
                                  CallbackInfoReturnable<Double> cir) {
        double multiplier = BeatZoom.fovMultiplier();
        if (multiplier != 1) {
            cir.setReturnValue(cir.getReturnValue() * multiplier);
        }
    }
}
