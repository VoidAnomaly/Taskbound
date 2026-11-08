package dev.taskbound.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Optional hooks are applied only to their inspected method shape, without loading external classes early. */
public final class OptionalIntegrationMixins implements IMixinConfigPlugin {
    private static final Map<String, List<String>> METHODS = Map.of(
            "StarcatcherCatchTransactionMixin", List.of("spawnFishFromPlayerFishing(Lnet/minecraft/server/level/ServerPlayer;ZIZZI)V"),
            "StarcatcherCatchMetadataMixin", List.of("awardFishCaughtCounter(Lcom/wdiscute/starcatcher/fish/FishProperties;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/entity/player/Player;IFZZZZ)V"),
            "FarmerDelightPotMixin", List.of(
                    "processCooking(Lnet/minecraft/world/item/crafting/RecipeHolder;Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;)Z",
                    "moveMealToOutput()V", "useStoredContainersOnMeal()V",
                    "getMeal()Lnet/minecraft/world/item/ItemStack;",
                    "getInventory()Lnet/neoforged/neoforge/items/ItemStackHandler;"),
            "FarmerDelightResultMixin", List.of("checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V"),
            "FarmerDelightCuttingMixin", List.of(
                    "processStoredItemUsingTool(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Z",
                    "getStoredItem()Lnet/minecraft/world/item/ItemStack;"),
            "FarmerDelightTomatoMixin", List.of("useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"),
            "FarmerDelightServeMixin", List.of("useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;"));
    private static final Map<String, List<String>> FIELDS = Map.of(
            "FarmerDelightResultMixin", List.of("player:Lnet/minecraft/world/entity/player/Player;", "removeCount:I",
                    "cookingPot:Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;"));

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        List<String> shape = METHODS.get(name);
        if (shape == null) return true;
        try {
            ClassNode target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            boolean supported = shape.stream().allMatch(signature -> target.methods.stream()
                    .anyMatch(method -> (method.name + method.desc).equals(signature)))
                    && FIELDS.getOrDefault(name, List.of()).stream().allMatch(signature -> target.fields.stream()
                    .anyMatch(field -> (field.name + ":" + field.desc).equals(signature)));
            if (!supported) LoggerFactory.getLogger("taskbound").warn(
                    "Skipping optional integration {}: {} has an unsupported method signature", mixinClassName, targetClassName);
            return supported;
        } catch (ClassNotFoundException missing) {
            return false;
        } catch (Exception failure) {
            LoggerFactory.getLogger("taskbound").warn("Cannot inspect optional integration {}", targetClassName, failure);
            return false;
        }
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
