package io.github.fernsehheft.skysong.mixin;

import io.github.fernsehheft.skysong.showcase.Showcase;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the player standing still while the showcase films them. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void skysong$standStill(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (Showcase.get().active()) {
            forwardImpulse = 0;
            leftImpulse = 0;
            up = false;
            down = false;
            left = false;
            right = false;
            jumping = false;
            shiftKeyDown = false;
        }
    }
}
