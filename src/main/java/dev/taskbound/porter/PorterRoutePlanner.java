package dev.taskbound.porter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Bounded radius search over automatically registered boards, without loading destination chunks. */
public final class PorterRoutePlanner {
    private static final int MAX_CANDIDATES = 128;
    private static final int MAX_SEARCH = 25000;

    private PorterRoutePlanner() { }

    public record Board(String id, String dimension, int x, int y, int z, String biome) {
        public double distance(Board other) { return Math.hypot((double) x - other.x, (double) z - other.z); }
    }

    public record Stop(Board board, List<String> deliver, List<String> collect, List<String> carry) {
        public Stop { deliver = List.copyOf(deliver); collect = List.copyOf(collect); carry = List.copyOf(carry); }
    }

    public record Route(List<Stop> stops, List<String> initialCargo, double distance) {
        public Route { stops = List.copyOf(stops); initialCargo = List.copyOf(initialCargo); }
    }

    public static Optional<Route> plan(Board origin, List<Board> known, Map<String, ?> parameters) {
        return plan(origin, known, parameters, route -> true);
    }

    public static Optional<Route> plan(Board origin, List<Board> known, Map<String, ?> parameters,
            java.util.function.Predicate<Route> allowed) {
        PorterObjectiveRules.validate(PorterObjectiveRules.TYPE, parameters);
        String kind = (String) parameters.get("route_kind");
        if (kind.equals("nether") || kind.equals("three_realms")) return dimensionRoute(origin, known, kind, allowed);
        int count = (int) PorterObjectiveRules.number(parameters, "stops", 1);
        double radius = Math.min(PorterObjectiveRules.number(parameters, "maximum_route_distance", 16384),
                PorterObjectiveRules.number(parameters, "maximum_leg_distance", 3072) * count);
        var choices = known.stream().filter(board -> !board.id.equals(origin.id) && board.dimension.equals(origin.dimension))
                .filter(board -> origin.distance(board) <= radius)
                .filter(board -> count != 1 || legAllowed(origin.distance(board), parameters))
                .sorted(Comparator.comparingDouble((Board board) -> origin.distance(board)).thenComparing(Board::id))
                .limit(MAX_CANDIDATES).toList();
        List<Board> path = new ArrayList<>();
        if (!search(origin, origin, choices, parameters, kind, count, path, 0, new int[]{0}, allowed)) return Optional.empty();
        return Optional.of(build(origin, path, kind));
    }

    private static boolean search(Board origin, Board previous, List<Board> choices, Map<String, ?> p, String kind,
            int remaining, List<Board> path, double distance, int[] iterations, java.util.function.Predicate<Route> allowed) {
        if (++iterations[0] > MAX_SEARCH) return false;
        double maximum = PorterObjectiveRules.number(p, "maximum_route_distance", 16384);
        if (distance > maximum) return false;
        if (remaining == 0) {
            double returnDistance = returns(kind) ? previous.distance(origin) : 0;
            if (returns(kind) && !legAllowed(returnDistance, p)) return false;
            double total = distance + returnDistance;
            return total >= PorterObjectiveRules.number(p, "minimum_route_distance", 0) && total <= maximum
                    && allowed.test(build(origin, path, kind));
        }
        for (Board board : choices) {
            if (path.stream().anyMatch(used -> used.id.equals(board.id))) continue;
            if (Boolean.TRUE.equals(p.get("different_biomes"))
                    && (board.biome.isBlank() || path.stream().anyMatch(used -> used.biome.equals(board.biome)))) continue;
            double leg = previous.distance(board);
            if (!legAllowed(leg, p)) continue;
            path.add(board);
            if (search(origin, board, choices, p, kind, remaining - 1, path, distance + leg, iterations, allowed)) return true;
            path.removeLast();
        }
        return false;
    }

    private static boolean legAllowed(double distance, Map<String, ?> p) {
        return distance >= PorterObjectiveRules.number(p, "minimum_leg_distance", 256)
                && distance <= PorterObjectiveRules.number(p, "maximum_leg_distance", 3072);
    }

    private static boolean returns(String kind) {
        return Set.of("collect_return", "receipt_return", "delivery_circuit", "collections", "exchange", "delivery_returns").contains(kind);
    }

    private static Route build(Board origin, List<Board> path, String kind) {
        List<Stop> stops = new ArrayList<>();
        List<String> initial = new ArrayList<>();
        List<String> incoming = new ArrayList<>();
        Board previous = origin;
        double distance = 0;
        for (int i = 0; i < path.size(); i++) {
            Board board = path.get(i);
            String outgoing = "outgoing_" + i;
            String returning = (kind.equals("receipt_return") || kind.equals("delivery_circuit") ? "receipt_" : "return_") + i;
            boolean pickupOnly = kind.equals("collect_return") || kind.equals("collections");
            List<String> deliver = pickupOnly ? List.of() : List.of(outgoing);
            List<String> collect = returns(kind) ? List.of(returning) : List.of();
            initial.addAll(deliver);
            incoming.addAll(collect);
            stops.add(new Stop(board, deliver, collect, List.of()));
            distance += previous.distance(board);
            previous = board;
        }
        if (returns(kind)) {
            stops.add(new Stop(origin, incoming, List.of(), List.of()));
            distance += previous.distance(origin);
        }
        return new Route(stops, initial, distance);
    }

    private static Optional<Route> dimensionRoute(Board origin, List<Board> known, String kind, java.util.function.Predicate<Route> allowed) {
        if (!origin.dimension.equals("minecraft:overworld")) return Optional.empty();
        List<Board> sorted = known.stream().sorted(Comparator.comparing(Board::id)).toList();
        for (Board nether : sorted.stream().filter(b -> b.dimension.equals("minecraft:the_nether")).limit(MAX_CANDIDATES).toList()) {
            if (kind.equals("nether")) {
                Route route = new Route(List.of(new Stop(nether, List.of("dispatch"), List.of(), List.of())), List.of("dispatch"), 0);
                if (allowed.test(route)) return Optional.of(route);
            } else for (Board end : sorted.stream().filter(b -> b.dimension.equals("minecraft:the_end")).limit(MAX_CANDIDATES).toList()) {
                Route route = new Route(List.of(new Stop(nether, List.of(), List.of(), List.of("dispatch")),
                        new Stop(end, List.of("dispatch"), List.of(), List.of())), List.of("dispatch"), 0);
                if (allowed.test(route)) return Optional.of(route);
            }
        }
        return Optional.empty();
    }
}
