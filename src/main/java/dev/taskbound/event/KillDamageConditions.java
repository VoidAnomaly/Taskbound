package dev.taskbound.event;

import dev.taskbound.core.GeneratedObjective;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/** Fatal-damage requirements shared by ordinary and tracked kills. */
final class KillDamageConditions {
    private KillDamageConditions() { }

    static ServerPlayer player(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        Entity direct = source.getDirectEntity();
        // Some mods supply only the projectile/TNT as the source. Its retained owner is
        // evidence; a nearby player or last attacker is not. Preserve explicit attribution.
        if (source.getEntity() != null && source.getEntity() != direct) return null;
        Entity owner = direct instanceof Projectile projectile ? projectile.getOwner()
                : direct instanceof PrimedTnt tnt ? tnt.getOwner() : null;
        return owner instanceof ServerPlayer player ? player : null;
    }

    static boolean matches(GeneratedObjective objective, DamageSource source) {
        String type = VanillaObjectiveConditions.text(objective.parameters(), "required_damage_type");
        String tag = VanillaObjectiveConditions.text(objective.parameters(), "required_damage_type_tag");
        return (type == null || source.is(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(type))))
                && (tag == null || source.is(TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(tag))));
    }

    static ItemStack weapon(DamageSource source) {
        // LivingEntity reports its current main hand for every damage type. That is not
        // weapon evidence for retaliation or environmental damage attributed to the player.
        if (source.is(DamageTypes.THORNS)
                || source.getDirectEntity() instanceof ServerPlayer
                && (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)
                    || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                    || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_LIGHTNING))) {
            return ItemStack.EMPTY;
        }
        // A modded projectile, beam or DamageSource can supply its actual weapon through
        // this vanilla hook. Never substitute the owner's held item when it supplies none.
        ItemStack weapon = source.getWeaponItem();
        return weapon == null ? ItemStack.EMPTY : weapon;
    }
}
