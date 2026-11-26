package io.github.fernsehheft.skysong.mixin;

import io.github.fernsehheft.skysong.showcase.Showcase;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the camera to the showcase while it runs, after Minecraft has set it up as usual. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void skysong$showcase(BlockGetter level, Entity entity, boolean thirdPerson, boolean mirrored,
                                  float partialTick, CallbackInfo ci) {
        Showcase.CameraPose pose = Showcase.get().pose();
        if (pose != null) {
            // Detached, so the player is drawn like in third person and can be seen in the shot.
            this.detached = true;
            setRotation(pose.yaw(), pose.pitch());
            setPosition(pose.position());
        }
    }
}
