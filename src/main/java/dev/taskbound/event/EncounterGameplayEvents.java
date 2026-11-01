package dev.taskbound.event;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.core.TaskStatus;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Native action evidence; persistent attempt state lives with the accepted task. */
public final class EncounterGameplayEvents {
    private record Critical(UUID target, long tick) { }
    private record Landing(float distance, long tick, boolean damaged) { }
    private record RaidHit(UUID attacker, long tick) { }
    private static final Map<UUID, Critical> CRITICALS = new HashMap<>();
    private static final Map<UUID, RaidHit> RAID_HITS = new HashMap<>();
    private static final Map<UUID, Landing> LANDINGS = new HashMap<>();
    private static final Map<UUID, ItemStack> CONSUMPTIONS = new HashMap<>();
    static final String TRIAL_STARTED = "encounter.trial_started";
    static final String ASHEN_STARTED = "encounter.ashen_started";
    static final String LOCKED_SWORD = "encounter.sword";
    static final String CITY = "encounter.city";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void attacking(AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getMainHandItem().is(Items.IRON_SWORD) && TaskboundRuntime.get().activeTasks(player).stream()
                .anyMatch(active -> active.task().objectives().stream().anyMatch(value -> truth(value, "same_weapon"))))
            TaskItemIdentity.ensure(player.getMainHandItem());
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            if (flag(active, ASHEN_STARTED) && !java.util.Objects.equals(active.progress().additionalData().get(LOCKED_SWORD), TaskItemIdentity.get(player.getMainHandItem())))
                fail(player, active, "message.taskbound.encounter.original_sword_required");
            else if (restrictedWeapons(active) && !player.getMainHandItem().is(Items.IRON_SWORD))
                fail(player, active, "message.taskbound.encounter.weapon_disallowed");
        }
        ProjectileReflectionTracking.attack(player, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void critical(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.isCriticalHit()) CRITICALS.put(player.getUUID(), new Critical(event.getTarget().getUUID(), player.serverLevel().getGameTime()));
        else CRITICALS.remove(player.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fall(LivingFallEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            LANDINGS.put(player.getUUID(), new Landing(event.getDistance(), player.serverLevel().getGameTime(), false));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        if (event.getEntity() instanceof ServerPlayer fallen && event.getSource().is(DamageTypes.FALL)) {
            Landing landing = LANDINGS.get(fallen.getUUID());
            if (landing != null && landing.tick == fallen.serverLevel().getGameTime())
                LANDINGS.put(fallen.getUUID(), new Landing(landing.distance, landing.tick, true));
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        checkAttackRestrictions(player, event.getSource());
        if (event.getEntity() instanceof Pillager pillager) {
            RAID_HITS.remove(pillager.getUUID());
            if (event.getSource().is(DamageTypes.PLAYER_ATTACK) && pillager.hasActiveRaid()
                    && pillager.getCurrentRaid() != null && !pillager.getCurrentRaid().isOver())
                RAID_HITS.put(pillager.getUUID(), new RaidHit(player.getUUID(), player.serverLevel().getGameTime()));
        }
        Critical critical = CRITICALS.get(player.getUUID());
        if (critical != null && critical.tick == player.serverLevel().getGameTime()
                && critical.target.equals(event.getEntity().getUUID()) && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            CRITICALS.remove(player.getUUID());
            advanceCombat(player, "critical_hit", event.getEntity(), weapon(event.getSource()), 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void died(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
                boolean ashen = flag(active, ASHEN_STARTED);
                boolean trial = active.task().status() == TaskStatus.ACTIVE && active.task().objectives().stream().anyMatch(objective ->
                        objective.type().equals("trial_encounter") && truth(objective, "no_death")
                                && (flag(active, TRIAL_STARTED) || truth(objective, "guard_from_acceptance")));
                boolean dragon = active.task().status() == TaskStatus.ACTIVE && active.progress().additionalData().containsKey("encounter.dragon");
                if (ashen || trial || dragon) fail(player, active, "message.taskbound.encounter.survival_failed");
            }
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        Entity victim = event.getEntity();
        // Raider.die removes raid membership before LivingDeathEvent; retain the damaging hit's membership.
        RaidHit raid = RAID_HITS.remove(victim.getUUID());
        if (event.getSource().is(DamageTypes.PLAYER_ATTACK) && raid != null && raid.attacker.equals(player.getUUID())
                && raid.tick == player.serverLevel().getGameTime())
            advanceCombat(player, "raid_pillager_kill", victim, weapon(event.getSource()), 1);
        if (victim instanceof Ghast && event.getSource().getDirectEntity() instanceof LargeFireball fireball
                && fireball.getPersistentData().hasUUID("taskbound.original_ghast")
                && fireball.getPersistentData().getUUID("taskbound.original_ghast").equals(victim.getUUID())
                && fireball.getPersistentData().hasUUID("taskbound.deflector")
                && fireball.getPersistentData().getUUID("taskbound.deflector").equals(player.getUUID())) {
            advanceCombat(player, "returned_ghast_fireball_kill", victim, ItemStack.EMPTY, 1);
        }
        ItemStack weapon = weapon(event.getSource());
        TaskboundRuntime.get().advanceMatching(player, (active, objective) -> {
            if (!objective.type().equals("tracked_kill") || !targetMatches(objective, victim)
                    || !event.getSource().is(DamageTypes.PLAYER_ATTACK) && "melee".equals(text(objective, "method"))
                    || !(event.getSource().getDirectEntity() instanceof AbstractArrow) && "projectile".equals(text(objective, "method"))
                    || !VanillaObjectiveConditions.matches(objective, player, player.serverLevel(), victim.blockPosition(), weapon, false)) return false;
            if (truth(objective, "same_weapon")) {
                if (!event.getSource().is(DamageTypes.PLAYER_ATTACK) || !player.getMainHandItem().is(Items.IRON_SWORD)) return false;
                String id = TaskItemIdentity.ensure(player.getMainHandItem());
                Object locked = active.progress().additionalData().get(LOCKED_SWORD);
                if (locked != null && !locked.equals(id)) { fail(player, active, "message.taskbound.encounter.weapon_replaced"); return false; }
                active.progress().putAdditionalData(LOCKED_SWORD, id);
            }
            if (truth(objective, "bind_structure")) {
                Map<String, Object> city = structure(active);
                if (city == null) {
                    city = TaskStructureIdentity.capture(player.serverLevel(), victim.blockPosition(), text(objective, "required_structure"));
                    if (city == null || !TaskStructureIdentity.contains(player.serverLevel(), player.blockPosition(), city)) return false;
                    active.progress().putAdditionalData(CITY, city);
                } else if (!TaskStructureIdentity.contains(player.serverLevel(), victim.blockPosition(), city)
                        || !TaskStructureIdentity.contains(player.serverLevel(), player.blockPosition(), city)) return false;
            }
            if (truth(objective, "no_death_after_start")) active.progress().putAdditionalData(ASHEN_STARTED, true);
            TaskboundRuntime.get().markChanged(player);
            return true;
        }, 1);
    }

    /** Called only by the native ServerPlayer movement-stat hook. Units are vanilla centimetres. */
    public static void movement(ServerPlayer player, Stat<?> stat, int centimetres) {
        if (centimetres <= 0 || centimetres > 800 || player.isSpectator() || player.getAbilities().flying) return;
        String mode;
        if (stat.equals(Stats.CUSTOM.get(Stats.SWIM_ONE_CM)) && player.isSwimming() && !player.isPassenger()) mode = "swim";
        else if (stat.equals(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM)) && player.isSprinting() && player.onGround() && !player.isPassenger()) mode = "sprint";
        else if (stat.equals(Stats.CUSTOM.get(Stats.BOAT_ONE_CM)) && player.getVehicle() instanceof Boat boat
                && boat.getControllingPassenger() == player && (boat.getPaddleState(0) || boat.getPaddleState(1))) mode = "boat";
        else return;
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            for (GeneratedObjective objective : active.task().objectives()) {
                if (!TaskboundRuntime.get().isObjectiveEligible(active, objective) || !objective.type().equals("travel_distance")
                        || !mode.equals(text(objective, "mode")) || !VanillaObjectiveConditions.matches(objective, player,
                        player.serverLevel(), player.blockPosition(), ItemStack.EMPTY, false)) continue;
                String key = "encounter.distance_remainder." + objective.key();
                int remainder = ((Number)active.progress().additionalData().getOrDefault(key, 0)).intValue() + centimetres;
                active.progress().putAdditionalData(key, remainder % 100);
                TaskboundRuntime.get().markChanged(player);
                if (remainder >= 100) TaskboundRuntime.get().advanceTaskObjective(player, active.task().taskId(), value -> value.key().equals(objective.key()), remainder / 100);
                break;
            }
        }
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.serverLevel().getGameTime();
        RAID_HITS.values().removeIf(hit -> hit.tick < now - 1);
        Landing landing = LANDINGS.get(player.getUUID());
        if (landing != null && now > landing.tick) {
            LANDINGS.remove(player.getUUID());
            if (landing.damaged && now - landing.tick <= 2 && player.isAlive()) {
                TaskboundRuntime.get().advanceMatching(player, objective -> objective.type().equals("survive_fall")
                        && landing.distance >= number(objective, "minimum_fall_distance", Double.MAX_VALUE)
                        && VanillaObjectiveConditions.matches(objective, player, player.serverLevel(), player.blockPosition(), ItemStack.EMPTY, false), 1);
            }
        }
        Critical critical = CRITICALS.get(player.getUUID());
        if (critical != null && critical.tick < now) CRITICALS.remove(player.getUUID());
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE) continue;
            Map<String, Object> city = structure(active);
            if (city != null && active.task().objectives().stream().anyMatch(value -> truth(value, "prevent_departure"))
                    && !TaskStructureIdentity.contains(player.serverLevel(), player.blockPosition(), city)) {
                fail(player, active, "message.taskbound.encounter.city_departure");
                continue;
            }
            checkEquipment(player, active);
        }
        DragonEncounterTracking.tick(player);
    }

    @SubscribeEvent
    public void equipment(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) checkEquipment(player, active);
    }

    /** The native equipment callback also catches remove-and-replace within one server tick. */
    public static void equipmentChanged(ServerPlayer player, EquipmentSlot slot, ItemStack replacement) {
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE || !flag(active, TRIAL_STARTED)) continue;
            for (GeneratedObjective objective : active.task().objectives()) {
                if (!objective.type().equals("trial_encounter")) continue;
                boolean invalidArmor = "full_iron".equals(text(objective, "armor_set")) && switch (slot) {
                    case HEAD -> !replacement.is(Items.IRON_HELMET);
                    case CHEST -> !replacement.is(Items.IRON_CHESTPLATE);
                    case LEGS -> !replacement.is(Items.IRON_LEGGINGS);
                    case FEET -> !replacement.is(Items.IRON_BOOTS);
                    default -> false;
                };
                boolean invalidShield = slot == EquipmentSlot.OFFHAND && "iron_sword_and_shield".equals(text(objective, "weapons")) && !replacement.is(Items.SHIELD);
                if (invalidArmor || invalidShield) { fail(player, active, "message.taskbound.encounter.equipment_removed"); break; }
            }
        }
    }

    static boolean checkEquipment(ServerPlayer player, ActiveTask active) {
        if (active.task().status() != TaskStatus.ACTIVE || !flag(active, TRIAL_STARTED)) return true;
        for (GeneratedObjective objective : active.task().objectives()) {
            if (!objective.type().equals("trial_encounter")) continue;
            if ("full_iron".equals(text(objective, "armor_set")) && !fullIron(player)) {
                fail(player, active, "message.taskbound.encounter.iron_armor_required"); return false;
            }
            if ("iron_sword_and_shield".equals(text(objective, "weapons")) && !player.getOffhandItem().is(Items.SHIELD)) {
                fail(player, active, "message.taskbound.encounter.shield_required"); return false;
            }
        }
        return true;
    }

    private static void checkAttackRestrictions(ServerPlayer player, DamageSource source) {
        ItemStack weapon = weapon(source);
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            if (flag(active, ASHEN_STARTED) && (!source.is(DamageTypes.PLAYER_ATTACK)
                    || !active.progress().additionalData().get(LOCKED_SWORD).equals(TaskItemIdentity.get(weapon)))) {
                fail(player, active, "message.taskbound.encounter.original_sword_required"); continue;
            }
            if (active.task().status() != TaskStatus.ACTIVE || !flag(active, TRIAL_STARTED)) continue;
            if (active.task().objectives().stream().anyMatch(objective -> objective.type().equals("trial_encounter")
                    && "iron_sword_and_shield".equals(text(objective, "weapons")))
                    && (!source.is(DamageTypes.PLAYER_ATTACK) || !weapon.is(Items.IRON_SWORD))) {
                fail(player, active, "message.taskbound.encounter.weapon_disallowed");
            }
        }
    }

    @SubscribeEvent public void consuming(LivingEntityUseItemEvent.Start event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        CONSUMPTIONS.put(player.getUUID(), event.getItem().copy());
        if (event.getItem().is(Items.BOW) || event.getItem().is(Items.CROSSBOW) || event.getItem().is(Items.TRIDENT))
            for (ActiveTask active : TaskboundRuntime.get().activeTasks(player))
                if (restrictedWeapons(active)) fail(player, active, "message.taskbound.encounter.weapon_disallowed");
    }
    @SubscribeEvent public void consumed(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack item = CONSUMPTIONS.remove(player.getUUID());
            checkConsumption(player, item == null ? event.getItem() : item);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void projectile(EntityJoinLevelEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof ThrownPotion potion && potion.getOwner() instanceof ServerPlayer player)
            checkConsumption(player, potion.getItem());
        if ((event.getEntity() instanceof AbstractArrow || event.getEntity() instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity)
                && event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player)
            for (ActiveTask active : TaskboundRuntime.get().activeTasks(player))
                if (restrictedWeapons(active)) fail(player, active, "message.taskbound.encounter.weapon_disallowed");
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void totem(LivingUseTotemEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player)
            for (ActiveTask active : TaskboundRuntime.get().activeTasks(player))
                if (active.task().status() == TaskStatus.ACTIVE && flag(active, TRIAL_STARTED)
                        && active.task().objectives().stream().anyMatch(value -> truth(value, "no_totem")))
                    fail(player, active, "message.taskbound.encounter.totem_disallowed");
    }

    private static void checkConsumption(ServerPlayer player, ItemStack item) {
        boolean potion = item.has(DataComponents.POTION_CONTENTS);
        boolean healing = item.is(Items.GOLDEN_APPLE) || item.is(Items.ENCHANTED_GOLDEN_APPLE);
        for (var effect : item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects())
            if (effect.getEffect().equals(MobEffects.HEAL) || effect.getEffect().equals(MobEffects.REGENERATION)) healing = true;
        var stew = item.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stew != null && stew.effects().stream().anyMatch(effect -> effect.effect().equals(MobEffects.HEAL)
                || effect.effect().equals(MobEffects.REGENERATION))) healing = true;
        boolean isHealing = healing;
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player))
            if (active.task().status() == TaskStatus.ACTIVE && flag(active, TRIAL_STARTED)
                    && active.task().objectives().stream().anyMatch(value -> potion && truth(value, "no_potions") || isHealing && truth(value, "no_healing")))
                fail(player, active, "message.taskbound.encounter.consumable_disallowed");
    }

    static void advanceCombat(ServerPlayer player, String action, Entity target, ItemStack weapon, int amount) {
        TaskboundRuntime.get().advanceMatching(player, objective -> objective.type().equals("combat_action")
                && action.equals(text(objective, "action")) && targetMatches(objective, target)
                && VanillaObjectiveConditions.matches(objective, player, player.serverLevel(), target.blockPosition(), weapon, false), amount);
    }
    static boolean targetMatches(GeneratedObjective objective, Entity target) {
        return (!truth(objective, "hostile_only") || target instanceof Enemy)
                && (text(objective, "entity") == null || BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString().equals(text(objective, "entity")));
    }
    static boolean fullIron(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS);
    }
    private static boolean restrictedWeapons(ActiveTask active) {
        return active.task().status() == TaskStatus.ACTIVE && flag(active, TRIAL_STARTED)
                && active.task().objectives().stream().anyMatch(objective -> objective.type().equals("trial_encounter")
                && "iron_sword_and_shield".equals(text(objective, "weapons")));
    }
    static boolean flag(ActiveTask active, String key) { return Boolean.TRUE.equals(active.progress().additionalData().get(key)); }
    static boolean truth(GeneratedObjective objective, String key) { return Boolean.TRUE.equals(objective.parameters().get(key)); }
    static String text(GeneratedObjective objective, String key) { return VanillaObjectiveConditions.text(objective.parameters(), key); }
    static double number(GeneratedObjective objective, String key, double fallback) { return VanillaObjectiveConditions.decimal(objective.parameters(), key, fallback); }
    static ItemStack weapon(DamageSource source) { return source.getWeaponItem() == null ? ItemStack.EMPTY : source.getWeaponItem(); }
    static void fail(ServerPlayer player, ActiveTask active, String message) { TaskboundRuntime.get().failActiveTask(player, active.task().taskId(), net.minecraft.network.chat.Component.translatable(message)); }
    @SuppressWarnings("unchecked") private static Map<String, Object> structure(ActiveTask active) {
        return active.progress().additionalData().get(CITY) instanceof Map<?, ?> map ? (Map<String,Object>)map : null;
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ProjectileReflectionTracking.clear(event.getEntity().getUUID()); CRITICALS.remove(event.getEntity().getUUID()); LANDINGS.remove(event.getEntity().getUUID()); CONSUMPTIONS.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { ProjectileReflectionTracking.clear(); RAID_HITS.clear(); CRITICALS.clear(); LANDINGS.clear(); CONSUMPTIONS.clear(); }
}
