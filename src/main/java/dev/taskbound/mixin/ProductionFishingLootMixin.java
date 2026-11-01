package dev.taskbound.mixin;

import dev.taskbound.event.ProductionEvents;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NestedLootTable.class)
abstract class ProductionFishingLootMixin {
    @Redirect(method = "createItemStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V"))
    private void taskbound$source(LootTable table, LootContext context, Consumer<ItemStack> consumer) {
        ProductionEvents.fishingLoot(table, context, consumer);
    }
}
