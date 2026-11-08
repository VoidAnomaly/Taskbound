package dev.taskbound.mixin;

import dev.taskbound.event.FarmerGameplayEvents;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeehiveBlock.class)
abstract class FarmerHiveMixin {
    @Unique private static final ThreadLocal<Deque<String>> taskbound$tools = ThreadLocal.withInitial(ArrayDeque::new);
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void taskbound$before(ItemStack stack, BlockState before, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        taskbound$tools.get().push(stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHEARS_HARVEST) ? "honeycomb" : stack.is(Items.GLASS_BOTTLE) ? "honey" : "");
    }
    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BeehiveBlock;resetHoneyLevel(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", shift = At.Shift.AFTER))
    private void taskbound$honey(ItemStack stack, BlockState before, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        if (player instanceof ServerPlayer serverPlayer && before.getValue(BeehiveBlock.HONEY_LEVEL) == 5
                && CampfireBlock.isSmokeyPos(level, pos)) {
            String action = taskbound$tools.get().peek();
            if (action != null && !action.isEmpty()) FarmerGameplayEvents.hive(serverPlayer, action, pos);
        }
    }
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void taskbound$after(ItemStack stack, BlockState before, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        taskbound$tools.get().pop();
        if (taskbound$tools.get().isEmpty()) taskbound$tools.remove();
    }
}
