package com.radaeli.createondemand.demand;

import java.util.*;

/** Loader-independent planning and FIFO accounting. Only Port may change the world. */
public final class DemandEngine {
    public enum Mode {
        STOCK, ORDERS;

        /** Unconfigured gauges accept orders; explicit opt-outs and removed modes remain stock-only. */
        public static Mode fromSaved(String value) { return value.isEmpty() || "ORDERS".equals(value) ? ORDERS : STOCK; }
    }
    public enum State { MATERIALS, PRODUCING, RETURNING, SHIPPING, SHIPPED, COMPLETED, SUSPENDED, CANCELLED }
    public record Key(UUID network, String item) {}
    public record Input(UUID source, Key key, int amount) {
        public Input { if (amount < 1) throw new IllegalArgumentException("ingredient amount"); }
    }
    public record Recipe(UUID id, Key output, int yield, Mode mode, List<Input> inputs, String signature) {
        public Recipe {
            inputs = List.copyOf(inputs);
            if (yield < 1 || inputs.isEmpty()) throw new IllegalArgumentException("invalid production recipe");
        }
    }
    public record Plan(Map<UUID, Integer> batches, Map<Key, Integer> used, Map<Key, Integer> missing,
                       Map<Key, Integer> producing, Map<Key, Integer> remaining, String error) {
        public boolean valid() { return error.isEmpty(); }
    }
    public interface Port {
        Map<Key, Integer> stock();
        Map<UUID, Recipe> recipes();
        boolean loaded(UUID gauge);
        boolean invalid(UUID gauge);
        /** Must check all inputs and commit a whole batch, or return false without dispatching. */
        boolean dispatch(Recipe recipe);
        void dirty();
        default void checkpoint() { dirty(); }
        default void failed(UUID order, RuntimeException error) {}
    }
    public static final class Order {
        public final UUID id, owner, root;
        public final int quantity;
        public final LinkedHashMap<UUID, Recipe> graph;
        public State state = State.MATERIALS;
        public String reason = "";
        public int sent, received;
        public boolean dispatching, completionNotified;
        public Order(UUID id, UUID owner, UUID root, int quantity, Map<UUID, Recipe> graph) {
            this.id = id; this.owner = owner; this.root = root; this.quantity = quantity;
            this.graph = new LinkedHashMap<>(graph);
        }
        public Recipe recipe() { return graph.get(root); }
        public boolean terminal() { return state == State.SHIPPED || state == State.COMPLETED || state == State.CANCELLED; }
    }
    public static final class Pending {
        public final UUID order, gauge;
        public final Key key;
        public int remaining;
        public Pending(UUID order, UUID gauge, Key key, int remaining) {
            this.order = order; this.gauge = gauge; this.key = key; this.remaining = remaining;
        }
    }
    public final List<Order> orders = new ArrayList<>();
    public final List<Pending> pending = new ArrayList<>();
    private final Port port;
    public DemandEngine(Port port) { this.port = port; }

    public static Map<UUID, Recipe> graph(UUID root, Map<UUID, Recipe> recipes) {
        Map<UUID, Recipe> graph = new LinkedHashMap<>();
        Deque<UUID> queue = new ArrayDeque<>(); queue.add(root);
        while (!queue.isEmpty()) {
            UUID id = queue.removeFirst(); Recipe r = recipes.get(id);
            if (r == null || graph.putIfAbsent(id, r) != null) continue;
            if (graph.size() > 4096) throw new IllegalArgumentException("chain_limit");
            for (Input input : r.inputs()) {
                Recipe source = recipes.get(input.source());
                if (source != null && source.mode() != Mode.STOCK) queue.addLast(source.id());
            }
        }
        return graph;
    }
    public static Plan plan(UUID root, int quantity, Map<UUID, Recipe> graph, Map<Key, Integer> available) {
        Planner planner = new Planner(graph, available);
        try {
            Recipe r = Objects.requireNonNull(graph.get(root), "Missing root recipe");
            planner.produce(r, quantity, new LinkedHashSet<>());
        } catch (ArithmeticException e) { planner.error = "amount_limit"; }
        catch (IllegalArgumentException | NullPointerException e) { planner.error = Objects.toString(e.getMessage(), "invalid_recipe"); }
        return new Plan(Collections.unmodifiableMap(planner.batches), Map.copyOf(planner.used),
                Map.copyOf(planner.missing), Map.copyOf(planner.producing), Map.copyOf(planner.pool), planner.error);
    }
    private static final class Planner {
        final Map<UUID, Recipe> graph;
        final Map<Key, Integer> pool, original;
        final LinkedHashMap<UUID, Integer> batches = new LinkedHashMap<>();
        final Map<Key, Integer> used = new LinkedHashMap<>(), missing = new LinkedHashMap<>(), producing = new LinkedHashMap<>();
        String error = "";
        int visited;
        Planner(Map<UUID, Recipe> graph, Map<Key, Integer> pool) {
            this.graph = graph; this.pool = new HashMap<>(pool); this.original = new HashMap<>(pool);
        }
        void need(Input input, int quantity, Set<UUID> path) {
            int take = Math.min(quantity, pool.getOrDefault(input.key(), 0));
            pool.merge(input.key(), -take, Integer::sum);
            int real = Math.min(take, original.getOrDefault(input.key(), 0));
            original.merge(input.key(), -real, Integer::sum);
            if (real > 0) used.merge(input.key(), real, Math::addExact);
            int deficit = quantity - take;
            if (deficit == 0) return;
            Recipe source = graph.get(input.source());
            if (source == null || source.mode() == Mode.STOCK || !source.output().equals(input.key())) {
                missing.merge(input.key(), deficit, Math::addExact); return;
            }
            int made = produce(source, deficit, path);
            pool.merge(input.key(), made - deficit, Math::addExact);
        }
        int produce(Recipe r, int quantity, Set<UUID> path) {
            if (++visited > 4096 || path.size() >= 128) throw new IllegalArgumentException("chain_limit");
            if (!path.add(r.id())) {
                StringJoiner cycle = new StringJoiner(","); path.forEach(id -> cycle.add(id.toString())); cycle.add(r.id().toString());
                throw new IllegalArgumentException("cycle:" + cycle);
            }
            int runs = Math.toIntExact((quantity + (long) r.yield() - 1) / r.yield());
            int made = Math.multiplyExact(runs, r.yield());
            for (Input input : r.inputs()) need(input, Math.multiplyExact(input.amount(), runs), path);
            path.remove(r.id());
            batches.merge(r.id(), runs, Math::addExact);
            producing.merge(r.output(), made, Math::addExact);
            return made;
        }
    }
    /** Actual batch output, with planned dependencies before their consumers and shared products summed. */
    public static Map<Key, Integer> craftingOrder(Plan plan, Map<UUID, Recipe> graph) {
        if (!plan.valid()) return Map.of();
        Map<Key, Integer> result = new LinkedHashMap<>();
        Set<UUID> visited = new HashSet<>();
        for (UUID id : plan.batches().keySet()) appendCraftingStep(id, plan, graph, visited, result);
        return Collections.unmodifiableMap(result);
    }
    private static void appendCraftingStep(UUID id, Plan plan, Map<UUID, Recipe> graph, Set<UUID> visited, Map<Key, Integer> result) {
        Recipe recipe = graph.get(id);
        if (recipe == null || !plan.batches().containsKey(id) || !visited.add(id)) return;
        for (Input input : recipe.inputs()) appendCraftingStep(input.source(), plan, graph, visited, result);
        result.merge(recipe.output(), Math.multiplyExact(plan.batches().get(id), recipe.yield()), Math::addExact);
    }
    public Order submit(UUID owner, UUID root, int quantity) {
        Recipe recipe = port.recipes().get(root);
        if (quantity < 1 || quantity > 65536 || recipe == null || recipe.mode() != Mode.ORDERS) throw new IllegalArgumentException("invalid order");
        Map<UUID, Recipe> graph = graph(root, port.recipes());
        Plan plan = plan(root, quantity, graph, port.stock());
        if (!plan.valid()) throw new IllegalArgumentException(plan.error());
        Order order = new Order(UUID.randomUUID(), owner, root, quantity, graph);
        orders.add(order); port.dirty(); return order;
    }
    public void cancel(Order order) {
        if (!order.terminal()) { order.state = State.CANCELLED; order.reason = ""; port.dirty(); }
    }
    public void arrived(Key key, int amount) {
        for (Iterator<Pending> it = pending.iterator(); it.hasNext() && amount > 0;) {
            Pending p = it.next();
            if (!p.key.equals(key)) continue;
            int credited = Math.min(amount, p.remaining);
            p.remaining -= credited; amount -= credited;
            for (Order o : orders) if (o.id.equals(p.order) && o.root.equals(p.gauge) && !o.terminal()) o.received += credited;
            if (p.remaining == 0) it.remove();
            port.dirty();
        }
    }
    public Map<Key, Integer> freeStock() {
        Map<Key, Integer> free = new HashMap<>(port.stock());
        for (Order o : orders) if (!o.terminal())
            subtract(free, o.recipe().output(), Math.min(o.quantity, o.received));
        return free;
    }
    private static void subtract(Map<Key, Integer> stock, Key key, int quantity) {
        stock.compute(key, (k, v) -> Math.max(0, (v == null ? 0 : v) - quantity));
    }
    public void tick() {
        Map<Key, Integer> free = freeStock();
        Map<Key, Integer> forecast = new HashMap<>(free);
        for (Pending p : pending) {
            Order owner = orders.stream().filter(o -> o.id.equals(p.order)).findFirst().orElse(null);
            if (owner != null && !owner.terminal() && !owner.root.equals(p.gauge))
                forecast.merge(p.key, p.remaining, Math::addExact);
        }
        for (Order o : orders) {
            if (o.terminal() || o.state == State.SUSPENDED) continue;
            boolean unavailable = false;
            for (Recipe r : o.graph.values()) {
                Recipe live = port.recipes().get(r.id());
                if (port.invalid(r.id()) || (live != null && !live.equals(r))) {
                    o.state = State.SUSPENDED;
                    o.reason = "recipe_changed"; port.dirty(); unavailable = true; break;
                }
                if (!port.loaded(r.id())) { o.reason = "unloaded"; unavailable = true; }
            }
            if (unavailable) continue;
            if (o.received >= o.quantity) {
                // Arrival is the acknowledgement. The factory's own return route owns delivery.
                o.state = State.COMPLETED; o.reason = ""; port.dirty();
                continue;
            }
            int needed = Math.max(0, o.quantity - o.sent);
            if (needed == 0) { o.state = State.RETURNING; o.reason = ""; continue; }
            Plan plan = plan(o.root, needed, o.graph, forecast);
            if (!plan.valid()) { o.state = State.SUSPENDED; o.reason = plan.error().startsWith("cycle:") ? "invalid_chain" : plan.error(); port.dirty(); continue; }
            boolean dispatched = false;
            Map<Key, Integer> budget = new HashMap<>(free);
            for (UUID id : plan.batches().keySet()) {
                Recipe r = o.graph.get(id);
                if (pending.stream().anyMatch(p -> p.gauge.equals(id))) continue;
                Map<Key, Integer> inputs = new HashMap<>();
                for (Input i : r.inputs()) inputs.merge(i.key(), i.amount(), Math::addExact);
                if (inputs.entrySet().stream().anyMatch(e -> budget.getOrDefault(e.getKey(), 0) < e.getValue())) continue;
                o.dispatching = true;
                try {
                    port.checkpoint();
                    if (!port.dispatch(r)) { o.dispatching = false; port.checkpoint(); continue; }
                    for (var e : inputs.entrySet()) subtract(budget, e.getKey(), e.getValue());
                    pending.add(new Pending(o.id, id, r.output(), r.yield()));
                    if (id.equals(o.root)) o.sent = Math.addExact(o.sent, r.yield());
                    o.dispatching = false; dispatched = true; port.checkpoint();
                }
                catch (RuntimeException e) {
                    o.dispatching = true; o.state = State.SUSPENDED; o.reason = "dispatch_uncertain"; port.failed(o.id, e); port.dirty(); break;
                }
            }
            // Reserve the earlier order's existing components, including while waiting for raw materials.
            for (var e : plan.used().entrySet()) subtract(free, e.getKey(), e.getValue());
            forecast = new HashMap<>(plan.remaining());
            if (o.state != State.SUSPENDED) {
                o.state = dispatched ? State.PRODUCING : pending.stream().anyMatch(p -> p.order.equals(o.id)) ? State.RETURNING : State.MATERIALS;
                o.reason = plan.missing().isEmpty() ? "" : "missing_materials";
            }
        }
    }
}
