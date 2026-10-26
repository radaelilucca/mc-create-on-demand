package com.radaeli.createondemand.forge1201.demand;

import com.google.common.collect.Multimap;
import com.radaeli.createondemand.demand.*;
import com.radaeli.createondemand.demand.DemandEngine.*;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** World-owned queue. Never loads chunks or manufactures inventory entries. */
public final class DemandService extends SavedData implements DemandEngine.Port {
    private static final String NAME = "create_on_demand_orders";
    private static final Map<MinecraftServer, DemandService> ACTIVE = new WeakHashMap<>();
    public final DemandEngine engine = new DemandEngine(this);
    private MinecraftServer server;
    private final Map<UUID, Ref> refs = new LinkedHashMap<>();
    private final Map<UUID, Recipe> recipes = new LinkedHashMap<>();
    private final Map<UUID, FactoryPanelBehaviour> loaded = new HashMap<>();
    private final Set<UUID> invalid = new HashSet<>();
    private final Map<Key, ItemStack> stacks = new HashMap<>();
    private final Map<UUID, Long> lastDispatch = new HashMap<>();
    private record Ref(String dimension, long pos, String slot) {}
    private record Session(net.minecraft.world.inventory.AbstractContainerMenu menu, Set<Key> highlights) {}
    private final Map<UUID, Set<Key>> unseen = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    public static DemandService get(MinecraftServer server) {
        DemandService data = ACTIVE.get(server);
        if (data == null) {
            data = server.overworld().getDataStorage().computeIfAbsent(
                    DemandService::load, () -> importPreviousOrders(server), NAME);
            data.server = server; ACTIVE.put(server, data);
        }
        return data;
    }
    /** Adopt the existing demand queue once, without editing the previous mod's saved data. */
    private static DemandService importPreviousOrders(MinecraftServer server) {
        java.nio.file.Path directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
        java.nio.file.Path previous = directory.resolve("larger_recipe_gauge_orders.dat");
        if (java.nio.file.Files.exists(directory.resolve(NAME + ".dat")) || !java.nio.file.Files.isRegularFile(previous)) return new DemandService();
        try {
            CompoundTag envelope = NbtIo.readCompressed(previous.toFile());
            DemandService data = load(envelope.getCompound("data")); data.setDirty();
            org.slf4j.LoggerFactory.getLogger(DemandService.class).info("Imported existing on-demand orders into {}", NAME);
            return data;
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException("Could not import the existing on-demand queue", e); }
    }
    public static void stop(MinecraftServer server) { ACTIVE.remove(server); }
    public static void register(FactoryPanelBehaviour gauge) {
        if (!(gauge.getWorld() instanceof ServerLevel level) || !gauge.isActive()) return;
        DemandService data = get(level.getServer());
        UUID id = ((DemandGauge) gauge).demandId();
        Ref ref = new Ref(level.dimension().location().toString(), gauge.getPos().asLong(), gauge.slot.name());
        Ref old = data.refs.put(id, ref);
        if (!ref.equals(old)) data.setDirty();
    }
    public void refresh() {
        loaded.clear(); invalid.clear();
        for (var entry : refs.entrySet()) {
            Ref ref = entry.getValue();
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ref.dimension)));
            BlockPos pos = BlockPos.of(ref.pos);
            if (level == null || !level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof FactoryPanelBlockEntity be)) { invalid.add(entry.getKey()); continue; }
            FactoryPanelBehaviour gauge = be.panels.get(FactoryPanelBlock.PanelSlot.valueOf(ref.slot));
            if (gauge == null || !gauge.isActive() || !((DemandGauge) gauge).demandId().equals(entry.getKey())) {
                invalid.add(entry.getKey()); continue;
            }
            loaded.put(entry.getKey(), gauge);
        }
        for (var entry : List.copyOf(loaded.entrySet())) {
            FactoryPanelBehaviour gauge = entry.getValue();
            if (gauge.panelBE().restocker || gauge.getFilter().isEmpty() || gauge.recipeOutput < 1
                    || gauge.recipeAddress.isBlank() || gauge.targetedBy.isEmpty()) { recipes.remove(entry.getKey()); continue; }
            List<Input> inputs = new ArrayList<>(); boolean complete = true, broken = false;
            for (FactoryPanelConnection connection : gauge.targetedBy.values()) {
                if (!gauge.getWorld().hasChunkAt(connection.from.pos())) { complete = false; break; }
                FactoryPanelBehaviour source = FactoryPanelBehaviour.at(gauge.getWorld(), connection);
                if (source == null || !source.isActive() || source.getFilter().isEmpty() || connection.amount < 1) { complete = false; broken = true; break; }
                inputs.add(new Input(((DemandGauge) source).demandId(), key(source.network, source.getFilter()), connection.amount));
            }
            if (!complete) {
                if (broken) { recipes.remove(entry.getKey()); invalid.add(entry.getKey()); }
                else loaded.remove(entry.getKey());
                continue;
            }
            inputs.sort(Comparator.comparing(i -> i.source().toString()));
            String signature = gauge.recipeAddress + "|" + gauge.activeCraftingArrangement.stream().map(this::encode).toList();
            Recipe recipe = new Recipe(entry.getKey(), key(gauge.network, gauge.getFilter()), gauge.recipeOutput,
                    ((DemandGauge) gauge).demandMode(), inputs, signature);
            if (!recipe.equals(recipes.put(entry.getKey(), recipe))) setDirty();
        }
        for (UUID id : invalid) recipes.remove(id);
    }
    public void tick() {
        if (server.getTickCount() % 20 != 0) return;
        refresh();
        // Native Create gauges own stock maintenance in both modes. Only manual work is queued here.
        engine.tick();
        collectFinishedOrders();
        sessions.entrySet().removeIf(e -> {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            return player == null || player.containerMenu != e.getValue().menu();
        });
    }
    /** Hide finished work immediately; retain in-flight accounting until all dispatched items return. */
    public void collectFinishedOrders() {
        for (Order order : engine.orders) if (order.state == State.COMPLETED && !order.completionNotified) {
            unseen.computeIfAbsent(order.owner, owner -> new LinkedHashSet<>()).add(order.recipe().output());
            order.completionNotified = true; setDirty();
        }
        if (engine.orders.removeIf(o -> o.terminal() && engine.pending.stream().noneMatch(p -> p.order.equals(o.id)))) setDirty();
    }
    /** An opening consumes only this player's notices on this network, once per actual menu session. */
    public void openSession(ServerPlayer player, StockTickerBlockEntity ticker) {
        if (!access(player, ticker)) return;
        collectFinishedOrders();
        Session previous = sessions.get(player.getUUID());
        if (previous != null && previous.menu() == player.containerMenu) return;
        Set<Key> selected = new LinkedHashSet<>();
        Set<Key> pending = unseen.get(player.getUUID());
        if (pending != null) {
            for (Key key : pending) if (key.network().equals(ticker.behaviour.freqId)) selected.add(key);
            pending.removeAll(selected);
            if (pending.isEmpty()) unseen.remove(player.getUUID());
            if (!selected.isEmpty()) setDirty();
        }
        sessions.put(player.getUUID(), new Session(player.containerMenu, Set.copyOf(selected)));
    }
    public boolean cancel(ServerPlayer player, StockTickerBlockEntity ticker, UUID id) {
        if (!access(player, ticker)) return false;
        for (Order order : engine.orders) if (order.id.equals(id)
                && order.recipe().output().network().equals(ticker.behaviour.freqId)
                && (order.owner.equals(player.getUUID()) || ticker.behaviour.mayAdministrate(player))) {
            engine.cancel(order); collectFinishedOrders(); return true;
        }
        return false;
    }
    @Override public Map<UUID, Recipe> recipes() { return Collections.unmodifiableMap(recipes); }
    @Override public boolean loaded(UUID id) { return loaded.containsKey(id); }
    @Override public boolean invalid(UUID id) { return invalid.contains(id) || (loaded.containsKey(id) && !recipes.containsKey(id)); }
    @Override public void dirty() { setDirty(); }
    @Override public void checkpoint() {
        setDirty();
        save(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(NAME + ".dat").toFile());
    }
    /** NeoForge normally saves SavedData asynchronously; journal intents must reach disk before dispatch. */
    @Override public void save(java.io.File file) {
        if (!isDirty()) return;
        CompoundTag envelope = new CompoundTag(); envelope.put("data", save(new CompoundTag()));
        envelope.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        try {
            java.nio.file.Files.createDirectories(file.toPath().getParent());
            for (int attempt = 0; ; attempt++) {
                try { NbtIo.writeCompressed(envelope, file); break; }
                catch (java.nio.file.AccessDeniedException denied) {
                    // Windows may briefly lock the destination during an atomic replace. Keep the journal synchronous.
                    if (attempt == 3) throw denied;
                    try { Thread.sleep(10L << attempt); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException("Production journal save interrupted");
                    }
                }
            }
        }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException("Could not persist the production journal", e); }
        setDirty(false);
    }
    @Override public void failed(UUID order, RuntimeException error) {
        org.slf4j.LoggerFactory.getLogger(DemandService.class).error("Production order {} suspended after an uncertain dispatch", order, error);
    }
    public Key key(UUID network, ItemStack stack) {
        Key key = new Key(network, encode(stack)); stacks.putIfAbsent(key, stack.copyWithCount(1)); return key;
    }
    public String encode(ItemStack stack) { return stack.isEmpty() ? "{}" : stack.copyWithCount(1).save(new CompoundTag()).toString(); }
    public ItemStack stack(Key key) {
        return stacks.computeIfAbsent(key, k -> {
            try { return ItemStack.of(TagParser.parseTag(k.item())); }
            catch (Exception e) { return ItemStack.EMPTY; }
        }).copy();
    }
    @Override public Map<Key, Integer> stock() {
        Set<Key> keys = new HashSet<>();
        for (Recipe r : recipes.values()) { keys.add(r.output()); for (Input i : r.inputs()) keys.add(i.key()); }
        for (Order o : engine.orders) for (Recipe r : o.graph.values()) { keys.add(r.output()); for (Input i : r.inputs()) keys.add(i.key()); }
        Map<UUID, InventorySummary> summaries = new HashMap<>();
        Map<Key, Integer> result = new HashMap<>();
        for (Key key : keys) result.put(key, summaries.computeIfAbsent(key.network(), n -> LogisticsManager.getSummaryOfNetwork(n, true)).getCountOf(stack(key)));
        return result;
    }
    @Override public boolean dispatch(Recipe recipe) {
        FactoryPanelBehaviour gauge = loaded.get(recipe.id());
        if (gauge == null || gauge.redstonePowered || gauge.getUnloadedLinks() > 0 || gauge.getPromised() > 0
                || server.getTickCount() - lastDispatch.getOrDefault(recipe.id(), -100000L)
                    < com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.factoryGaugeTimer.get()) return false;
        Map<UUID, List<BigItemStack>> demands = new LinkedHashMap<>();
        for (Input i : recipe.inputs()) demands.computeIfAbsent(i.key().network(), n -> new ArrayList<>()).add(new BigItemStack(stack(i.key()), i.amount()));
        PackageOrderWithCrafts context = gauge.activeCraftingArrangement.isEmpty() ? PackageOrderWithCrafts.empty()
                : PackageOrderWithCrafts.singleRecipe(gauge.activeCraftingArrangement.stream().map(s -> new BigItemStack(s.copyWithCount(1))).toList());
        List<Multimap<PackagerBlockEntity, PackagingRequest>> batches = new ArrayList<>();
        for (var e : demands.entrySet()) {
            PackageOrderWithCrafts order = new PackageOrderWithCrafts(new PackageOrder(e.getValue()), context.orderedCrafts());
            Multimap<PackagerBlockEntity, PackagingRequest> requests = LogisticsManager.findPackagersForRequest(e.getKey(), order, null, gauge.recipeAddress);
            if (!complete(e.getValue(), requests) || requests.keySet().stream().anyMatch(p -> p.isTooBusyFor(RequestType.RESTOCK))) return false;
            batches.add(requests);
        }
        if (!physicalInputsAvailable(batches)) return false;
        for (var requests : batches) LogisticsManager.performPackageRequests(requests);
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(gauge.network);
        if (promises != null) promises.add(new RequestPromise(new BigItemStack(gauge.getFilter(), gauge.recipeOutput)));
        // Refresh native satisfied/promisedSatisfied flags before its next stock-maintenance tick.
        gauge.lazyTick();
        lastDispatch.put(recipe.id(), (long) server.getTickCount());
        return true;
    }
    @Override public int ship(Order order, int quantity) {
        UUID network = order.recipe().output().network();
        ItemStack item = stack(order.recipe().output());
        // Native dispatch stops after 100 boxes per Packager. Bound each group to at most
        // 64 boxes so large orders do not leave an unfinished native package group behind.
        int batchQuantity = (int) Math.min(quantity, item.getItem().canFitInsideContainerItems()
                ? 64L * 9 * item.getMaxStackSize() : 64L);
        List<BigItemStack> expected = List.of(new BigItemStack(item, batchQuantity));
        PackageOrderWithCrafts context = new PackageOrderWithCrafts(new PackageOrder(expected), List.of());
        Multimap<PackagerBlockEntity, PackagingRequest> requests =
                LogisticsManager.findPackagersForRequest(network, context, null, order.address);
        if (!complete(expected, requests)
                || requests.keySet().stream().anyMatch(p -> p.isTooBusyFor(RequestType.PLAYER))
                || !physicalInputsAvailable(List.of(requests))) return 0;
        // Create mutates the counts of these same request objects as packages are made.
        LogisticsManager.performPackageRequests(requests);
        int remaining = requests.values().stream().mapToInt(PackagingRequest::getCount).sum();
        LogisticsManager.ACCURATE_SUMMARIES.invalidateAll();
        LogisticsManager.SUMMARIES.invalidateAll();
        return batchQuantity - remaining;
    }
    /** Different networks can expose the same physical inventory. Preflight their combined withdrawals. */
    private static boolean physicalInputsAvailable(List<Multimap<PackagerBlockEntity, PackagingRequest>> batches) {
        Map<Object, List<PackagingRequest>> groups = new HashMap<>();
        Map<Object, IdentifiedInventory> inventories = new HashMap<>();
        for (var batch : batches) for (var e : batch.asMap().entrySet()) {
            IdentifiedInventory inv = e.getKey().targetInventory.getIdentifiedInventory();
            if (inv == null) return false;
            Object id = inv.identifier() == null ? inv.handler() : inv.identifier();
            inventories.put(id, inv); groups.computeIfAbsent(id, k -> new ArrayList<>()).addAll(e.getValue());
        }
        for (var e : groups.entrySet()) {
            var inventory = inventories.get(e.getKey()).handler();
            for (PackagingRequest request : e.getValue()) {
                long needed = e.getValue().stream().filter(r -> ItemStack.isSameItemSameTags(r.item(), request.item())).mapToLong(PackagingRequest::getCount).sum();
                long available = 0;
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    ItemStack item = inventory.getStackInSlot(slot);
                    if (ItemStack.isSameItemSameTags(item, request.item())) available += item.getCount();
                }
                if (available < needed) return false;
            }
        }
        return true;
    }
    private static boolean complete(List<BigItemStack> expected, Multimap<PackagerBlockEntity, PackagingRequest> requests) {
        for (BigItemStack item : expected) {
            int need = expected.stream().filter(i -> ItemStack.isSameItemSameTags(i.stack, item.stack)).mapToInt(i -> i.count).sum();
            int found = requests.values().stream().filter(r -> ItemStack.isSameItemSameTags(r.item(), item.stack)).mapToInt(PackagingRequest::getCount).sum();
            if (found < need) return false;
        }
        return !requests.isEmpty();
    }
    public static void arrival(RequestPromiseQueue queue, ItemStack item, int amount) {
        for (DemandService data : List.copyOf(ACTIVE.values())) {
            for (UUID network : Create.LOGISTICS.logisticsNetworks.keySet())
                if (Create.LOGISTICS.getQueuedPromises(network) == queue) { data.engine.arrived(data.key(network, item), amount); return; }
        }
    }
    public boolean access(ServerPlayer player, StockTickerBlockEntity ticker) {
        // Create's server RequestMenuProvider returns a CategoryMenu with the REQUEST menu type.
        // The registered type and bound ticker identify the session; the implementation class does not.
        return player.containerMenu instanceof com.simibubi.create.foundation.gui.menu.MenuBase<?> menu
                && menu.getType() == com.simibubi.create.AllMenuTypes.STOCK_KEEPER_REQUEST.get() && menu.contentHolder == ticker
                && ticker.getLevel() == player.level() && player.distanceToSqr(ticker.getBlockPos().getCenter()) <= 64
                && ticker.isKeeperPresent() && ticker.behaviour.mayInteract(player);
    }
    public CompoundTag snapshot(ServerPlayer player, StockTickerBlockEntity ticker) {
        collectFinishedOrders();
        CompoundTag result = new CompoundTag(); ListTag catalog = new ListTag(), orders = new ListTag(), highlighted = new ListTag();
        UUID network = ticker.behaviour.freqId;
        for (Recipe r : recipes.values()) if (r.mode() == Mode.ORDERS && r.output().network().equals(network)) {
            CompoundTag tag = recipeTag(r); Ref ref = refs.get(r.id());
            tag.putString("Location", ref == null ? "" : BlockPos.of(ref.pos).toShortString()); catalog.add(tag);
        }
        for (Order o : engine.orders) if (!o.terminal() && o.recipe().output().network().equals(network)
                && (o.owner.equals(player.getUUID()) || ticker.behaviour.mayAdministrate(player))) {
            CompoundTag summary = orderTag(o); ListTag rootOnly = new ListTag(); rootOnly.add(recipeTag(o.recipe()));
            summary.put("Graph", rootOnly); orders.add(summary);
        }
        Session session = sessions.get(player.getUUID());
        if (session != null && session.menu() == player.containerMenu)
            for (Key key : session.highlights()) if (key.network().equals(network)) highlighted.add(keyTag(key));
        result.put("Catalog", catalog); result.put("Orders", orders); result.put("Highlights", highlighted); return result;
    }
    public CompoundTag preview(UUID root, int quantity) {
        CompoundTag tag = new CompoundTag();
        Map<UUID, Recipe> graph = DemandEngine.graph(root, recipes);
        Plan plan = DemandEngine.plan(root, quantity, graph, engine.freeStock());
        tag.putString("Error", plan.error()); tag.put("Used", countsTag(plan.used()));
        tag.put("Missing", countsTag(plan.missing())); tag.put("Producing", countsTag(plan.producing()));
        tag.put("ToCraft", countsTag(DemandEngine.craftingOrder(plan, graph)));
        if (plan.error().startsWith("cycle:")) {
            ListTag cycle = new ListTag();
            for (String id : plan.error().substring(6).split(",")) {
                Recipe r = graph.get(UUID.fromString(id)); if (r != null) cycle.add(keyTag(r.output()));
            }
            tag.put("Cycle", cycle); tag.putString("Error", "invalid_chain");
        }
        return tag;
    }
    private static ListTag countsTag(Map<Key, Integer> counts) {
        ListTag list = new ListTag(); counts.forEach((k, v) -> { CompoundTag t = keyTag(k); t.putInt("Count", v); list.add(t); }); return list;
    }
    public static CompoundTag keyTag(Key key) { CompoundTag t = new CompoundTag(); t.putUUID("Network", key.network()); t.putString("Item", key.item()); return t; }
    public static Key readKey(CompoundTag t) { return new Key(t.getUUID("Network"), t.getString("Item")); }
    public static CompoundTag recipeTag(Recipe r) {
        CompoundTag t = new CompoundTag(); t.putUUID("Id", r.id()); t.put("Output", keyTag(r.output())); t.putInt("Yield", r.yield());
        t.putString("Mode", r.mode().name()); t.putString("Signature", r.signature()); ListTag inputs = new ListTag();
        for (Input i : r.inputs()) { CompoundTag a = keyTag(i.key()); a.putUUID("Source", i.source()); a.putInt("Count", i.amount()); inputs.add(a); }
        t.put("Inputs", inputs); return t;
    }
    public static Recipe readRecipe(CompoundTag t) {
        List<Input> inputs = new ArrayList<>(); for (Tag v : t.getList("Inputs", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) v; inputs.add(new Input(a.getUUID("Source"), readKey(a), a.getInt("Count")));
        }
        return new Recipe(t.getUUID("Id"), readKey(t.getCompound("Output")), t.getInt("Yield"), Mode.fromSaved(t.getString("Mode")), inputs, t.getString("Signature"));
    }
    public static CompoundTag orderTag(Order o) {
        CompoundTag t = new CompoundTag(); t.putUUID("Id", o.id); t.putUUID("Owner", o.owner); t.putUUID("Root", o.root);
        t.putInt("Quantity", o.quantity); t.putString("Address", o.address); t.putInt("Packaged", o.packaged);
        t.putString("State", o.state.name()); t.putString("Reason", o.reason); t.putInt("Sent", o.sent); t.putInt("Received", o.received);
        t.putBoolean("Dispatching", o.dispatching); t.putBoolean("CompletionNotified", o.completionNotified);
        ListTag graph = new ListTag(); o.graph.values().forEach(r -> graph.add(recipeTag(r))); t.put("Graph", graph); return t;
    }
    private static Order readOrder(CompoundTag t) {
        Map<UUID, Recipe> graph = new LinkedHashMap<>(); for (Tag v : t.getList("Graph", Tag.TAG_COMPOUND)) {
            Recipe r = readRecipe((CompoundTag) v); graph.put(r.id(), r);
        }
        Order o = new Order(t.getUUID("Id"), t.getUUID("Owner"), t.getUUID("Root"), t.getInt("Quantity"), graph, t.getString("Address"));
        o.packaged = t.getInt("Packaged");
        o.completionNotified = t.getBoolean("CompletionNotified");
        o.state = State.valueOf(t.getString("State")); o.reason = t.getString("Reason"); o.sent = t.getInt("Sent"); o.received = t.getInt("Received");
        // A removed producer mode requires review; never silently resume its old chain.
        for (Tag value : t.getList("Graph", Tag.TAG_COMPOUND))
            if (((CompoundTag) value).getString("Mode").equals("DEMAND") && !o.terminal()) {
                o.state = State.SUSPENDED; o.reason = "recipe_changed";
            }
        if (t.getBoolean("Shipping") || t.getBoolean("Dispatching")) { o.state = State.SUSPENDED; o.reason = "dispatch_uncertain"; } return o;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", 5); ListTag rs = new ListTag(), os = new ListTag(), ps = new ListTag();
        refs.forEach((id, ref) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("Id", id); t.putString("Dimension", ref.dimension); t.putLong("Pos", ref.pos); t.putString("Slot", ref.slot);
            if (recipes.containsKey(id)) t.put("Recipe", recipeTag(recipes.get(id))); rs.add(t);
        });
        engine.orders.forEach(o -> os.add(orderTag(o)));
        for (Pending p : engine.pending) { CompoundTag t = keyTag(p.key); t.putUUID("Order", p.order); t.putUUID("Gauge", p.gauge); t.putInt("Remaining", p.remaining); ps.add(t); }
        ListTag notices = new ListTag();
        unseen.forEach((owner, keys) -> keys.forEach(key -> { CompoundTag notice = keyTag(key); notice.putUUID("Owner", owner); notices.add(notice); }));
        tag.put("Refs", rs); tag.put("Orders", os); tag.put("Pending", ps); tag.put("Unseen", notices); return tag;
    }
    public static DemandService load(CompoundTag tag) {
        DemandService data = new DemandService();
        for (Tag v : tag.getList("Refs", Tag.TAG_COMPOUND)) { CompoundTag t = (CompoundTag) v; UUID id = t.getUUID("Id");
            data.refs.put(id, new Ref(t.getString("Dimension"), t.getLong("Pos"), t.getString("Slot")));
            if (t.contains("Recipe")) data.recipes.put(id, readRecipe(t.getCompound("Recipe")));
        }
        // Retire legacy background orders, but keep their in-flight accounting until arrival.
        for (Tag v : tag.getList("Orders", Tag.TAG_COMPOUND)) {
            CompoundTag order = (CompoundTag) v;
            if (!order.getBoolean("Internal")) data.engine.orders.add(readOrder(order));
        }
        for (Tag v : tag.getList("Pending", Tag.TAG_COMPOUND)) { CompoundTag t = (CompoundTag) v;
            data.engine.pending.add(new Pending(t.getUUID("Order"), t.getUUID("Gauge"), readKey(t), t.getInt("Remaining")));
        }
        for (Tag v : tag.getList("Unseen", Tag.TAG_COMPOUND)) {
            CompoundTag notice = (CompoundTag) v;
            data.unseen.computeIfAbsent(notice.getUUID("Owner"), owner -> new LinkedHashSet<>()).add(readKey(notice));
        }
        if (tag.getInt("Version") < 5) data.setDirty();
        return data;
    }
}
