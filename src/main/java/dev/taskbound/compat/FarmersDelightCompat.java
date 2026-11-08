package dev.taskbound.compat;

import dev.taskbound.TaskboundMod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional classes appear only as names; absent-mod servers do not link against Farmer's Delight. */
public final class FarmersDelightCompat {
    private static final AtomicBoolean LOGGED = new AtomicBoolean();
    private static final String POT = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity";
    private static final String RESULT = "vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot";
    private static Field potField;
    private static Method mealMethod;
    private static Boolean supported;
    private FarmersDelightCompat() { }

    public static boolean available() {
        if (supported != null) return supported;
        try {
            Class<?> pot = Class.forName(POT, false, FarmersDelightCompat.class.getClassLoader());
            mealMethod = pot.getMethod("getMeal"); pot.getMethod("getInventory");
            pot.getDeclaredMethod("processCooking", net.minecraft.world.item.crafting.RecipeHolder.class, pot);
            pot.getDeclaredMethod("moveMealToOutput"); pot.getDeclaredMethod("useStoredContainersOnMeal");
            Class<?> slot = Class.forName(RESULT, false, FarmersDelightCompat.class.getClassLoader());
            potField = slot.getField("cookingPot");
            slot.getDeclaredMethod("checkTakeAchievements", ItemStack.class);
            slot.getDeclaredField("player"); slot.getDeclaredField("removeCount");
            Class<?> cutting = Class.forName("vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity", false, FarmersDelightCompat.class.getClassLoader());
            cutting.getMethod("processStoredItemUsingTool", ItemStack.class, net.minecraft.world.entity.player.Player.class);
            cutting.getMethod("getStoredItem");
            Class<?> tomato = Class.forName("vectorwing.farmersdelight.common.block.TomatoBlock", false, FarmersDelightCompat.class.getClassLoader());
            tomato.getDeclaredMethod("useWithoutItem", net.minecraft.world.level.block.state.BlockState.class,
                    net.minecraft.world.level.Level.class, net.minecraft.core.BlockPos.class, net.minecraft.world.entity.player.Player.class,
                    net.minecraft.world.phys.BlockHitResult.class);
            Class<?> block = Class.forName("vectorwing.farmersdelight.common.block.CookingPotBlock", false, FarmersDelightCompat.class.getClassLoader());
            block.getDeclaredMethod("useItemOn", ItemStack.class, net.minecraft.world.level.block.state.BlockState.class,
                    net.minecraft.world.level.Level.class, net.minecraft.core.BlockPos.class, net.minecraft.world.entity.player.Player.class,
                    net.minecraft.world.InteractionHand.class, net.minecraft.world.phys.BlockHitResult.class);
            supported = true;
        } catch (ClassNotFoundException missing) { supported = false; }
        catch (ReflectiveOperationException | LinkageError mismatch) { failure(mismatch); supported = false; }
        return supported;
    }

    public static BlockEntity pot(Slot slot) {
        try {
            if (potField == null && !available()) return null;
            return (BlockEntity) potField.get(slot);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError mismatch) { failure(mismatch); return null; }
    }

    public static ItemStack meal(BlockEntity pot) {
        try {
            if (pot == null || !pot.getClass().getName().equals(POT) || mealMethod == null && !available()) return ItemStack.EMPTY;
            return (ItemStack) mealMethod.invoke(pot);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError mismatch) { failure(mismatch); return ItemStack.EMPTY; }
    }

    private static void failure(Throwable error) {
        if (LOGGED.compareAndSet(false, true)) TaskboundMod.LOGGER.warn("Farmer's Delight task integration is unavailable for this runtime", error);
    }
}
