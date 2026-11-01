package dev.taskbound.mixin;

import dev.taskbound.event.ProductionEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingRodItem.class)
abstract class ProductionFishingRodMixin {
    @Redirect(method = "use", at = @At(value = "NEW", target = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)Lnet/minecraft/world/entity/projectile/FishingHook;"))
    private FishingHook taskbound$cast(Player caster, Level level, int luck, int lure, Level useLevel, Player player, InteractionHand hand) {
        FishingHook hook = new FishingHook(caster, level, luck, lure);
        ProductionEvents.cast(hook, player.getItemInHand(hand));
        return hook;
    }
}
