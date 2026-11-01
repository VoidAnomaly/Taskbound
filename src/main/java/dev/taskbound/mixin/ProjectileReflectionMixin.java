package dev.taskbound.mixin;

import dev.taskbound.event.ProjectileReflectionTracking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
abstract class ProjectileReflectionMixin {
    @Unique private Entity taskbound$originalOwner;
    @Inject(method = "deflect", at = @At("HEAD"))
    private void taskbound$captureOwner(ProjectileDeflection deflection, Entity entity, Entity owner, boolean player, CallbackInfoReturnable<Boolean> callback) {
        taskbound$originalOwner = ((Projectile)(Object)this).getOwner();
    }
    @Inject(method = "deflect", at = @At("RETURN"))
    private void taskbound$reflected(ProjectileDeflection deflection, Entity entity, Entity owner, boolean player, CallbackInfoReturnable<Boolean> callback) {
        ProjectileReflectionTracking.reflected((Projectile)(Object)this, taskbound$originalOwner, owner, callback.getReturnValueZ());
        taskbound$originalOwner = null;
    }
}
