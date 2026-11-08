package dev.taskbound.event;

import dev.taskbound.TaskboundMod;
import dev.taskbound.api.TaskboundProviders;
import dev.taskbound.api.fishing.FishCatchData;
import dev.taskbound.api.fishing.FishingCatchContext;
import dev.taskbound.api.fishing.FishingTaskProvider;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Translates qualifying post-acceptance gameplay actions into persisted objective progress. */
public final class TaskboundGameplayEvents {
    private static final Map<UUID, PotionImpact> RECENT_POTION_IMPACTS = new HashMap<>();
    private static final Map<UUID, CureAttempt> CURE_ATTEMPTS = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TaskboundRuntime.get().resumeTimers(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            TaskboundRuntime.get().recordPlayerDeath(deadPlayer);
        }
        if (event.getEntity() instanceof ZombieVillager zombie
                && zombie.level() instanceof ServerLevel zombieLevel) {
            CURE_ATTEMPTS.remove(zombie.getUUID());
            TaskboundSavedData.get(zombieLevel.getServer()).takePendingCure(zombie.getUUID());
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = KillDamageConditions.player(event.getSource());
        if (player == null) return;
        Entity victim = event.getEntity();
        ItemStack weapon = KillDamageConditions.weapon(event.getSource());
        TaskboundRuntime.get().advanceMatching(player, objective ->
                qualifiesKill(objective, player, victim, level, weapon, event.getSource()), 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFishCaught(ItemFishedEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getHookEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack rod = fishingRod(player);
        FishingCatchContext context = new FishingCatchContext(
                level, event.getHookEntity().blockPosition(), rod, "minecraft:rod");
        for (ItemStack caught : event.getDrops()) {
            if (caught.isEmpty()) {
                continue;
            }
            // Exact hook loot is directly observable. Loot-table category and cast-time rod
            // provenance are deliberately not inferred from it.
            TaskboundRuntime.get().advanceMatching(player, objective ->
                    objective.type().equals("catch_item")
                            && VanillaObjectiveConditions.itemMatches(caught,
                                    TaskboundRuntime.stringParameter(objective, "fish_item"),
                                    TaskboundRuntime.stringParameter(objective, "fish_item_tag"))
                            && VanillaObjectiveConditions.matches(objective, player, level,
                                    event.getHookEntity().blockPosition(), ItemStack.EMPTY, true), caught.getCount());
            List<FishCatchData> recognizedCatches = TaskboundProviders.fishingProviders().stream()
                    .map(provider -> safeReadCatch(provider, player, caught, context))
                    .flatMap(Optional::stream)
                    .toList();
            if (!recognizedCatches.isEmpty()) {
                TaskboundRuntime.get().advanceMatching(
                        player,
                        objective -> recognizedCatches.stream().anyMatch(data ->
                                qualifiesCatch(objective, data, level, event.getHookEntity().blockPosition())),
                        Math.max(1, caught.getCount())
                );
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getInventory() instanceof CraftingContainer)
                || !(player.containerMenu instanceof CraftingMenu || player.containerMenu instanceof InventoryMenu)) {
            return;
        }
        ItemStack output = event.getCrafting();
        if (output.isEmpty()) {
            return;
        }
        // ResultSlot fires once per recipe take, with the original output stack for
        // shift-click as well. Its count is output units (four torches, not one craft).
        TaskboundRuntime.get().advanceMatching(player, objective ->
                objective.type().equals("craft_item")
                        && VanillaObjectiveConditions.itemMatches(output,
                                TaskboundRuntime.stringParameter(objective, "item"), null)
                        && VanillaObjectiveConditions.matches(objective, player, player.serverLevel(),
                                player.blockPosition(), player.getMainHandItem(), false), output.getCount());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockHarvested(BlockDropsEvent event) {
        if (event.isCanceled() || !(event.getBreaker() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        BlockState state = event.getState();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        TaskboundRuntime.get().advanceMatching(player, objective ->
                (objective.type().equals("gather_block")
                        || objective.type().equals("harvest_crop") && isMatureCrop(state))
                        && blockId.equals(TaskboundRuntime.stringParameter(objective, "block"))
                        && VanillaObjectiveConditions.matches(objective, player, event.getLevel(),
                                event.getPos(), event.getTool(), false), 1);
    }

    private static boolean isMatureCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                || state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) == 2;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemEnchanted(PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getEnchantments().isEmpty()) {
            return;
        }
        TaskboundRuntime.get().advanceMatching(player, objective -> {
            if (!matchesEnchantmentTransaction(objective, player, event.getEnchantedItem(), "table")) {
                return false;
            }
            String required = TaskboundRuntime.stringParameter(objective, "required_enchantment");
            int minimum = VanillaObjectiveConditions.number(objective.parameters(), "minimum_enchantment_level", 1);
            return required == null || event.getEnchantments().stream().anyMatch(instance ->
                    instance.level >= minimum && instance.enchantment.unwrapKey()
                            .map(key -> key.location().toString().equals(required)).orElse(false));
        }, 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAnvilUsed(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack output = ProductionEvents.anvilOutput(player, event.getOutput());
        TaskboundRuntime.get().advanceMatching(player, objective -> {
            if (!matchesEnchantmentTransaction(objective, player, output, "anvil")) {
                return false;
            }
            String required = TaskboundRuntime.stringParameter(objective, "required_enchantment");
            if (required != null) {
                return VanillaEventRules.enchantmentIncreased(
                        VanillaObjectiveConditions.enchantmentLevel(player.serverLevel(), event.getLeft(), required),
                        VanillaObjectiveConditions.enchantmentLevel(player.serverLevel(), output, required),
                        VanillaObjectiveConditions.number(objective.parameters(), "minimum_enchantment_level", 1));
            }
            return output.getEnchantments().entrySet().stream().anyMatch(entry ->
                    entry.getIntValue() > event.getLeft().getEnchantments().getLevel(entry.getKey()));
        }, 1);
    }

    private static boolean matchesEnchantmentTransaction(
            GeneratedObjective objective, ServerPlayer player, ItemStack output, String method) {
        return objective.type().equals("enchant_item")
                && matchesOptional(method, TaskboundRuntime.stringParameter(objective, "method"))
                && VanillaObjectiveConditions.itemMatches(output,
                        TaskboundRuntime.stringParameter(objective, "item"), null)
                && VanillaObjectiveConditions.matches(objective, player, player.serverLevel(),
                        player.blockPosition(), player.getMainHandItem(), false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onShieldBlocked(LivingShieldBlockEvent event) {
        if (event.isCanceled() || !event.getBlocked() || event.getBlockedDamage() <= 0
                || !(event.getEntity() instanceof ServerPlayer player) || !player.getUseItem().is(Items.SHIELD)) {
            return;
        }
        DamageSource source = event.getDamageSource();
        Entity attacker = source.getEntity();
        TaskboundRuntime.get().advanceMatching(player, objective -> {
            if (!objective.type().equals("challenge_action")) {
                return false;
            }
            String action = TaskboundRuntime.stringParameter(objective, "action");
            boolean qualifies = switch (action == null ? "" : action) {
                case "shield_block" -> true;
                case "shield_block_skeleton_arrows" -> attacker != null
                        && attacker.getType() == EntityType.SKELETON && source.getDirectEntity() instanceof AbstractArrow;
                case "shield_block_piglin" -> attacker != null && attacker.getType() == EntityType.PIGLIN
                        && source.getDirectEntity() == attacker && source.is(DamageTypes.MOB_ATTACK);
                default -> false;
            };
            return qualifies && VanillaObjectiveConditions.matches(objective, player, player.serverLevel(),
                    player.blockPosition(), player.getMainHandItem(), false);
        }, 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBowHit(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof Enemy)
                || !(source.getEntity() instanceof ServerPlayer player)
                || !(source.getDirectEntity() instanceof AbstractArrow)
                || !VanillaObjectiveConditions.itemMatches(source.getWeaponItem(), "minecraft:bow", null)) {
            return;
        }
        TaskboundRuntime.get().advanceMatching(player, objective ->
                objective.type().equals("challenge_action")
                        && "bow_hit_on_hostile".equals(TaskboundRuntime.stringParameter(objective, "action"))
                        && player.distanceTo(event.getEntity()) >= VanillaObjectiveConditions.decimal(
                                objective.parameters(), "minimum_distance", 0)
                        && VanillaObjectiveConditions.matches(objective, player, player.serverLevel(),
                                event.getEntity().blockPosition(), source.getWeaponItem(), false), 1);
    }

    @SubscribeEvent
    public void onPotionBrewed(PlayerBrewedPotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack potion = event.getStack();
        TaskboundRuntime.get().advanceMatching(player, objective ->
                objective.type().equals("perform_magic_action")
                        && "brew_potion".equals(TaskboundRuntime.stringParameter(objective, "action"))
                        && matchesItem(potion, TaskboundRuntime.stringParameter(objective, "potion_item"))
                        && matchesPotion(potion, TaskboundRuntime.stringParameter(objective, "potion")), 1);
    }

    @SubscribeEvent
    public void onItemUseFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack used = event.getItem();
        TaskboundRuntime.get().advanceMatching(player, objective ->
                objective.type().equals("perform_magic_action")
                        && "drink_potion".equals(TaskboundRuntime.stringParameter(objective, "action"))
                        && matchesItem(used, TaskboundRuntime.stringParameter(objective, "potion_item"))
                        && matchesPotion(used, TaskboundRuntime.stringParameter(objective, "potion")), 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPotionImpact(ProjectileImpactEvent event) {
        if (event.isCanceled()
                || !(event.getProjectile() instanceof ThrownPotion potion)
                || !(potion.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        PotionImpact impact = new PotionImpact(
                player.server,
                potion.getItem().copy(),
                potion.level().dimension().location(),
                event.getRayTraceResult().getLocation(),
                player.serverLevel().getGameTime()
        );
        RECENT_POTION_IMPACTS.put(player.getUUID(), impact);
    }

    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        ServerPlayer player = effectPlayer(event.getEffectSource());
        if (player == null) {
            return;
        }
        String effectId = event.getEffectInstance().getEffect().unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        String targetId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        PotionImpact impact = RECENT_POTION_IMPACTS.get(player.getUUID());
        ItemStack sourceItem = impact != null && impact.matches(event.getEntity())
                ? impact.item()
                : ItemStack.EMPTY;
        TaskboundRuntime.get().advanceMatching(player, objective ->
                objective.type().equals("perform_magic_action")
                        && "apply_effect".equals(TaskboundRuntime.stringParameter(objective, "action"))
                        && matchesOptional(effectId, TaskboundRuntime.stringParameter(objective, "effect"))
                        && matchesOptional(targetId, TaskboundRuntime.stringParameter(objective, "target_entity"))
                        && matchesItem(sourceItem, TaskboundRuntime.stringParameter(objective, "potion_item"))
                        && matchesPotion(sourceItem, TaskboundRuntime.stringParameter(objective, "potion")), 1);
    }

    @SubscribeEvent
    public void onPlayerDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0.0F) {
            TaskboundRuntime.get().recordPlayerDamage(player, event.getNewDamage());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TaskboundRuntime.get().tickPlayer(player);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        var attempts = CURE_ATTEMPTS.entrySet().iterator();
        while (attempts.hasNext()) {
            var entry = attempts.next();
            CureAttempt attempt = entry.getValue();
            if (attempt.server() != server) {
                continue;
            }
            Entity entity = null;
            for (ServerLevel level : server.getAllLevels()) {
                entity = level.getEntity(entry.getKey());
                if (entity != null) {
                    break;
                }
            }
            if (entity instanceof ZombieVillager zombie && zombie.isAlive() && zombie.isConverting()) {
                TaskboundSavedData.get(server).rememberPendingCure(
                        zombie.getUUID(), attempt.playerId(), attempt.taskId());
            }
            attempts.remove();
        }
        RECENT_POTION_IMPACTS.entrySet().removeIf(entry -> entry.getValue().server() == server);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTeleport(EntityTeleportEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            TaskboundRuntime.get().recordTeleport(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TaskboundRuntime.get().recordDimensionChange(player);
        }
    }

    @SubscribeEvent
    public void onZombieVillagerInteracted(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ZombieVillager zombie)
                || !zombie.hasEffect(MobEffects.WEAKNESS)) {
            return;
        }
        Optional<String> taskId = TaskboundRuntime.get().firstEligibleTaskId(player, objective ->
                objective.type().equals("perform_magic_action")
                        && "cure_entity".equals(TaskboundRuntime.stringParameter(objective, "action"))
                        && matchesItem(event.getItemStack(), TaskboundRuntime.stringParameter(objective, "required_item")));
        if (taskId.isEmpty()) {
            return;
        }
        CURE_ATTEMPTS.put(
                zombie.getUUID(),
                new CureAttempt(player.server, player.getUUID(), taskId.get())
        );
    }

    @SubscribeEvent
    public void onLivingConversion(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof ZombieVillager)
                || !(event.getOutcome() instanceof Villager)
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        TaskboundSavedData.PendingCure pending = TaskboundSavedData.get(level.getServer())
                .takePendingCure(event.getEntity().getUUID());
        if (pending != null) {
            TaskboundRuntime.get().advanceTaskObjective(
                    level.getServer(),
                    pending.playerId(),
                    pending.taskId(),
                    objective -> objective.type().equals("perform_magic_action")
                            && "cure_entity".equals(TaskboundRuntime.stringParameter(objective, "action")),
                    1
            );
        }
    }

    private static boolean qualifiesKill(
            GeneratedObjective objective,
            ServerPlayer player,
            Entity victim,
            ServerLevel level,
            ItemStack weapon,
            DamageSource source
    ) {
        if (!objective.type().equals("kill_entity") || !KillDamageConditions.matches(objective, source)) {
            return false;
        }
        if (Boolean.TRUE.equals(objective.parameters().get("hostile_only")) && !(victim instanceof Enemy)) {
            return false;
        }
        if ("melee".equals(TaskboundRuntime.stringParameter(objective, "method"))
                && (!source.is(DamageTypes.PLAYER_ATTACK) || source.getDirectEntity() != player)) {
            return false;
        }
        String entity = TaskboundRuntime.stringParameter(objective, "entity");
        if (entity != null && !entity.equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString())) {
            return false;
        }
        String entityTag = TaskboundRuntime.stringParameter(objective, "entity_tag");
        if (entityTag != null && !victim.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(entityTag)))) {
            return false;
        }
        if (!matchesBiome(objective, level, victim.blockPosition())) {
            return false;
        }
        String dimension = TaskboundRuntime.stringParameter(objective, "required_dimension");
        if (dimension != null && !dimension.equals(level.dimension().location().toString())) {
            return false;
        }
        String weaponId = TaskboundRuntime.stringParameter(objective, "required_weapon");
        if (weaponId != null && !matchesItem(weapon, weaponId)) {
            return false;
        }
        String weaponTag = TaskboundRuntime.stringParameter(objective, "required_weapon_tag");
        if (weaponTag != null) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(weaponTag));
            if (weapon == null || !weapon.is(tag)) {
                return false;
            }
        }
        return VanillaObjectiveConditions.matches(objective, player, level, victim.blockPosition(), weapon, false);
    }

    private static boolean qualifiesCatch(
            GeneratedObjective objective,
            FishCatchData data,
            ServerLevel level,
            net.minecraft.core.BlockPos hookPosition
    ) {
        if (!objective.type().equals("catch_fish")) {
            return false;
        }
        String species = TaskboundRuntime.stringParameter(objective, "species_id");
        String item = TaskboundRuntime.stringParameter(objective, "fish_item");
        if (!matchesOptional(data.speciesId().toString(), species)
                || item != null && !matchesItem(data.item(), item)
                || !matchesBiome(objective, level, hookPosition)
                || !matchesWeatherAndTime(objective, level, hookPosition)) {
            return false;
        }
        String providerId = TaskboundRuntime.stringParameter(objective, "compatibility_provider_id");
        if (providerId != null && !providerId.equals(data.providerId().toString())) {
            return false;
        }
        String dimension = TaskboundRuntime.stringParameter(objective, "required_dimension");
        if (dimension != null && !dimension.equals(data.dimension().toString())) {
            return false;
        }
        return matchesRange(data.weight(), objective, "minimum_weight", "maximum_weight")
                && matchesRange(data.length(), objective, "minimum_length", "maximum_length")
                && matchesOptional(data.rarity().map(ResourceLocation::toString).orElse(null),
                        TaskboundRuntime.stringParameter(objective, "exact_rarity"));
    }

    private static boolean matchesBiome(
            GeneratedObjective objective,
            ServerLevel level,
            net.minecraft.core.BlockPos position
    ) {
        String biome = TaskboundRuntime.stringParameter(objective, "required_biome");
        if (biome != null && !level.getBiome(position).unwrapKey()
                .map(key -> key.location().toString().equals(biome)).orElse(false)) {
            return false;
        }
        String biomeTag = TaskboundRuntime.stringParameter(objective, "required_biome_tag");
        return biomeTag == null || level.getBiome(position)
                .is(TagKey.create(Registries.BIOME, ResourceLocation.parse(biomeTag)));
    }

    private static boolean matchesWeatherAndTime(
            GeneratedObjective objective,
            ServerLevel level,
            net.minecraft.core.BlockPos position
    ) {
        return VanillaObjectiveConditions.matchesWeather(
                TaskboundRuntime.stringParameter(objective, "required_weather"), level, position, true)
                && VanillaEventRules.matchesTime(
                        TaskboundRuntime.stringParameter(objective, "required_time"), level.getDayTime());
    }

    private static boolean matchesRange(
            Optional<Double> actual,
            GeneratedObjective objective,
            String minimumKey,
            String maximumKey
    ) {
        Object minimum = objective.parameters().get(minimumKey);
        Object maximum = objective.parameters().get(maximumKey);
        if (minimum == null && maximum == null) {
            return true;
        }
        if (actual.isEmpty()) {
            return false;
        }
        return (!(minimum instanceof Number minimumNumber) || actual.get() >= minimumNumber.doubleValue())
                && (!(maximum instanceof Number maximumNumber) || actual.get() <= maximumNumber.doubleValue());
    }

    private static boolean matchesPotion(ItemStack stack, String requiredPotion) {
        if (requiredPotion == null) {
            return true;
        }
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.potion().flatMap(Holder::unwrapKey)
                .map(key -> key.location().toString().equals(requiredPotion))
                .orElse(false);
    }

    private static boolean matchesItem(ItemStack stack, String requiredItem) {
        return requiredItem == null
                || stack != null && !stack.isEmpty()
                && requiredItem.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static boolean matchesOptional(String actual, String required) {
        return required == null || required.equals(actual);
    }

    private static ServerPlayer effectPlayer(Entity source) {
        if (source instanceof ServerPlayer player) {
            return player;
        }
        return source instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player
                ? player
                : null;
    }

    private static ItemStack fishingRod(ServerPlayer player) {
        if (player.getMainHandItem().canPerformAction(ItemAbilities.FISHING_ROD_CAST)) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().canPerformAction(ItemAbilities.FISHING_ROD_CAST)) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private static Optional<FishCatchData> safeReadCatch(
            FishingTaskProvider provider,
            ServerPlayer player,
            ItemStack caught,
            FishingCatchContext context
    ) {
        try {
            Optional<FishCatchData> result = provider.readCatch(player, caught, context);
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException exception) {
            TaskboundMod.LOGGER.warn("Fishing provider {} failed to inspect a catch", provider.id(), exception);
            return Optional.empty();
        }
    }

    private record PotionImpact(
            MinecraftServer server,
            ItemStack item,
            ResourceLocation dimension,
            Vec3 position,
            long gameTime
    ) {
        private PotionImpact {
            item = item.copy();
        }

        private boolean matches(Entity target) {
            return target.level().dimension().location().equals(dimension)
                    && target.level().getGameTime() == gameTime
                    && target.position().distanceToSqr(position) <= 64.0D;
        }
    }

    private record CureAttempt(MinecraftServer server, UUID playerId, String taskId) {
    }
}
