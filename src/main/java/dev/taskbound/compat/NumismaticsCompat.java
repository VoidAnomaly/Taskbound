package dev.taskbound.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** Optional, registry-only compatibility with Create: Numismatics. */
public final class NumismaticsCompat {
    public static final String MOD_ID = "numismatics";
    public static final String SPUR = "numismatics:spur";
    private static final ResourceLocation SPUR_ID = ResourceLocation.parse(SPUR);

    private NumismaticsCompat() {
    }

    public static boolean rewardsAvailable() {
        return ModList.get().isLoaded(MOD_ID)
                && BuiltInRegistries.ITEM.containsKey(SPUR_ID);
    }
}
