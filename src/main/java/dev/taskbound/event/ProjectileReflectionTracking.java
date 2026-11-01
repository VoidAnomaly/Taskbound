package dev.taskbound.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/** Retains the original shooter across vanilla projectile ownership changes. */
public final class ProjectileReflectionTracking {
    private record Attack(UUID target, long tick, ItemStack weapon) { }
    private static final Map<UUID, Attack> ATTACKS = new HashMap<>();
    private ProjectileReflectionTracking() { }

    public static void attack(ServerPlayer player, Entity target) {
        if (target instanceof LargeFireball)
            ATTACKS.put(player.getUUID(), new Attack(target.getUUID(), player.serverLevel().getGameTime(), player.getMainHandItem().copy()));
    }

    public static void reflected(Projectile projectile, Entity originalOwner, Entity newOwner, boolean succeeded) {
        if (!succeeded || !(projectile instanceof LargeFireball) || !(newOwner instanceof ServerPlayer player)) return;
        var data = projectile.getPersistentData();
        if (originalOwner instanceof Ghast) data.putUUID("taskbound.original_ghast", originalOwner.getUUID());
        if (!data.hasUUID("taskbound.original_ghast")) return;
        data.putUUID("taskbound.deflector", player.getUUID());
        Attack attack = ATTACKS.remove(player.getUUID());
        if (attack != null && attack.tick == player.serverLevel().getGameTime() && attack.target.equals(projectile.getUUID())
                && (!data.hasUUID("taskbound.credited_deflector") || !data.getUUID("taskbound.credited_deflector").equals(player.getUUID()))) {
            data.putUUID("taskbound.credited_deflector", player.getUUID());
            EncounterGameplayEvents.advanceCombat(player, "ghast_fireball_deflection", projectile, attack.weapon, 1);
        }
    }
    public static void clear(UUID player) { ATTACKS.remove(player); }
    public static void clear() { ATTACKS.clear(); }
}
