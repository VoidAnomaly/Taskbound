package dev.taskbound.mixin;

import dev.taskbound.event.DragonEncounterTracking;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndDragonFight.class)
abstract class EndDragonFightEvidenceMixin {
    @Inject(method = "onCrystalDestroyed", at = @At("HEAD"))
    private void taskbound$crystal(EndCrystal crystal, DamageSource source, CallbackInfo callback) {
        DragonEncounterTracking.crystal((EndDragonFight)(Object)this, crystal, source);
    }
    @Inject(method = "setDragonKilled", at = @At("TAIL"))
    private void taskbound$defeated(EnderDragon dragon, CallbackInfo callback) {
        DragonEncounterTracking.defeated((EndDragonFight)(Object)this, dragon);
    }
}
