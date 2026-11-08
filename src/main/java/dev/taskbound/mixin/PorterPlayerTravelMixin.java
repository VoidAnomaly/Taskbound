package dev.taskbound.mixin;

import dev.taskbound.porter.PorterTasks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla and modded callers of the actual player teleport APIs share the restriction. */
@Mixin(ServerPlayer.class)
abstract class PorterPlayerTravelMixin {
    @Unique private Vec3 taskbound$beforeTeleport;
    @Unique private ResourceKey<Level> taskbound$beforeTeleportLevel;
    @Unique private ResourceKey<Level> taskbound$beforeDimension;
    @Unique private Vec3 taskbound$beforeDimensionPosition;

    @Inject(method = {"teleportTo(DDD)V", "teleportRelative(DDD)V",
            "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V"}, at = @At("HEAD"))
    private void taskbound$rememberTeleport(CallbackInfo ci) {
        taskbound$rememberPosition();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"))
    private void taskbound$rememberRelativeTeleport(CallbackInfoReturnable<Boolean> cir) {
        taskbound$rememberPosition();
    }

    @Unique private void taskbound$rememberPosition() {
        var player = (ServerPlayer) (Object) this;
        taskbound$beforeTeleport = player.position();
        taskbound$beforeTeleportLevel = player.level().dimension();
    }

    @Inject(method = {"teleportTo(DDD)V", "teleportRelative(DDD)V",
            "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V"}, at = @At("RETURN"))
    private void taskbound$instantTeleport(CallbackInfo ci) {
        taskbound$checkTeleport();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("RETURN"))
    private void taskbound$relativeTeleport(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) taskbound$checkTeleport();
    }

    @Inject(method = "changeDimension", at = @At("HEAD"))
    private void taskbound$rememberDimension(CallbackInfoReturnable<Entity> cir) {
        var player = (ServerPlayer) (Object) this;
        taskbound$beforeDimension = player.level().dimension();
        taskbound$beforeDimensionPosition = player.position();
    }

    @Inject(method = "changeDimension", at = @At("RETURN"))
    private void taskbound$dimensionTravel(CallbackInfoReturnable<Entity> cir) {
        var player = (ServerPlayer) (Object) this;
        if (cir.getReturnValue() == null) return;
        if (!player.level().dimension().equals(taskbound$beforeDimension)) PorterTasks.dimensionTravel(player);
        else if (!player.position().equals(taskbound$beforeDimensionPosition)) PorterTasks.teleported(player);
    }

    @Unique private void taskbound$checkTeleport() {
        var player = (ServerPlayer) (Object) this;
        if (!player.position().equals(taskbound$beforeTeleport) || !player.level().dimension().equals(taskbound$beforeTeleportLevel)) PorterTasks.teleported(player);
    }
}
