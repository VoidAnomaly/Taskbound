package dev.taskbound.event;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.TaskStatus;
import dev.taskbound.core.TaskTimer;
import dev.taskbound.persistence.FoodProductionClock;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Receives completed server transactions from native/optional hooks. */
public final class FarmerGameplayEvents {
    public static final String ACCEPTED_PRODUCTION = "farmer_production_boundary";
    public static final String BREEDER = "taskbound_farm_breeder";
    private static final Map<String, String> CROPS = Map.ofEntries(
            Map.entry("minecraft:wheat", "minecraft:wheat"), Map.entry("minecraft:carrots", "minecraft:carrot"),
            Map.entry("minecraft:potatoes", "minecraft:potato"), Map.entry("minecraft:beetroots", "minecraft:beetroot"),
            Map.entry("minecraft:cocoa", "minecraft:cocoa_beans"), Map.entry("farmersdelight:cabbages", "farmersdelight:cabbage"),
            Map.entry("farmersdelight:tomatoes", "farmersdelight:tomato"), Map.entry("farmersdelight:tomatoes_on_rope", "farmersdelight:tomato"),
            Map.entry("farmersdelight:onions", "farmersdelight:onion"),
            Map.entry("farmersdelight:rice_panicles", "farmersdelight:rice"));
    private static final Set<String> VARIETY_CROPS = Set.of("minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot",
            "farmersdelight:cabbage", "farmersdelight:tomato", "farmersdelight:onion", "farmersdelight:rice");

    public static void accepted(ServerPlayer player, ActiveTask active) {
        if (active.task().objectives().stream().anyMatch(o -> "farm_action".equals(o.type()) && "cook".equals(o.parameters().get("action")))) {
            active.progress().putAdditionalData(ACCEPTED_PRODUCTION, FoodProductionClock.next(player.server));
            TaskboundRuntime.get().markChanged(player);
        }
    }

    public static boolean eligible(ServerPlayer player, Map<String, Object> p) {
        String item = text(p, "item");
        if (item != null && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item))) return false;
        String entity = text(p, "entity");
        if (entity != null && !BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(entity))) return false;
        if ("cooking_pot".equals(p.get("station")) && !dev.taskbound.compat.FarmersDelightCompat.available()) return false;
        if ("cut".equals(p.get("action"))) {
            if (!dev.taskbound.compat.FarmersDelightCompat.available()) return false;
            ItemStack ingredient = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
            return player.serverLevel().getRecipeManager().getRecipes().stream().anyMatch(holder ->
                    BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString().equals("farmersdelight:cutting")
                            && holder.value().getIngredients().stream().anyMatch(input -> input.test(ingredient)));
        }
        if (Set.of("craft", "cook").contains(p.get("action"))) {
            return player.serverLevel().getRecipeManager().getRecipes().stream().anyMatch(holder -> {
                String recipeType = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString();
                boolean typeMatches = "craft".equals(p.get("action")) ? holder.value().getType() == RecipeType.CRAFTING
                        : "cooking_pot".equals(p.get("station")) ? recipeType.equals("farmersdelight:cooking")
                        : holder.value().getType() == RecipeType.SMELTING || holder.value().getType() == RecipeType.SMOKING;
                return typeMatches && id(holder.value().getResultItem(player.registryAccess())).equals(item);
            });
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void harvest(BlockDropsEvent event) {
        if (!event.isCanceled() && event.getBreaker() instanceof ServerPlayer player) harvested(player, event.getState(), event.getPos());
    }

    public static void harvested(ServerPlayer player, BlockState state, BlockPos pos) {
        String produce = CROPS.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        boolean mature = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                || state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) == 2;
        if (produce != null && mature) record(player, "harvest", produce, null, null, null, 1, 0, null, pos);
    }

    public static void milked(ServerPlayer player, Cow cow) {
        if (!cow.isBaby()) record(player, "milk", null, null, cow.getStringUUID(), null, 1, 0, null, cow.blockPosition());
    }

    public static void sheared(ServerPlayer player, Sheep sheep) {
        if (!sheep.isBaby() && sheep.isSheared()) record(player, "shear", null, null, sheep.getStringUUID(), sheep.getColor().getName(), 1, 0, null, sheep.blockPosition());
    }

    public static void markBirth(Animal parent, Animal mate, Entity baby) {
        if (baby == null) return;
        ServerPlayer player = parent.getLoveCause() != null ? parent.getLoveCause() : mate.getLoveCause();
        if (valid(player)) baby.getPersistentData().putUUID(BREEDER, player.getUUID());
    }

    public static void born(Entity baby) {
        if (!(baby instanceof Animal animal) || !animal.isBaby()) return;
        var data = baby.getPersistentData();
        if (!data.hasUUID(BREEDER) || !(baby.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var owner = data.getUUID(BREEDER);
        data.remove(BREEDER);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null && level.getPlayerByUUID(owner) instanceof ServerPlayer mock) player = mock;
        record(player, "breed", null, BuiltInRegistries.ENTITY_TYPE.getKey(baby.getType()).toString(), baby.getStringUUID(), null, 1, 0, null, baby.blockPosition());
    }

    public static void hive(ServerPlayer player, String action, BlockPos pos) {
        record(player, action, null, null, null, null, 1, 0, null, pos);
    }

    public static void cut(ServerPlayer player, ItemStack input, BlockPos pos) {
        if (!input.isEmpty()) record(player, "cut", id(input), null, null, null, 1, 0, null, pos);
    }

    public static void cooked(ServerPlayer player, String item, String station, int amount, long sequence, BlockPos pos) {
        record(player, "cook", item, null, null, null, amount, sequence, station, pos);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getInventory() instanceof CraftingContainer inputs)
                || !(player.containerMenu instanceof CraftingMenu || player.containerMenu instanceof InventoryMenu)) return;
        for (int i = 0; i < inputs.getContainerSize(); i++) {
            String input = id(inputs.getItem(i));
            if (input.startsWith("farmersdelight:") && input.endsWith("_pie_slice")) return;
        }
        ItemStack result = event.getCrafting();
        if (!result.isEmpty()) record(player, "craft", id(result), null, null, null, result.getCount(), 0, null, player.blockPosition());
    }

    private static void record(ServerPlayer player, String action, String item, String entity, String identity,
            String color, int amount, long production, String station, BlockPos pos) {
        if (!valid(player) || amount < 1) return;
        var runtime = TaskboundRuntime.get();
        for (ActiveTask active : runtime.activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE || TaskTimer.expired(active, player.serverLevel().getGameTime())) continue;
            for (GeneratedObjective objective : active.task().objectives()) {
                var p = objective.parameters();
                if (!"farm_action".equals(objective.type()) || !action.equals(p.get("action")) || !runtime.isObjectiveEligible(active, objective)
                        || p.containsKey("item") && !p.get("item").equals(item)
                        || p.containsKey("entity") && !p.get("entity").equals(entity)
                        || p.containsKey("station") && !p.get("station").equals(station)
                        || !VanillaObjectiveConditions.matches(objective, player, player.serverLevel(), pos, player.getMainHandItem(), false)) continue;
                if (action.equals("cook")) {
                    Object boundary = active.progress().additionalData().get(ACCEPTED_PRODUCTION);
                    if (!(boundary instanceof Number n) || production <= n.longValue()) continue;
                }
                int current = active.progress().progressFor(objective.key());
                int target = current + amount;
                String key = "farmer_" + objective.key();
                var state = new LinkedHashMap<String, Object>();
                if (active.progress().additionalData().get(key) instanceof Map<?, ?> old) old.forEach((k,v) -> state.put(k.toString(), v));
                if (action.equals("harvest") && p.containsKey("minimum_crop_types")) {
                    if (!VARIETY_CROPS.contains(item)) continue;
                    target = FarmerProgress.cropProgress(state, item, amount, number(p, "minimum_crop_types", 4), number(p, "per_crop_count", 24));
                } else if (action.equals("milk") || action.equals("shear") || action.equals("breed")) {
                    target = FarmerProgress.animalProgress(state, identity, color, !Boolean.FALSE.equals(p.get("distinct")), number(p,"required_count",1), number(p,"minimum_colors",0));
                }
                active.progress().putAdditionalData(key, state);
                runtime.markChanged(player);
                if (target > current) runtime.advanceTaskObjective(player, active.task().taskId(), o -> o.key().equals(objective.key()), target - current);
                // One transaction cannot also advance the next sequential stage.
                break;
            }
        }
    }

    public static boolean valid(ServerPlayer player) { return player != null && !player.isCreative() && !player.isSpectator() && !(player instanceof FakePlayer); }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static String text(Map<String, ?> p, String key) { return p.get(key) instanceof String s ? s : null; }
    private static int number(Map<String, ?> p, String key, int fallback) { return p.get(key) instanceof Number n ? n.intValue() : fallback; }
}
