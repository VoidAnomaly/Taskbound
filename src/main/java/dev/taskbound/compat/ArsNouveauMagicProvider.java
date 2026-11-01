package dev.taskbound.compat;

import dev.taskbound.TaskboundMod;
import dev.taskbound.api.TaskGenerationContext;
import dev.taskbound.api.magic.MagicActionCandidate;
import dev.taskbound.api.magic.MagicActionData;
import dev.taskbound.api.magic.MagicTaskProvider;
import dev.taskbound.api.magic.MagicProgressListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Optional Ars Nouveau adapter that never links against Ars classes at compile time. */
public final class ArsNouveauMagicProvider implements MagicTaskProvider {
    public static final ResourceLocation ID = id("taskbound", "ars_nouveau");
    static final ResourceLocation CAST_SPELL = id("taskbound", "cast_spell");
    static final ResourceLocation HARM = id("ars_nouveau", "glyph_harm");
    static final ResourceLocation ZOMBIE = id("minecraft", "zombie");
    private static final ResourceLocation UNKNOWN_BIOME = id("minecraft", "unknown");
    private static final MagicActionCandidate HARM_CANDIDATE = new MagicActionCandidate(
            CAST_SPELL,
            Optional.of(HARM),
            Set.of(),
            Set.of(ZOMBIE),
            0,
            Map.of(
                    "compatibility_mod", "ars_nouveau",
                    "required_cast_form", "projectile_or_touch"
            )
    );

    private final ArsReflection ars;
    private final AtomicBoolean runtimeFailureLogged = new AtomicBoolean();
    private volatile MagicProgressListener progressListener = (player, action) -> {
    };

    private ArsNouveauMagicProvider(ArsReflection ars) {
        this.ars = ars;
        addDynamicListener(ars.effectResolvePostEvent(), this::onEffectResolved);
    }

    /**
     * Creates and subscribes the provider when the compatible Ars API is present.
     * Absence is expected and produces an empty result; an incompatible installed API is logged and disabled.
     */
    public static Optional<ArsNouveauMagicProvider> createIfAvailable() {
        try {
            return Optional.of(new ArsNouveauMagicProvider(ArsReflection.load()));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            TaskboundMod.LOGGER.warn("Ars Nouveau compatibility could not be initialized", exception);
            return Optional.empty();
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supportsAction(ResourceLocation actionType) {
        return isSupportedAction(actionType);
    }

    @Override
    public Collection<MagicActionCandidate> getEligibleActions(
            ServerPlayer player,
            TaskGenerationContext context
    ) {
        Objects.requireNonNull(player, "player");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            try {
                if (!stack.isEmpty() && ars.stackContainsEligibleHarmSpell(stack)) {
                    return List.of(HARM_CANDIDATE);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                logRuntimeFailure("inspect an Ars Nouveau caster item", exception);
            }
        }
        return List.of();
    }

    @Override
    public void registerProgressListener(MagicProgressListener listener) {
        progressListener = Objects.requireNonNull(listener, "listener");
    }

    static boolean isSupportedAction(ResourceLocation actionType) {
        return actionType != null && ArsNouveauSpellRecipes.isSupportedAction(actionType.toString());
    }

    static boolean isEligibleHarmRecipe(Iterable<ResourceLocation> recipe) {
        var serialized = new java.util.ArrayList<String>();
        for (ResourceLocation part : recipe) {
            serialized.add(part.toString());
        }
        return ArsNouveauSpellRecipes.isEligibleHarmRecipe(serialized);
    }

    private void onEffectResolved(Event event) {
        try {
            Object rawShooter = ars.shooter().get(event);
            Object rawHitResult = ars.rayTraceResult().get(event);
            Object rawEffect = ars.resolveEffect().get(event);
            if (!(rawShooter instanceof ServerPlayer player)
                    || !(ars.world().get(event) instanceof ServerLevel level)
                    || !(rawHitResult instanceof EntityHitResult hitResult)
                    || !HARM.equals(ars.registryName(rawEffect))) {
                return;
            }

            Entity target = hitResult.getEntity();
            ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
            ResourceLocation biome = level.getBiome(target.blockPosition()).unwrapKey()
                    .map(key -> key.location())
                    .orElse(UNKNOWN_BIOME);
            ItemStack casterTool = ars.casterTool(ars.context().get(event));

            progressListener.onMagicAction(player, new MagicActionData(
                    ID,
                    CAST_SPELL,
                    Optional.of(HARM),
                    Optional.of(HARM),
                    casterTool,
                    Optional.of(targetId),
                    biome,
                    level.dimension().location(),
                    level.getGameTime(),
                    Map.of("ars_effect_id", HARM.toString())
            ));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logRuntimeFailure("read an Ars Nouveau spell resolution", exception);
        }
    }

    private void logRuntimeFailure(String operation, Throwable exception) {
        if (runtimeFailureLogged.compareAndSet(false, true)) {
            TaskboundMod.LOGGER.warn("Taskbound could not {}", operation, exception);
        }
    }

    private static <T extends Event> void addDynamicListener(Class<T> eventType, Consumer<Event> listener) {
        NeoForge.EVENT_BUS.addListener(eventType, listener::accept);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private record ArsReflection(
            Class<? extends Event> effectResolvePostEvent,
            Method casterFromStack,
            Method casterMaxSlots,
            Method casterSpell,
            Method spellRecipe,
            Method spellPartRegistryName,
            Method contextCasterTool,
            Field world,
            Field shooter,
            Field rayTraceResult,
            Field resolveEffect,
            Field context
    ) {
        private static ArsReflection load() throws ReflectiveOperationException {
            ClassLoader loader = ArsNouveauMagicProvider.class.getClassLoader();
            Class<? extends Event> eventType = Class.forName(
                    "com.hollingsworth.arsnouveau.api.event.EffectResolveEvent$Post", false, loader
            ).asSubclass(Event.class);
            Class<?> casterRegistry = Class.forName(
                    "com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry", false, loader);
            Class<?> abstractCaster = Class.forName(
                    "com.hollingsworth.arsnouveau.api.spell.AbstractCaster", false, loader);
            Class<?> spell = Class.forName(
                    "com.hollingsworth.arsnouveau.api.spell.Spell", false, loader);
            Class<?> spellPart = Class.forName(
                    "com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart", false, loader);
            Class<?> spellContext = Class.forName(
                    "com.hollingsworth.arsnouveau.api.spell.SpellContext", false, loader);

            return new ArsReflection(
                    eventType,
                    casterRegistry.getMethod("from", ItemStack.class),
                    abstractCaster.getMethod("getMaxSlots"),
                    abstractCaster.getMethod("getSpell", int.class),
                    spell.getMethod("serializeRecipe"),
                    spellPart.getMethod("getRegistryName"),
                    spellContext.getMethod("getCasterTool"),
                    eventType.getField("world"),
                    eventType.getField("shooter"),
                    eventType.getField("rayTraceResult"),
                    eventType.getField("resolveEffect"),
                    eventType.getField("context")
            );
        }

        private boolean stackContainsEligibleHarmSpell(ItemStack stack) throws ReflectiveOperationException {
            Object caster = casterFromStack.invoke(null, stack);
            if (caster == null) {
                return false;
            }
            int maximumSlots = ((Number) casterMaxSlots.invoke(caster)).intValue();
            for (int slot = 0; slot < maximumSlots; slot++) {
                Object spell = casterSpell.invoke(caster, slot);
                Object rawRecipe = spellRecipe.invoke(spell);
                if (rawRecipe instanceof Iterable<?> recipe && isEligibleHarmRecipe(resourceLocations(recipe))) {
                    return true;
                }
            }
            return false;
        }

        private ResourceLocation registryName(Object spellPart) throws ReflectiveOperationException {
            Object result = spellPartRegistryName.invoke(spellPart);
            return result instanceof ResourceLocation id ? id : null;
        }

        private ItemStack casterTool(Object spellContext) throws ReflectiveOperationException {
            Object result = contextCasterTool.invoke(spellContext);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }

        private static List<ResourceLocation> resourceLocations(Iterable<?> values) {
            var result = new java.util.ArrayList<ResourceLocation>();
            for (Object value : values) {
                if (value instanceof ResourceLocation id) {
                    result.add(id);
                }
            }
            return List.copyOf(result);
        }
    }
}
