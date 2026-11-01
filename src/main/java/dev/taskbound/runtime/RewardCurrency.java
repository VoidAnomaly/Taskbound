package dev.taskbound.runtime;

import dev.taskbound.TaskboundMod;
import dev.taskbound.compat.NumismaticsCompat;
import dev.taskbound.config.TaskboundServerConfig;
import dev.taskbound.core.CurrencyRewards;
import dev.taskbound.core.GeneratedTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Shared server-configured currency selection for new payouts and the client heading. */
public final class RewardCurrency {
    private static final AtomicReference<String> INVALID_CUSTOM_WARNING = new AtomicReference<>();
    private static final AtomicBoolean MISSING_SPUR_WARNING = new AtomicBoolean();

    private RewardCurrency() { }

    public static String itemId() { return resolve().itemId(); }

    public static GeneratedTask convertRewards(GeneratedTask task) {
        Selection currency = resolve();
        return CurrencyRewards.convert(task, currency.itemId(), currency.itemsPerEmerald());
    }

    private static Selection resolve() {
        if (TaskboundServerConfig.USE_CUSTOM_CURRENCY_REWARDS.getAsBoolean()) {
            String configured = TaskboundServerConfig.CUSTOM_CURRENCY_ITEM.get();
            ResourceLocation id = ResourceLocation.tryParse(configured);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR) {
                INVALID_CUSTOM_WARNING.set(null);
                return new Selection(id.toString(), 1);
            }
            if (!configured.equals(INVALID_CUSTOM_WARNING.getAndSet(configured))) {
                TaskboundMod.LOGGER.warn("Custom currency item '{}' is unavailable; Taskbound will use automatic emerald/spur rewards", configured);
            }
        } else INVALID_CUSTOM_WARNING.set(null);
        if (TaskboundServerConfig.useNumismaticsRewards()) {
            if (NumismaticsCompat.rewardsAvailable()) {
                return new Selection(NumismaticsCompat.SPUR, TaskboundServerConfig.NUMISMATICS_SPURS_PER_EMERALD.get());
            }
            if (ModList.get().isLoaded(NumismaticsCompat.MOD_ID) && MISSING_SPUR_WARNING.compareAndSet(false, true)) {
                TaskboundMod.LOGGER.warn("Create: Numismatics is loaded but its spur item is unavailable; Taskbound will retain emerald rewards");
            }
        }
        return new Selection("minecraft:emerald", 1);
    }

    private record Selection(String itemId, int itemsPerEmerald) { }
}
