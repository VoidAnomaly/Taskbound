package dev.taskbound.event;

import dev.taskbound.core.GeneratedObjective;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.biome.Biome;

/** Reads vanilla state at the qualifying event; it stores no provenance or movement state. */
final class VanillaObjectiveConditions {
    private VanillaObjectiveConditions() {
    }

    static boolean matches(GeneratedObjective objective, ServerPlayer player, ServerLevel level,
            BlockPos position, ItemStack tool, boolean rainMustReachPosition) {
        Map<String, Object> p = objective.parameters();
        String dimension = text(p, "required_dimension");
        String biome = text(p, "required_biome");
        String biomeTag = text(p, "required_biome_tag");
        if (dimension != null && !level.dimension().location().toString().equals(dimension)
                || biome != null && !level.getBiome(position).unwrapKey()
                        .map(key -> key.location().toString().equals(biome)).orElse(false)
                || biomeTag != null && !level.getBiome(position)
                        .is(TagKey.create(Registries.BIOME, ResourceLocation.parse(biomeTag)))
                || position.getY() < number(p, "min_y", Integer.MIN_VALUE)
                || position.getY() > number(p, "max_y", Integer.MAX_VALUE)
                || player.getHealth() > decimal(p, "max_health", Double.POSITIVE_INFINITY)
                || !VanillaEventRules.matchesTime(text(p, "required_time"), level.getDayTime())
                || !matchesWeather(text(p, "required_weather"), level, position, rainMustReachPosition)) {
            return false;
        }
        if (!effectMatches(player, p, "required_effect", "minimum_effect_amplifier")
                || !effectMatches(player, p, "required_effect_extra", "minimum_effect_extra_amplifier")
                || !itemMatches(tool, text(p, "required_weapon"), text(p, "required_weapon_tag"))
                || !itemMatches(tool, text(p, "required_tool"), text(p, "required_tool_tag"))) {
            return false;
        }
        String enchantment = text(p, "tool_enchantment");
        if (enchantment != null && enchantmentLevel(level, tool, enchantment)
                < number(p, "minimum_tool_enchantment_level", 1)) {
            return false;
        }
        String structure = text(p, "required_structure");
        if (structure != null && !level.structureManager().getStructureWithPieceAt(position,
                holder -> holder.unwrapKey().map(key -> key.location().toString().equals(structure)).orElse(false))
                .isValid()) {
            return false;
        }
        String structureTag = text(p, "required_structure_tag");
        return structureTag == null || level.structureManager().getStructureWithPieceAt(position,
                TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(structureTag))).isValid();
    }

    static boolean itemMatches(ItemStack stack, String item, String tag) {
        if (item == null && tag == null) {
            return true;
        }
        return stack != null && !stack.isEmpty()
                && (item == null || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item))
                && (tag == null || stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag))));
    }

    static int enchantmentLevel(ServerLevel level, ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ResourceLocation.parse(id))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }

    private static boolean effectMatches(ServerPlayer player, Map<String, Object> parameters,
            String effectKey, String amplifierKey) {
        String effect = text(parameters, effectKey);
        if (effect == null) {
            return true;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effect))
                .map(holder -> player.getEffect(holder))
                .map(instance -> instance.getAmplifier() >= number(parameters, amplifierKey, 0)).orElse(false);
    }

    static boolean matchesWeather(String required, ServerLevel level, BlockPos pos, boolean exposed) {
        if (required == null) {
            return true;
        }
        Biome.Precipitation precipitation = level.getBiome(pos).value().getPrecipitationAt(pos);
        boolean ambient = level.isRaining() && precipitation != Biome.Precipitation.NONE;
        return switch (required) {
            case "rain" -> ambient && precipitation == Biome.Precipitation.RAIN
                    && (!exposed || level.isRainingAt(pos));
            case "snow" -> ambient && precipitation == Biome.Precipitation.SNOW
                    && (!exposed || level.canSeeSky(pos));
            case "thunder" -> ambient && level.isThundering() && (!exposed || level.isRainingAt(pos));
            case "clear" -> !ambient;
            default -> false;
        };
    }

    static String text(Map<String, ?> values, String key) {
        return values.get(key) instanceof String value ? value : null;
    }

    static int number(Map<String, ?> values, String key, int fallback) {
        return values.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    static double decimal(Map<String, ?> values, String key, double fallback) {
        return values.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }
}
