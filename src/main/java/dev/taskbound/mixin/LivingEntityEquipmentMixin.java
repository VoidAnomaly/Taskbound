package dev.taskbound.mixin;

import dev.taskbound.event.EncounterGameplayEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityEquipmentMixin {
    @Inject(method = "onEquipItem", at = @At("HEAD"))
    private void taskbound$equipment(EquipmentSlot slot, ItemStack previous, ItemStack replacement, CallbackInfo callback) {
        if ((Object)this instanceof ServerPlayer player) EncounterGameplayEvents.equipmentChanged(player, slot, replacement);
    }
}
