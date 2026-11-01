package dev.taskbound.event;

import dev.taskbound.core.ActiveTask;
import dev.taskbound.core.TaskStatus;
import dev.taskbound.persistence.TaskboundSavedData;
import dev.taskbound.runtime.TaskboundRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;

/** One saved dragon UUID and its original spike crystals bind both task stages. */
public final class DragonEncounterTracking {
    private static final String FIGHT = "encounter.dragon";
    private static final String CRYSTALS = "encounter.dragon_crystals";
    private static final String DESTROYED = "encounter.dragon_destroyed";
    private static final String FINISHER = "taskbound.sword_finisher";
    private DragonEncounterTracking() { }

    public static void tick(ServerPlayer player) {
        EndDragonFight fight = player.serverLevel().getDragonFight();
        if (fight == null || fight.getDragonUUID() == null
                || !(player.serverLevel().getEntity(fight.getDragonUUID()) instanceof EnderDragon dragon)
                || !dragon.isAlive() || dragon.distanceToSqr(player) > 65536) return;
        for (ActiveTask active : TaskboundRuntime.get().activeTasks(player)) {
            if (active.task().status() != TaskStatus.ACTIVE || active.task().objectives().stream().noneMatch(o -> o.type().equals("dragon_encounter"))) continue;
            Object existing = active.progress().additionalData().get(FIGHT);
            if (existing != null) {
                if (!existing.equals(dragon.getUUID().toString())) EncounterGameplayEvents.fail(player, active, "message.taskbound.encounter.dragon_ended");
                continue;
            }
            List<String> crystals = new ArrayList<>();
            for (var spike : SpikeFeature.getSpikesForLevel(player.serverLevel()))
                for (EndCrystal crystal : player.serverLevel().getEntitiesOfClass(EndCrystal.class, spike.getTopBoundingBox())) crystals.add(crystal.getUUID().toString());
            active.progress().putAdditionalData(FIGHT, dragon.getUUID().toString());
            active.progress().putAdditionalData(CRYSTALS, crystals);
            TaskboundRuntime.get().markChanged(player);
        }
    }

    public static void crystal(EndDragonFight fight, EndCrystal crystal, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !(source.getDirectEntity() instanceof AbstractArrow)
                || !EncounterGameplayEvents.weapon(source).is(Items.BOW) || fight.getDragonUUID() == null) return;
        TaskboundRuntime.get().advanceMatching(player, (active, objective) -> {
            if (!objective.type().equals("dragon_encounter") || !"bow_crystal".equals(EncounterGameplayEvents.text(objective, "action"))
                    || !fight.getDragonUUID().toString().equals(active.progress().additionalData().get(FIGHT))
                    || !values(active, CRYSTALS).contains(crystal.getUUID().toString())) return false;
            List<String> destroyed = values(active, DESTROYED);
            if (destroyed.contains(crystal.getUUID().toString())) return false;
            destroyed.add(crystal.getUUID().toString());
            active.progress().putAdditionalData(DESTROYED, destroyed);
            return true;
        }, 1);
    }

    public static void damaged(EnderDragon dragon, DamageSource source, boolean hurt) {
        if (!hurt || dragon.getHealth() > 0 || !(source.getEntity() instanceof ServerPlayer player)) return;
        if (source.is(DamageTypes.PLAYER_ATTACK) && EncounterGameplayEvents.weapon(source).is(ItemTags.SWORDS))
            dragon.getPersistentData().putUUID(FINISHER, player.getUUID());
        else dragon.getPersistentData().remove(FINISHER);
    }

    public static void defeated(EndDragonFight fight, EnderDragon dragon) {
        if (!(dragon.level() instanceof ServerLevel level) || !dragon.getUUID().equals(fight.getDragonUUID())
                || !dragon.getPersistentData().hasUUID(FINISHER)) return;
        UUID uuid = dragon.getPersistentData().getUUID(FINISHER);
        for (ActiveTask active : List.copyOf(TaskboundSavedData.get(level.getServer()).player(uuid).activeTasks().values())) {
            if (!dragon.getUUID().toString().equals(active.progress().additionalData().get(FIGHT))) continue;
            TaskboundRuntime.get().advanceTaskObjective(level.getServer(), uuid, active.task().taskId(), objective ->
                    objective.type().equals("dragon_encounter") && "sword_finish".equals(EncounterGameplayEvents.text(objective, "action")), 1);
        }
        dragon.getPersistentData().remove(FINISHER);
    }

    private static List<String> values(ActiveTask active, String key) {
        Object value = active.progress().additionalData().get(key);
        return value instanceof List<?> list ? new ArrayList<>(list.stream().filter(String.class::isInstance).map(String.class::cast).toList()) : new ArrayList<>();
    }
}
