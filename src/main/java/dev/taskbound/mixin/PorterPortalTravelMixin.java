package dev.taskbound.mixin;

import dev.taskbound.porter.PorterTasks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Marks the real portal call site; a nearby/stale portal is not evidence of portal travel. */
@Mixin(Entity.class)
abstract class PorterPortalTravelMixin {
    @Redirect(method = "handlePortal", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;"))
    private Entity taskbound$portalTransition(Entity entity, DimensionTransition transition) {
        PorterTasks.enterPortal();
        try { return entity.changeDimension(transition); }
        finally { PorterTasks.leavePortal(); }
    }
}
