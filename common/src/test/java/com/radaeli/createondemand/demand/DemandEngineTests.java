package com.radaeli.createondemand.demand;

import com.radaeli.createondemand.demand.DemandEngine.*;
import java.util.*;

/** Behavioral checks run without Minecraft, identical on Java 17 and Java 21. */
public final class DemandEngineTests {
    private static final UUID NETWORK = UUID.randomUUID(), OWNER = UUID.randomUUID();
    private static final UUID LOG = UUID.randomUUID(), PLANK = UUID.randomUUID(), SLAB = UUID.randomUUID(), BARREL = UUID.randomUUID();
    private static final Key LOG_KEY = new Key(NETWORK, "log"), PLANK_KEY = new Key(NETWORK, "plank"),
            SLAB_KEY = new Key(NETWORK, "slab"), BARREL_KEY = new Key(NETWORK, "barrel");
    private static Recipe recipe(UUID id, Key output, int yield, Input... inputs) {
        return new Recipe(id, output, yield, Mode.ORDERS, List.of(inputs), "address");
    }
    private static final class World implements Port {
        final Map<Key, Integer> stock = new HashMap<>();
        final Map<UUID, Recipe> recipes = new HashMap<>();
        final List<Recipe> dispatched = new ArrayList<>();
        final Set<UUID> unloaded = new HashSet<>();
        int checkpoints, failCheckpointAt = -1;
        DemandEngine engine = new DemandEngine(this);
        World() {
            recipes.put(PLANK, recipe(PLANK, PLANK_KEY, 4, new Input(LOG, LOG_KEY, 1)));
            recipes.put(SLAB, recipe(SLAB, SLAB_KEY, 6, new Input(PLANK, PLANK_KEY, 3)));
            recipes.put(BARREL, recipe(BARREL, BARREL_KEY, 1, new Input(PLANK, PLANK_KEY, 6), new Input(SLAB, SLAB_KEY, 2)));
        }
        public Map<Key, Integer> stock() { return new HashMap<>(stock); }
        public Map<UUID, Recipe> recipes() { return recipes; }
        public boolean loaded(UUID id) { return !unloaded.contains(id); }
        public boolean invalid(UUID id) { return false; }
        public void dirty() {}
        public void checkpoint() { if (++checkpoints == failCheckpointAt) throw new IllegalStateException("journal write interrupted"); }
        public boolean dispatch(Recipe r) {
            Map<Key, Integer> total = new HashMap<>(); for (Input i : r.inputs()) total.merge(i.key(), i.amount(), Integer::sum);
            check(total.entrySet().stream().allMatch(e -> stock.getOrDefault(e.getKey(), 0) >= e.getValue()), "No virtual ingredients");
            total.forEach((k, v) -> stock.merge(k, -v, Integer::sum)); dispatched.add(r); return true;
        }
        void returns() {
            for (Recipe r : List.copyOf(dispatched)) { stock.merge(r.output(), r.yield(), Integer::sum); engine.arrived(r.output(), r.yield()); }
            dispatched.clear();
        }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        World optOut = new World();
        Recipe component = optOut.recipes.get(PLANK);
        optOut.recipes.put(PLANK, new Recipe(component.id(), component.output(), component.yield(), Mode.STOCK,
                component.inputs(), component.signature()));
        Map<UUID, Recipe> enabledChain = DemandEngine.graph(BARREL, optOut.recipes);
        check(!enabledChain.containsKey(PLANK), "Native stock-only producers are not scheduled by manual orders");
        Plan requiresStock = DemandEngine.plan(BARREL, 1, enabledChain, Map.of());
        check(requiresStock.valid() && requiresStock.missing().containsKey(PLANK_KEY),
                "A disabled component producer leaves its material waiting for native replenishment");
        boolean rejected = false;
        try { optOut.engine.submit(OWNER, PLANK, 4); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Stock-only gauges reject manual crafting");
        check(Mode.fromSaved("DEMAND") == Mode.STOCK && Mode.fromSaved("ORDERS") == Mode.ORDERS,
                "Saved passive mode opts out; saved order mode retains explicit opt-in");
        check(Mode.fromSaved("") == Mode.ORDERS && Mode.fromSaved("STOCK") == Mode.STOCK && Mode.fromSaved("unknown") == Mode.STOCK,
                "Unconfigured gauges accept orders while saved opt-outs and unknown modes remain stock-only");

        World world = new World(); world.stock.put(LOG_KEY, 3);
        Plan p = DemandEngine.plan(BARREL, 1, world.recipes, world.stock);
        check(p.valid() && p.missing().isEmpty(), "A whole chain is craftable from logs");
        check(p.batches().get(PLANK) == 3 && p.batches().get(SLAB) == 1, "Batch yields and shared plank demand");
        Map<Key, Integer> crafting = DemandEngine.craftingOrder(p, world.recipes);
        check(new ArrayList<>(crafting.keySet()).equals(List.of(PLANK_KEY, SLAB_KEY, BARREL_KEY)),
                "Crafting preview lists connected dependencies before their consumers and the final product");
        check(crafting.get(PLANK_KEY) == 12 && crafting.get(SLAB_KEY) == 6 && crafting.get(BARREL_KEY) == 1,
                "Preview combines shared component demand using actual batch output");
        World reordered = new World();
        reordered.recipes.put(BARREL, recipe(BARREL, BARREL_KEY, 1, new Input(SLAB, SLAB_KEY, 2), new Input(PLANK, PLANK_KEY, 6)));
        Plan reversed = DemandEngine.plan(BARREL, 1, reordered.recipes, Map.of(PLANK_KEY, 3, LOG_KEY, 2));
        check(new ArrayList<>(reversed.batches().keySet()).equals(List.of(SLAB, PLANK, BARREL)), "Fixture discovers a consumer before its replenished component");
        check(new ArrayList<>(DemandEngine.craftingOrder(reversed, reordered.recipes).keySet()).equals(List.of(PLANK_KEY, SLAB_KEY, BARREL_KEY)),
                "Connection order wins over batch discovery order when existing stock covers an earlier branch");
        Plan stocked = DemandEngine.plan(BARREL, 1, world.recipes, Map.of(PLANK_KEY, 6, SLAB_KEY, 2));
        check(DemandEngine.craftingOrder(stocked, world.recipes).equals(Map.of(BARREL_KEY, 1)),
                "Stored components and raw materials are not listed as new crafting");
        Plan rounded = DemandEngine.plan(PLANK, 5, world.recipes, Map.of(LOG_KEY, 2));
        check(DemandEngine.craftingOrder(rounded, world.recipes).equals(Map.of(PLANK_KEY, 8)),
                "Crafting preview shows rounded output instead of silently dropping batch surplus");
        Order barrel = world.engine.submit(OWNER, BARREL, 1);
        for (int t = 0; t < 20 && !barrel.terminal(); t++) { world.engine.tick(); world.returns(); }
        check(barrel.state == State.COMPLETED && world.stock.getOrDefault(BARREL_KEY, 0) == 1, "Chain completes when the final item returns and stays in network storage");
        check(world.stock.getOrDefault(PLANK_KEY, 0) == 3 && world.stock.getOrDefault(SLAB_KEY, 0) == 4, "Surplus stays in storage");

        World existing = new World(); existing.stock.put(PLANK_KEY, 100); existing.stock.put(LOG_KEY, 2);
        Order forced = existing.engine.submit(OWNER, PLANK, 5);
        for (int t = 0; t < 10 && !forced.terminal(); t++) { existing.engine.tick(); existing.returns(); }
        check(forced.sent == 8 && existing.stock.get(PLANK_KEY) == 108, "Force new root production and round batch yields");

        World fifo = new World(); fifo.stock.put(LOG_KEY, 1);
        Order first = fifo.engine.submit(OWNER, PLANK, 4);
        Order second = fifo.engine.submit(OWNER, PLANK, 4);
        fifo.engine.tick(); check(first.sent == 4 && second.sent == 0, "One log cannot fund two requests");
        fifo.returns(); fifo.engine.tick(); check(first.state == State.COMPLETED && second.sent == 0, "FIFO credit and exclusive dispatch");
        fifo.stock.put(LOG_KEY, 1); fifo.engine.tick(); fifo.returns(); fifo.engine.tick();
        check(second.state == State.COMPLETED && fifo.stock.getOrDefault(PLANK_KEY, 0) == 8, "Waiting order resumes after replenishment");

        World unaddressed = new World(); unaddressed.stock.put(LOG_KEY, 1);
        Order unnamed = unaddressed.engine.submit(OWNER, PLANK, 4);
        for (int t = 0; t < 10 && !unnamed.terminal(); t++) { unaddressed.engine.tick(); unaddressed.returns(); }
        check(unnamed.state == State.COMPLETED && unaddressed.stock.getOrDefault(PLANK_KEY, 0) == 4, "Crafting completes in the network without requiring a delivery address");
        World unaddressedWaiting = new World();
        Order waitingUnnamed = unaddressedWaiting.engine.submit(OWNER, PLANK, 4);
        unaddressedWaiting.engine.tick();
        check(waitingUnnamed.state == State.MATERIALS && waitingUnnamed.sent == 0, "An empty address does not prevent queuing while materials are missing");
        unaddressedWaiting.stock.put(LOG_KEY, 1);
        for (int t = 0; t < 10 && !waitingUnnamed.terminal(); t++) { unaddressedWaiting.engine.tick(); unaddressedWaiting.returns(); }
        check(waitingUnnamed.state == State.COMPLETED, "An unaddressed order resumes after supplies arrive");

        World acknowledgement = new World(); acknowledgement.stock.put(LOG_KEY, 1);
        Order returning = acknowledgement.engine.submit(OWNER, PLANK, 4);
        acknowledgement.engine.tick(); acknowledgement.engine.tick();
        check(!returning.terminal() && returning.received == 0, "Dispatched ingredients alone do not acknowledge production");
        acknowledgement.engine.arrived(new Key(UUID.randomUUID(), PLANK_KEY.item()), 4);
        check(returning.received == 0, "A product arriving in a different network cannot acknowledge this order");
        acknowledgement.stock.put(PLANK_KEY, 2); acknowledgement.engine.arrived(PLANK_KEY, 2); acknowledgement.engine.tick();
        check(!returning.terminal() && returning.received == 2, "Partial returns keep the order waiting");
        acknowledgement.stock.put(PLANK_KEY, 4); acknowledgement.engine.arrived(PLANK_KEY, 2); acknowledgement.engine.tick();
        acknowledgement.engine.tick();
        check(returning.state == State.COMPLETED && acknowledgement.stock.get(PLANK_KEY) == 4,
                "The full return is acknowledged without extracting the result or dispatching again");

        World cancel = new World(); cancel.stock.put(LOG_KEY, 1);
        Order cancelled = cancel.engine.submit(OWNER, PLANK, 4);
        cancel.engine.tick(); cancel.engine.cancel(cancelled); cancel.returns(); cancel.engine.tick();
        check(cancelled.state == State.CANCELLED && cancel.stock.get(PLANK_KEY) == 4 && cancel.engine.pending.isEmpty(), "Cancellation keeps already dispatched output in storage");

        World mutation = new World(); Order stale = mutation.engine.submit(OWNER, BARREL, 1);
        mutation.recipes.put(PLANK, recipe(PLANK, PLANK_KEY, 8, new Input(LOG, LOG_KEY, 1)));
        mutation.engine.tick(); check(stale.state == State.SUSPENDED, "Recipe edits suspend snapshots");
        World chunks = new World(); chunks.stock.put(LOG_KEY, 3); Order off = chunks.engine.submit(OWNER, BARREL, 1);
        chunks.unloaded.add(PLANK); chunks.engine.tick(); check(off.sent == 0 && off.reason.equals("unloaded"), "No chunk forcing");

        World cycle = new World(); cycle.recipes.put(PLANK, recipe(PLANK, PLANK_KEY, 1, new Input(SLAB, SLAB_KEY, 1)));
        check(!DemandEngine.plan(PLANK, 1, cycle.recipes, Map.of()).valid(), "Cycle detected without stack overflow");
        Plan missing = DemandEngine.plan(BARREL, 1, new World().recipes, Map.of());
        check(missing.missing().get(LOG_KEY) == 3, "Missing raw materials reported");

        World sharing = new World(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Key aKey = new Key(NETWORK, "a"), bKey = new Key(NETWORK, "b");
        sharing.recipes.put(a, recipe(a, aKey, 1, new Input(PLANK, PLANK_KEY, 3)));
        sharing.recipes.put(b, recipe(b, bKey, 1, new Input(PLANK, PLANK_KEY, 1)));
        sharing.stock.put(LOG_KEY, 1);
        Order aOrder = sharing.engine.submit(OWNER, a, 1), bOrder = sharing.engine.submit(OWNER, b, 1);
        for (int t = 0; t < 10; t++) { sharing.engine.tick(); sharing.returns(); }
        check(aOrder.state == State.COMPLETED && bOrder.state == State.COMPLETED, "Concurrent chains share component batch surplus");
        check(sharing.stock.getOrDefault(PLANK_KEY, 0) == 0, "The four produced components were not allocated twice");
        Recipe excessive = recipe(a, aKey, 1, new Input(PLANK, PLANK_KEY, Integer.MAX_VALUE));
        check(!DemandEngine.plan(a, 65536, Map.of(a, excessive), Map.of()).valid(), "Excessive counts are rejected without crashing the scheduler");
        World interrupted = new World(); interrupted.stock.put(LOG_KEY, 1); interrupted.failCheckpointAt = 2;
        Order uncertain = interrupted.engine.submit(OWNER, PLANK, 4);
        interrupted.engine.tick(); interrupted.engine.tick();
        check(uncertain.state == State.SUSPENDED && uncertain.dispatching && uncertain.sent == 4,
                "A journal failure after dispatch suspends the order with its intent preserved");
        check(interrupted.dispatched.size() == 1 && interrupted.engine.pending.size() == 1,
                "An uncertain dispatch is never repeated automatically");
        System.out.println("DemandEngine: all behavioral scenarios passed");
    }
}
