package dev.taskbound.client;

import dev.taskbound.TaskboundMod;
import dev.taskbound.config.TaskboundClientConfig;
import dev.taskbound.porter.PorterNavigation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Mirrors the player's server-owned delivery compasses into Xaero's native third-party waypoint API. */
@EventBusSubscriber(modid = TaskboundMod.MOD_ID, value = Dist.CLIENT)
public final class PorterWaypointClient {
    private static final ResourceLocation ORIGIN = ResourceLocation.fromNamespaceAndPath(TaskboundMod.MOD_ID, "porter");
    private static final Set<Object> collections = new HashSet<>();
    private static Map<String, Target> previous = Map.of();
    private static Object previousRoot;
    private static Api api;
    private static boolean checkedApi;
    private static int ticks;

    private record Target(String name, GlobalPos position) { }

    private PorterWaypointClient() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !TaskboundClientConfig.PORTER_XAERO_WAYPOINTS.get()) {
            clear();
            return;
        }
        if (++ticks % 5 != 0) return;
        Map<String, Target> targets = new LinkedHashMap<>();
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            var stack = minecraft.player.getInventory().getItem(slot);
            var taskId = PorterNavigation.guideTaskId(stack);
            if (taskId.isEmpty() || !PorterNavigation.guideOwnerId(stack).filter(minecraft.player.getUUID()::equals).isPresent()) continue;
            var tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            if (tracker == null || tracker.target().isEmpty()) continue;
            targets.putIfAbsent(taskId.get(), new Target(stack.getHoverName().getString(), tracker.target().get()));
        }
        if (targets.isEmpty()) {
            clear();
            return;
        }
        if (!checkedApi) {
            checkedApi = true;
            try { api = new Api(); }
            catch (ClassNotFoundException absent) {
                if (net.neoforged.fml.ModList.get().isLoaded("xaerominimap")) unavailable(absent);
                return;
            }
            catch (ReflectiveOperationException | LinkageError unsupported) { unavailable(unsupported); return; }
        }
        if (api == null) return;
        try {
            Object session = api.currentSession.invoke(null);
            Object minimap = session == null ? null : api.waypointsManager.invoke(session);
            Object manager = minimap == null ? null : api.worldManager.invoke(minimap);
            Object root = manager == null ? null : api.autoRoot.invoke(manager);
            if (root == null) { clear(); return; }
            if (root == previousRoot && targets.equals(previous)) return;
            clear();
            Object dimensions = api.dimensionHelper.invoke(minimap);
            Object rootPath = api.path.invoke(root);
            for (var entry : targets.entrySet()) {
                Target target = entry.getValue();
                String dimensionName = (String) api.dimensionDirectory.invoke(dimensions, target.position().dimension());
                Object path = api.resolve.invoke(rootPath, dimensionName);
                Object container = api.addContainer.invoke(root, path);
                Object waypointManager = api.thirdPartyManager.invoke(container);
                Object waypoints = api.collection.invoke(waypointManager, ORIGIN);
                collections.add(waypoints);
                var position = target.position().pos();
                Object waypoint = api.waypoint.newInstance(position.getX(), position.getY(), position.getZ(), target.name(), "P", 6);
                api.temporary.invoke(waypoint, true);
                api.visibility.invoke(waypoint, api.globalVisibility);
                api.add.invoke(waypoints, entry.getKey(), waypoint);
            }
            previousRoot = root;
            previous = Map.copyOf(targets);
            api.refreshMap();
        } catch (ReflectiveOperationException | LinkageError unsupported) {
            unavailable(unsupported);
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void clear() {
        if (api != null) {
            for (Object collection : collections) {
                try { api.clear.invoke(collection); }
                catch (ReflectiveOperationException | LinkageError failure) {
                    TaskboundMod.LOGGER.warn("Could not clear Taskbound's temporary Xaero waypoints", failure);
                }
            }
            if (!collections.isEmpty()) api.refreshMap();
        }
        collections.clear();
        previousRoot = null;
        previous = Map.of();
    }

    private static void unavailable(Throwable failure) {
        clear();
        api = null;
        TaskboundMod.LOGGER.warn("Xaero's waypoint API is unavailable; Porter delivery compasses remain usable", failure);
    }

    /** Follows Xaero 26.6's own Waystones adapter; never adds to or saves a personal waypoint set. */
    private static final class Api {
        final Method currentSession, waypointsManager, worldManager, autoRoot, dimensionHelper, dimensionDirectory;
        final Method path, resolve, addContainer, thirdPartyManager, collection, clear, add, temporary, visibility;
        final Constructor<?> waypoint;
        final Object globalVisibility;
        final java.lang.reflect.Field mapSupport;
        final Method mapRefresh;

        Api() throws ReflectiveOperationException {
            Class<?> session = Class.forName("xaero.common.XaeroMinimapSession");
            Class<?> minimap = Class.forName("xaero.hud.minimap.module.MinimapSession");
            Class<?> worlds = Class.forName("xaero.hud.minimap.world.MinimapWorldManager");
            Class<?> dimensions = Class.forName("xaero.hud.minimap.world.MinimapDimensionHelper");
            Class<?> container = Class.forName("xaero.hud.minimap.world.container.MinimapWorldContainer");
            Class<?> pathType = Class.forName("xaero.hud.path.XaeroPath");
            Class<?> thirdParty = Class.forName("xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager");
            Class<?> waypoints = Class.forName("xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints");
            Class<?> waypointType = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Class<?> visibilityType = Class.forName("xaero.common.minimap.waypoints.WaypointVisibilityType");
            currentSession = session.getMethod("getCurrentSession");
            waypointsManager = session.getMethod("getWaypointsManager");
            worldManager = minimap.getMethod("getWorldManager");
            autoRoot = worlds.getMethod("getAutoRootContainer");
            dimensionHelper = minimap.getMethod("getDimensionHelper");
            dimensionDirectory = dimensions.getMethod("getDimensionDirectoryName", ResourceKey.class);
            path = container.getMethod("getPath");
            resolve = pathType.getMethod("resolve", String.class);
            addContainer = container.getMethod("addSubContainer", pathType);
            thirdPartyManager = container.getMethod("getThirdPartyWaypointManager");
            collection = thirdParty.getMethod("get", ResourceLocation.class);
            clear = waypoints.getMethod("clear");
            add = waypoints.getMethod("add", String.class, waypointType);
            waypoint = waypointType.getConstructor(int.class, int.class, int.class, String.class, String.class, int.class);
            temporary = waypointType.getMethod("setTemporary", boolean.class);
            visibility = waypointType.getMethod("setVisibility", visibilityType);
            globalVisibility = visibilityType.getField("GLOBAL").get(null);
            java.lang.reflect.Field support = null;
            Method refresh = null;
            try {
                support = Class.forName("xaero.map.mods.SupportMods").getField("xaeroMinimap");
                refresh = Class.forName("xaero.map.mods.SupportXaeroMinimap").getMethod("requestWaypointsRefresh");
            } catch (ClassNotFoundException absentWorldMap) { /* Minimap alone still renders native markers. */ }
            catch (ReflectiveOperationException | LinkageError unsupportedWorldMap) {
                TaskboundMod.LOGGER.warn("Xaero's World Map refresh bridge is unavailable; Minimap delivery markers remain enabled", unsupportedWorldMap);
                support = null;
                refresh = null;
            }
            mapSupport = support;
            mapRefresh = refresh;
        }

        void refreshMap() {
            if (mapSupport == null || mapRefresh == null) return;
            try {
                Object support = mapSupport.get(null);
                if (support != null) mapRefresh.invoke(support);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Xaero also refreshes when opening the map. Its optional bridge cannot disable the compass/minimap.
            }
        }
    }
}
