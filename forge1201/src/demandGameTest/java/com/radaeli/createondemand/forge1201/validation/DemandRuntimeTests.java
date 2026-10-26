package com.radaeli.createondemand.forge1201.validation;

import com.radaeli.createondemand.demand.*;
import com.radaeli.createondemand.demand.DemandEngine.*;
import com.radaeli.createondemand.forge1201.demand.DemandService;
import com.radaeli.createondemand.forge1201.demand.DemandCommands;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.*;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("create_on_demand")
@PrefixGameTestTemplate(false)
public final class DemandRuntimeTests {
    @GameTest(template = "demand_empty", batch = "demand", timeoutTicks = 600)
    public static void physicalCraftAndReturn(GameTestHelper helper) {
        production(helper, false, 3);
    }
    @GameTest(template = "demand_empty", batch = "demand_chain", timeoutTicks = 1000)
    public static void physicalIngredientChain(GameTestHelper helper) {
        production(helper, true, 1);
    }
    @GameTest(template = "demand_empty", batch = "demand", timeoutTicks = 600)
    public static void physicalCraftRemainsInNetwork(GameTestHelper helper) {
        production(helper, false, 1);
    }
    @GameTest(template = "empty", batch = "native_stock", timeoutTicks = 1800)
    public static void maintainStockUsesOriginalSlider(GameTestHelper helper) {
        production(helper, false, 1, Mode.STOCK);
    }
    @GameTest(template = "empty", batch = "native_stock_orders", timeoutTicks = 2200)
    public static void acceptOrdersKeepsOriginalSlider(GameTestHelper helper) {
        production(helper, false, 1, Mode.ORDERS);
    }
    @GameTest(template = "empty", batch = "native_stock_overlap", timeoutTicks = 2200)
    public static void ordersWaitForNativePromises(GameTestHelper helper) {
        production(helper, false, 1, Mode.ORDERS, true);
    }
    @GameTest(template = "demand_empty", batch = "addressed_production", timeoutTicks = 1000)
    public static void addressedProductionShipsExactQuantity(GameTestHelper helper) {
        production(helper, false, 1, null, false, "Home");
    }
    @GameTest(template = "demand_empty", batch = "addressed_chain", timeoutTicks = 1000)
    public static void addressedChainShipsOnlyFinalProduct(GameTestHelper helper) {
        production(helper, true, 1, null, false, "Workshop");
    }
    private static void production(GameTestHelper helper, boolean chain, int dimension) {
        production(helper, chain, dimension, null);
    }
    private static void production(GameTestHelper helper, boolean chain, int dimension, Mode stockMode) {
        production(helper, chain, dimension, stockMode, false);
    }
    private static void production(GameTestHelper helper, boolean chain, int dimension, Mode stockMode, boolean queueEarly) {
        production(helper, chain, dimension, stockMode, queueEarly, "");
    }
    private static void production(GameTestHelper helper, boolean chain, int dimension, Mode stockMode, boolean queueEarly, String address) {
        var level = helper.getLevel(); UUID network = UUID.randomUUID();
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos sourcePos = base, sourceBox = base.north(), crafterPos = base.east(4);
        BlockPos resultPos = crafterPos.relative(Direction.WEST), resultBox = resultPos.south(), receivingBox = crafterPos.south();
        level.setBlockAndUpdate(sourcePos, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(sourcePos)).setItem(0, new ItemStack(Items.OAK_LOG, stockMode == null ? (!address.isEmpty() && !chain ? 2 : 1) : 6));
        PackagerBlockEntity source = packager(helper, sourceBox, Direction.NORTH);
        link(helper, sourceBox.above(), network);
        level.setBlockAndUpdate(resultPos, Blocks.CHEST.defaultBlockState());
        PackagerBlockEntity result = packager(helper, resultBox, Direction.SOUTH);
        link(helper, resultBox.above(), network);
        List<MechanicalCrafterBlockEntity> array = new ArrayList<>();
        for (int row = 0; row < dimension; row++) for (int col = 0; col < dimension; col++) {
            BlockPos p = crafterPos.offset(col, row, 0);
            level.setBlockAndUpdate(p, AllBlocks.MECHANICAL_CRAFTER.getDefaultState()
                    .setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.NORTH)
                    .setValue(MechanicalCrafterBlock.POINTING, col == 0 && row > 0 ? Pointing.DOWN : Pointing.RIGHT));
            array.add((MechanicalCrafterBlockEntity) level.getBlockEntity(p));
        }
        MechanicalCrafterBlockEntity crafter = (MechanicalCrafterBlockEntity) level.getBlockEntity(crafterPos);
        for (int row = 0; row < dimension; row++) for (int col = 0; col < dimension; col++) {
            if (row == 0 && col == 0) continue;
            BlockPos p = crafterPos.offset(col, row, 0);
            if (!crafter.getInput().getInventories(level, crafterPos).contains(((MechanicalCrafterBlockEntity) level.getBlockEntity(p)).getInventory()))
                ConnectedInputHandler.toggleConnection(level, col > 0 ? p.west() : p.below(), p);
        }
        helper.assertTrue(crafter.getInput().getInventories(level, crafterPos).size() == dimension * dimension, "Wrench-connected array exposes all crafting cells");
        PackagerBlockEntity receiver = packager(helper, receivingBox, Direction.SOUTH);
        BlockPos secondPos = base.east(8), finalPos = secondPos.west();
        MechanicalCrafterBlockEntity second;
        PackagerBlockEntity secondReceiver, finalPackager;
        if (chain) {
            level.setBlockAndUpdate(secondPos, AllBlocks.MECHANICAL_CRAFTER.getDefaultState()
                    .setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.NORTH).setValue(MechanicalCrafterBlock.POINTING, Pointing.RIGHT));
            second = (MechanicalCrafterBlockEntity) level.getBlockEntity(secondPos);
            level.setBlockAndUpdate(finalPos, Blocks.CHEST.defaultBlockState());
            finalPackager = packager(helper, finalPos.south(), Direction.SOUTH); link(helper, finalPos.south().above(), network);
            secondReceiver = packager(helper, secondPos.south(), Direction.SOUTH);
        } else { second = null; secondReceiver = null; finalPackager = result; }
        BlockPos boardPos = base.south(3);
        level.setBlockAndUpdate(boardPos.south(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(boardPos, AllBlocks.FACTORY_GAUGE.getDefaultState()
                .setValue(FactoryPanelBlock.FACE, AttachFace.WALL).setValue(FactoryPanelBlock.FACING, Direction.NORTH));
        FactoryPanelBlockEntity board = (FactoryPanelBlockEntity) level.getBlockEntity(boardPos);
        var slots = FactoryPanelBlock.PanelSlot.values(); board.addPanel(slots[0], network); board.addPanel(slots[1], network);
        FactoryPanelBehaviour product = board.panels.get(slots[0]), ingredient = board.panels.get(slots[1]);
        ingredient.setFilter(new ItemStack(Items.OAK_LOG)); product.setFilter(new ItemStack(Items.OAK_PLANKS));
        product.recipeAddress = "craft"; product.recipeOutput = 4;
        List<ItemStack> pattern = new ArrayList<>(Collections.nCopies(dimension * dimension, ItemStack.EMPTY));
        pattern.set(0, new ItemStack(Items.OAK_LOG)); product.activeCraftingArrangement = pattern;
        product.targetedBy.put(ingredient.getPanelPosition(), new FactoryPanelConnection(ingredient.getPanelPosition(), 1));
        ingredient.targeting.add(product.getPanelPosition());
        product.upTo = true; product.count = 0;
        if (stockMode != null) product.setValueSettings(null,
                new com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings(0, 10), false);
        ((DemandGauge) product).demandMode(stockMode == null ? Mode.ORDERS : stockMode);
        FactoryPanelBehaviour root;
        if (chain) {
            board.addPanel(slots[2], network); root = board.panels.get(slots[2]);
            root.setFilter(new ItemStack(Items.OAK_BUTTON)); root.recipeOutput = 1; root.recipeAddress = "buttons";
            root.activeCraftingArrangement = List.of(new ItemStack(Items.OAK_PLANKS));
            root.targetedBy.put(product.getPanelPosition(), new FactoryPanelConnection(product.getPanelPosition(), 1));
            product.targeting.add(root.getPanelPosition()); ((DemandGauge) root).demandMode(Mode.ORDERS);
        } else root = product;
        DemandService service = DemandService.get(level.getServer());
        Order[] order = new Order[1]; boolean[] unpacked = {false}, depleted = {false};
        int[] settled = {0};
        helper.runAtTickTime(30, () -> {
            service.refresh();
            helper.assertTrue(service.recipes().containsKey(((DemandGauge) product).demandId()), "Recipe is discovered from the physical gauge");
            if (chain) {
                CompoundTag preview = service.preview(((DemandGauge) root).demandId(), 1);
                var steps = preview.getList("ToCraft", 10);
                helper.assertTrue(preview.getList("Missing", 10).isEmpty() && steps.size() == 2,
                        "Craftable chain exposes its actual production steps in the preview packet");
                helper.assertTrue(service.stack(DemandService.readKey(steps.getCompound(0))).is(Items.OAK_PLANKS)
                        && steps.getCompound(0).getInt("Count") == 4
                        && service.stack(DemandService.readKey(steps.getCompound(1))).is(Items.OAK_BUTTON)
                        && steps.getCompound(1).getInt("Count") == 1,
                        "Serialized preview keeps the component before the final product, including surplus");
            }
            if (stockMode == null || queueEarly)
                order[0] = service.engine.submit(UUID.randomUUID(), ((DemandGauge) root).demandId(), chain ? 1 : address.isEmpty() ? 4 : 5, address);
        });
        helper.onEachTick(() -> {
            helper.assertTrue(level.getBlockEntity(crafterPos) == crafter, "Crafter replaced before tick: " + level.getBlockState(crafterPos) + " current=" + level.getBlockEntity(crafterPos));
            // Adjacent crafter cogwheels must alternate direction or Create destroys the conflicting gears.
            for (int index = 0; index < array.size(); index++) array.get(index).setSpeed(((index / dimension + index % dimension) & 1) == 0 ? 64 : -64);
            if (second != null) second.setSpeed(64);
            if (!source.heldBox.isEmpty() && source.animationTicks == 0 && receiver.unwrapBox(source.heldBox, true)) {
                ItemStack box = source.inventory.extractItem(0, 1, false);
                if (!box.isEmpty()) {
                    helper.assertTrue(PackageItem.getAddress(box).equals("craft"), "Ingredients use the gauge address");
                    CompoundTag inputTag = new CompoundTag(); crafter.getInput().write(inputTag);
                    helper.assertTrue(receiver.unwrapBox(box, false), "The genuine package can be unpacked onto the crafter; array=" + dimension
                            + " inventories=" + crafter.getInput().getInventories(level, crafterPos).size()
                            + " sameEntity=" + (level.getBlockEntity(crafterPos) == crafter) + " block=" + level.getBlockState(crafterPos) + " current=" + level.getBlockEntity(crafterPos) + " input=" + inputTag);
                    unpacked[0] = true;
                }
            }
            if (chain && !result.heldBox.isEmpty() && result.animationTicks == 0) {
                ItemStack box = result.inventory.extractItem(0, 1, false);
                if (!box.isEmpty()) helper.assertTrue(secondReceiver.unwrapBox(box, false), "Intermediate product is unpacked into the second real crafter");
            }
            if (stockMode != null) {
                ChestBlockEntity storage = (ChestBlockEntity) level.getBlockEntity(resultPos);
                int count = 0;
                for (int slot = 0; slot < storage.getContainerSize(); slot++)
                    if (storage.getItem(slot).is(Items.OAK_PLANKS)) count += storage.getItem(slot).getCount();
                if (stockMode == Mode.ORDERS && order[0] == null && count >= 10) {
                    helper.assertTrue(count == 12, "Native maintenance rounds target ten to three batches of four");
                    service.refresh();
                    order[0] = service.engine.submit(UUID.randomUUID(), ((DemandGauge) product).demandId(), 4);
                }
                if (order[0] != null && order[0].state == State.COMPLETED && !depleted[0]) {
                    helper.assertTrue(order[0].sent == 4, "An automatic return cannot acknowledge an undispatched manual order");
                    if (queueEarly && count < 10) return;
                    helper.assertTrue(queueEarly ? count == 12 || count == 16 : count == 16,
                            "Manual production returns new output while native maintenance covers the minimum");
                    int remaining = 8;
                    for (int slot = 0; slot < storage.getContainerSize() && remaining > 0; slot++)
                        if (storage.getItem(slot).is(Items.OAK_PLANKS)) remaining -= storage.removeItem(slot, remaining).getCount();
                    depleted[0] = true; count -= 8;
                    LogisticsManager.SUMMARIES.invalidate(network);
                }
                if ((stockMode == Mode.STOCK || depleted[0]) && count >= 10) {
                    helper.assertTrue(count == 12, "Original slider is maintained, including after a manual order and withdrawal");
                    if (++settled[0] >= 40) {
                        int logs = ((ChestBlockEntity) level.getBlockEntity(sourcePos)).getItem(0).getCount();
                        helper.assertTrue(logs == (stockMode == Mode.STOCK ? 3 : 1), "No duplicate background or manual batches");
                        helper.assertTrue(service.engine.orders.isEmpty(), "Stock maintenance does not create service orders");
                        helper.succeed();
                    }
                } else settled[0] = 0;
                return;
            }
            if (!address.isEmpty() && order[0] != null && order[0].state == State.SHIPPED) {
                helper.assertTrue(unpacked[0], "Addressed production used the real crafting path");
                helper.assertTrue(!service.engine.orders.contains(order[0]), "Only fully packaged orders leave the queue");
                int packed = packagedCount(helper, finalPackager, address, chain ? Items.OAK_BUTTON : Items.OAK_PLANKS);
                helper.assertTrue(packed == (chain ? 1 : 5) && order[0].packaged == packed, "Exactly the requested final products are addressed");
                var storage = (ChestBlockEntity) level.getBlockEntity(chain ? finalPos : resultPos);
                int count = 0;
                for (int slot = 0; slot < storage.getContainerSize(); slot++)
                    if (storage.getItem(slot).is(chain ? Items.OAK_BUTTON : Items.OAK_PLANKS)) count += storage.getItem(slot).getCount();
                helper.assertTrue(count == (chain ? 0 : 3), "Rounded batch surplus stays in storage");
                helper.succeed();
            }
            if (order[0] != null && order[0].state == State.COMPLETED) {
                helper.assertTrue(unpacked[0], "Production used the package's crafting context");
                helper.assertTrue(!service.engine.orders.contains(order[0]), "The returned order is automatically cleared from the queue");
                helper.assertTrue(finalPackager.heldBox.isEmpty(), "Completion must not withdraw returned output into another package");
                var finalStorage = (ChestBlockEntity) level.getBlockEntity(chain ? finalPos : resultPos);
                int outputs = 0;
                for (int slot = 0; slot < finalStorage.getContainerSize(); slot++)
                    if (finalStorage.getItem(slot).is(chain ? Items.OAK_BUTTON : Items.OAK_PLANKS)) outputs += finalStorage.getItem(slot).getCount();
                helper.assertTrue(outputs == (chain ? 1 : 4), "The acknowledgement leaves the newly crafted items in the network storage");
                helper.assertTrue(((ChestBlockEntity) level.getBlockEntity(sourcePos)).getItem(0).isEmpty(), "Ingredients were physically consumed");
                helper.succeed();
            }
        });
        helper.runAtTickTime(chain ? 950 : 550, () -> {
            if (stockMode == null && order[0] != null && order[0].state != State.COMPLETED && order[0].state != State.SHIPPED) helper.fail("Production stalled: " + order[0].state + "/" + order[0].reason
                    + " sent=" + order[0].sent + " received=" + order[0].received + " unpacked=" + unpacked[0]
                    + " sourceBox=" + source.heldBox + " resultBox=" + result.heldBox
                    + " crafter=" + crafter.getInventory().getStackInSlot(0) + " target=" + crafter.getTargetDirection());
        });
    }
    private static int packagedCount(GameTestHelper helper, PackagerBlockEntity packager, String address, Item item) {
        List<ItemStack> boxes = new ArrayList<>();
        if (!packager.heldBox.isEmpty()) boxes.add(packager.heldBox);
        for (var queued : packager.queuedExitingPackages) {
            helper.assertTrue(queued.count == 1, "Each queued box is counted once"); boxes.add(queued.stack);
        }
        int count = 0;
        for (ItemStack box : boxes) {
            helper.assertTrue(PackageItem.getAddress(box).equals(address), "Every box uses the saved destination");
            var contents = PackageItem.getContents(box);
            for (int slot = 0; slot < contents.getSlots(); slot++) {
                ItemStack stack = contents.getStackInSlot(slot);
                helper.assertTrue(stack.isEmpty() || stack.is(item), "Packages contain only final products");
                count += stack.getCount();
            }
        }
        return count;
    }
    @GameTest(template = "demand_empty", batch = "addressed_packages", timeoutTicks = 240)
    public static void addressedMultiplePackagersWaitAndResume(GameTestHelper helper) {
        var level = helper.getLevel(); UUID network = UUID.randomUUID(), root = UUID.randomUUID();
        DemandService service = DemandService.get(level.getServer());
        Key output = service.key(network, new ItemStack(Items.OAK_PLANKS));
        Recipe recipe = new Recipe(root, output, 4, Mode.ORDERS,
                List.of(new Input(UUID.randomUUID(), service.key(network, new ItemStack(Items.OAK_LOG)), 1)), "test");
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), root, 1200, Map.of(root, recipe), "Large order");
        order.sent = order.received = 1200; service.engine.orders.add(order);
        List<PackagerBlockEntity> packagers = new ArrayList<>();
        List<ChestBlockEntity> chests = new ArrayList<>();
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(order.state == State.SHIPPING && order.packaged == 0,
                    "Fully returned output waits for storage and Packagers without producer chunks");
            for (int i = 0; i < 2; i++) {
                BlockPos pos = helper.absolutePos(new BlockPos(1 + i * 5, 2, 1));
                level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
                ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos); chests.add(chest);
                for (int slot = 0; slot < 11; slot++) chest.setItem(slot, new ItemStack(Items.OAK_PLANKS, 64));
                PackagerBlockEntity packager = packager(helper, pos.south(), Direction.SOUTH); packagers.add(packager);
                link(helper, pos.south().above(), network);
            }
            LogisticsManager.ACCURATE_SUMMARIES.invalidateAll(); LogisticsManager.SUMMARIES.invalidateAll();
        });
        helper.runAtTickTime(26, () -> {
            // Explicitly register links so this preflight test does not depend on their next lazy tick.
            for (PackagerBlockEntity packager : packagers) {
                var link = (PackagerLinkBlockEntity) level.getBlockEntity(packager.getBlockPos().above());
                LogisticallyLinkedBehaviour.keepAlive(link.behaviour);
            }
            LogisticsManager.ACCURATE_SUMMARIES.invalidateAll(); LogisticsManager.SUMMARIES.invalidateAll();
            PackagerBlockEntity busy = packagers.get(0);
            for (int i = 0; i < 51; i++) busy.queuedExitingPackages.add(new com.simibubi.create.content.logistics.BigItemStack(
                    PackageItem.containing(List.of(new ItemStack(Items.STONE))), 1));
            helper.assertTrue(service.ship(order, order.quantity) == 0, "A busy Packager prevents an incomplete multi-inventory shipment");
            helper.assertTrue(packagers.stream().allMatch(p -> p.heldBox.isEmpty()), "Busy preflight does not extract products");
            busy.queuedExitingPackages.clear();
            LogisticsManager.ACCURATE_SUMMARIES.invalidateAll(); LogisticsManager.SUMMARIES.invalidateAll();
        });
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(order.state == State.SHIPPED && order.packaged == 1200, "All requested products are packaged");
            int packed = packagers.stream().mapToInt(p -> packagedCount(helper, p, "Large order", Items.OAK_PLANKS)).sum();
            helper.assertTrue(packed == 1200 && packagers.stream().allMatch(p -> !p.heldBox.isEmpty()),
                    "Multiple real Packagers produce multiple addressed boxes");
            int stored = 0;
            for (ChestBlockEntity chest : chests) for (int slot = 0; slot < chest.getContainerSize(); slot++) stored += chest.getItem(slot).getCount();
            helper.assertTrue(stored == 208, "Only the requested count was removed across physical inventories");
            helper.assertTrue(!service.engine.orders.contains(order), "Fully packaged order is removed");
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "demand")
    public static void modeAndIdentityPersistence(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, AllBlocks.FACTORY_GAUGE.getDefaultState().setValue(FactoryPanelBlock.FACE, AttachFace.WALL).setValue(FactoryPanelBlock.FACING, Direction.NORTH));
        FactoryPanelBlockEntity be = (FactoryPanelBlockEntity) level.getBlockEntity(pos);
        var slot = FactoryPanelBlock.PanelSlot.values()[0]; be.addPanel(slot, UUID.randomUUID());
        DemandGauge g = (DemandGauge) be.panels.get(slot);
        helper.assertTrue(g.demandMode() == Mode.ORDERS, "A newly placed gauge accepts manual orders by default");
        UUID id = g.demandId();
        CompoundTag full = be.saveWithFullMetadata();
        FactoryPanelBlockEntity copy = (FactoryPanelBlockEntity) BlockEntity.loadStatic(pos, be.getBlockState(), full);
        copy.setLevel(level); DemandGauge restored = (DemandGauge) copy.panels.get(slot);
        helper.assertTrue(restored.demandId().equals(id) && restored.demandMode() == Mode.ORDERS, "Normal persistence preserves identity and mode");
        helper.assertTrue(Mode.values().length == 2, "Only stock and stock-with-orders are available");
        FactoryPanelBehaviour panel = be.panels.get(slot); panel.setValueSettings(null,
                new com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings(0, 10), false);
        CompoundTag legacy = new CompoundTag(); panel.write(legacy, false);
        legacy.getCompound(com.simibubi.create.foundation.utility.CreateLang.asId(slot.name())).putString("DemandMode", "DEMAND");
        panel.read(legacy, false);
        helper.assertTrue(g.demandMode() == Mode.STOCK && panel.getAmount() == 10,
                "Removed passive mode becomes native stock maintenance without changing the slider");
        CompoundTag savedSlot = legacy.getCompound(com.simibubi.create.foundation.utility.CreateLang.asId(slot.name()));
        savedSlot.putString("DemandMode", "STOCK");
        panel.read(legacy, false);
        helper.assertTrue(g.demandMode() == Mode.STOCK && panel.getAmount() == 10, "An explicit stock-only mode survives loading");
        savedSlot.remove("DemandMode");
        panel.read(legacy, false);
        helper.assertTrue(g.demandMode() == Mode.ORDERS && panel.getAmount() == 10, "A gauge with no saved mode uses orders without changing its stock target");
        g.demandMode(Mode.ORDERS);
        CompoundTag safe = new CompoundTag(); be.panels.get(slot).writeSafe(safe);
        helper.assertTrue(!safe.getCompound(com.simibubi.create.foundation.utility.CreateLang.asId(slot.name())).contains("DemandId"), "Schematic copies must not duplicate the production identity");
        BlockPos moved = pos.east(2); level.setBlockAndUpdate(moved.south(), Blocks.STONE.defaultBlockState());
        var fake = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DemandIdentity"));
        be.panels.get(slot).moveTo(new FactoryPanelPosition(moved, slot), fake);
        FactoryPanelBehaviour relocated = FactoryPanelBehaviour.at(level, new FactoryPanelPosition(moved, slot));
        helper.assertTrue(relocated != null && ((DemandGauge) relocated).demandId().equals(id) && ((DemandGauge) relocated).demandMode() == Mode.ORDERS,
                "Relocation preserves the production identity and mode");
        DemandService service = DemandService.get(level.getServer()); service.refresh();
        helper.assertTrue(service.loaded(id), "The order registry follows the relocated gauge immediately");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "demand_persistence")
    public static void queuePersistenceAndInterruptedDispatch(GameTestHelper helper) {
        DemandService service = DemandService.get(helper.getLevel().getServer());
        UUID root = UUID.randomUUID(), owner = UUID.randomUUID(), network = UUID.randomUUID();
        Key output = new Key(network, "output"), raw = new Key(network, "raw");
        Recipe r = new Recipe(root, output, 4, Mode.ORDERS, List.of(new Input(UUID.randomUUID(), raw, 1)), "test");
        Order o = new Order(UUID.randomUUID(), owner, root, 4, Map.of(root, r), "Saved destination");
        o.sent = 4; o.state = State.RETURNING; o.packaged = 2;
        service.engine.orders.add(o); service.engine.pending.add(new Pending(o.id, root, output, 4));
        CompoundTag saved = service.save(new CompoundTag());
        DemandService restored = DemandService.load(saved);
        Order copy = restored.engine.orders.stream().filter(a -> a.id.equals(o.id)).findFirst().orElseThrow();
        helper.assertTrue(copy.sent == 4 && copy.received == 0 && copy.owner.equals(owner) && copy.state == State.RETURNING,
                "A normal reload preserves owner, dispatched work and waiting state");
        helper.assertTrue(restored.engine.pending.stream().anyMatch(p -> p.order.equals(o.id) && p.remaining == 4), "In-flight production is persisted");
        helper.assertTrue(copy.address.equals("Saved destination") && copy.packaged == 2,
                "Queue persistence retains the immutable destination and packaging progress");
        CompoundTag old = saved.copy();
        CompoundTag oldOrder = old.getList("Orders", 10).stream().map(v -> (CompoundTag) v)
                .filter(v -> v.getUUID("Id").equals(o.id)).findFirst().orElseThrow();
        oldOrder.remove("Address"); oldOrder.remove("Packaged");
        DemandService oldData = DemandService.load(old);
        Order oldCopy = oldData.engine.orders.stream().filter(a -> a.id.equals(o.id)).findFirst().orElseThrow();
        helper.assertTrue(oldCopy.address.isEmpty() && oldCopy.packaged == 0, "Old saves remain unaddressed");
        o.dispatching = true;
        saved = service.save(new CompoundTag());
        restored = DemandService.load(saved);
        copy = restored.engine.orders.stream().filter(a -> a.id.equals(o.id)).findFirst().orElseThrow();
        helper.assertTrue(copy.state == State.SUSPENDED && copy.reason.equals("dispatch_uncertain"), "Interrupted dispatch is suspended instead of repeated");
        CompoundTag legacy = saved.copy();
        CompoundTag legacyOrder = legacy.getList("Orders", 10).stream().map(v -> (CompoundTag) v)
                .filter(v -> v.getUUID("Id").equals(o.id)).findFirst().orElseThrow();
        legacyOrder.putBoolean("Dispatching", false);
        legacyOrder.getList("Graph", 10).getCompound(0).putString("Mode", "DEMAND");
        restored = DemandService.load(legacy);
        copy = restored.engine.orders.stream().filter(a -> a.id.equals(o.id)).findFirst().orElseThrow();
        helper.assertTrue(copy.state == State.SUSPENDED && copy.reason.equals("recipe_changed"),
                "An old manual chain using the removed mode is suspended for review");
        legacyOrder.putBoolean("Internal", true);
        restored = DemandService.load(legacy);
        helper.assertTrue(restored.engine.orders.stream().noneMatch(a -> a.id.equals(o.id)), "Legacy background orders cannot dispatch again");
        helper.assertTrue(restored.engine.pending.stream().anyMatch(p -> p.order.equals(o.id)), "Already dispatched legacy materials retain their return accounting");
        service.engine.orders.remove(o); service.engine.pending.removeIf(p -> p.order.equals(o.id)); service.setDirty();
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "demand_stock_keeper")
    public static void stockKeeperSessionAndPublishedCatalog(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3)); UUID network = UUID.randomUUID();
        level.setBlockAndUpdate(pos, AllBlocks.STOCK_TICKER.getDefaultState());
        StockTickerBlockEntity ticker = (StockTickerBlockEntity) level.getBlockEntity(pos); ticker.behaviour.freqId = network;
        level.setBlockAndUpdate(pos.west(), AllBlocks.BLAZE_BURNER.getDefaultState()
                .setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
                        com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SMOULDERING));
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DemandMenu"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1);
        // Exercise the actual provider instead of assuming which server menu class Create constructs.
        player.containerMenu = ticker.new RequestMenuProvider().createMenu(1, player.getInventory(), player);
        DemandService service = DemandService.get(level.getServer());
        helper.assertTrue(service.access(player, ticker), "The native Stock Keeper REQUEST menu must be accepted on the server: type="
                + player.containerMenu.getType() + " bound=" + ((com.simibubi.create.foundation.gui.menu.MenuBase<?>) player.containerMenu).contentHolder.equals(ticker)
                + " keeper=" + ticker.isKeeperPresent() + " access=" + ticker.behaviour.mayInteract(player)
                + " distance=" + player.distanceToSqr(pos.getCenter()));
        BlockPos boardPos = pos.east(2); level.setBlockAndUpdate(boardPos.south(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(boardPos, AllBlocks.FACTORY_GAUGE.getDefaultState().setValue(FactoryPanelBlock.FACE, AttachFace.WALL).setValue(FactoryPanelBlock.FACING, Direction.NORTH));
        FactoryPanelBlockEntity board = (FactoryPanelBlockEntity) level.getBlockEntity(boardPos);
        var slots = FactoryPanelBlock.PanelSlot.values(); board.addPanel(slots[0], network); board.addPanel(slots[1], network);
        var product = board.panels.get(slots[0]); var ingredient = board.panels.get(slots[1]);
        product.setFilter(new ItemStack(Items.OAK_PLANKS)); ingredient.setFilter(new ItemStack(Items.OAK_LOG));
        product.recipeOutput = 4; product.recipeAddress = "planks";
        product.targetedBy.put(ingredient.getPanelPosition(), new FactoryPanelConnection(ingredient.getPanelPosition(), 1));
        DemandService.register(product); DemandService.register(ingredient);
        ((DemandGauge) product).demandMode(Mode.STOCK);
        CompoundTag mode = new CompoundTag(); mode.putString("Action", "mode"); mode.putLong("Pos", boardPos.asLong());
        mode.putInt("Slot", slots[0].ordinal()); mode.putString("Mode", Mode.ORDERS.name());
        CompoundTag staleMode = mode.copy(); staleMode.putInt("Mode", 1);
        DemandCommands.handle(player, staleMode);
        helper.assertTrue(((DemandGauge) product).demandMode() == Mode.STOCK, "An obsolete numeric mode cannot accidentally opt a gauge into orders");
        DemandCommands.handle(player, mode);
        helper.assertTrue(((DemandGauge) product).demandMode() == Mode.ORDERS, "The actual server command confirms the requested mode");
        service.refresh(); CompoundTag catalog = service.snapshot(player, ticker);
        helper.assertTrue(catalog.getList("Catalog", 10).size() == 1, "Accepting orders publishes the gauge's product in the same network");
        helper.assertTrue(catalog.getList("Catalog", 10).getCompound(0).getUUID("Id").equals(((DemandGauge) product).demandId()), "Catalog keeps the selected producer identity");
        player.containerMenu = player.inventoryMenu;
        helper.assertTrue(!service.access(player, ticker), "Closing the request menu revokes access"); helper.succeed();
    }
    private record KeeperFixture(StockTickerBlockEntity ticker, net.minecraft.server.level.ServerPlayer player, DemandService service) {}
    private static KeeperFixture keeper(GameTestHelper helper, String name) {
        var level = helper.getLevel(); BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos, AllBlocks.STOCK_TICKER.getDefaultState());
        StockTickerBlockEntity ticker = (StockTickerBlockEntity) level.getBlockEntity(pos); ticker.behaviour.freqId = UUID.randomUUID();
        level.setBlockAndUpdate(pos.west(), AllBlocks.BLAZE_BURNER.getDefaultState()
                .setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
                        com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SMOULDERING));
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), name));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1);
        player.containerMenu = ticker.new RequestMenuProvider().createMenu(1, player.getInventory(), player);
        return new KeeperFixture(ticker, player, DemandService.get(level.getServer()));
    }
    private static Order exampleOrder(KeeperFixture fixture) {
        UUID root = UUID.randomUUID(); Key output = fixture.service.key(fixture.ticker.behaviour.freqId, new ItemStack(Items.OAK_PLANKS));
        Recipe recipe = new Recipe(root, output, 4, Mode.ORDERS,
                List.of(new Input(UUID.randomUUID(), fixture.service.key(fixture.ticker.behaviour.freqId, new ItemStack(Items.OAK_LOG)), 1)), "test");
        Order order = new Order(UUID.randomUUID(), fixture.player.getUUID(), root, 4, Map.of(root, recipe));
        fixture.service.engine.orders.add(order); return order;
    }
    @GameTest(template = "empty", batch = "demand_feedback")
    public static void completedOrdersAndFirstOpeningHighlights(GameTestHelper helper) {
        KeeperFixture f = keeper(helper, "DemandFeedback");
        f.service.openSession(f.player, f.ticker);
        Order completed = exampleOrder(f); completed.sent = 4; completed.received = 4; completed.state = State.COMPLETED;
        f.service.collectFinishedOrders();
        helper.assertTrue(!f.service.engine.orders.contains(completed), "A completed order is automatically removed");
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Orders", 10).isEmpty(), "Completed orders disappear from the UI");
        f.service.openSession(f.player, f.ticker);
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Highlights", 10).isEmpty(), "Returns during an open session wait for the next opening");
        CompoundTag saved = f.service.save(new CompoundTag());
        DemandService restored = DemandService.load(saved);
        helper.assertTrue(restored.save(new CompoundTag()).getList("Unseen", 10).size() >= 1,
                "Unread completion highlights survive a world reload");
        var other = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DemandOther"));
        other.setPos(f.player.getX(), f.player.getY(), f.player.getZ());
        other.containerMenu = f.ticker.new RequestMenuProvider().createMenu(2, other.getInventory(), other);
        f.service.openSession(other, f.ticker);
        helper.assertTrue(f.service.snapshot(other, f.ticker).getList("Highlights", 10).isEmpty(), "Another player cannot consume the author's highlights");
        f.player.containerMenu = f.ticker.new RequestMenuProvider().createMenu(3, f.player.getInventory(), f.player);
        f.service.openSession(f.player, f.ticker);
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Highlights", 10).size() == 1, "The first reopening highlights the returned product");
        f.service.openSession(f.player, f.ticker);
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Highlights", 10).size() == 1, "Resizing or polling keeps the highlight in the same menu session");
        f.player.containerMenu = f.ticker.new RequestMenuProvider().createMenu(4, f.player.getInventory(), f.player);
        f.service.openSession(f.player, f.ticker);
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Highlights", 10).isEmpty(), "The following openings do not repeat the highlight");
        Order surplus = exampleOrder(f); surplus.state = State.COMPLETED; surplus.sent = 8; surplus.received = 4;
        f.service.engine.pending.add(new Pending(surplus.id, surplus.root, surplus.recipe().output(), 4));
        f.service.collectFinishedOrders();
        f.player.containerMenu = f.ticker.new RequestMenuProvider().createMenu(5, f.player.getInventory(), f.player);
        f.service.openSession(f.player, f.ticker);
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Highlights", 10).size() == 1, "A subsequent completion generates its own new highlight");
        f.service.collectFinishedOrders();
        f.player.containerMenu = f.ticker.new RequestMenuProvider().createMenu(6, f.player.getInventory(), f.player);
        f.service.openSession(f.player, f.ticker);
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Highlights", 10).isEmpty(), "Pending batch surplus does not repeat a consumed completion notice");
        f.service.engine.arrived(surplus.recipe().output(), 4); f.service.collectFinishedOrders();
        Order shipped = new Order(UUID.randomUUID(), f.player.getUUID(), surplus.root, 4, surplus.graph, "Elsewhere");
        shipped.state = State.SHIPPED; shipped.received = shipped.sent = shipped.packaged = 4;
        f.service.engine.orders.add(shipped);
        f.service.engine.pending.add(new Pending(shipped.id, shipped.root, shipped.recipe().output(), 1));
        f.service.collectFinishedOrders();
        helper.assertTrue(f.service.engine.orders.contains(shipped), "Shipped orders retain accounting until pending surplus returns");
        f.service.engine.arrived(shipped.recipe().output(), 1); f.service.collectFinishedOrders();
        helper.assertTrue(!f.service.engine.orders.contains(shipped), "Shipped orders are cleaned after final pending returns");
        f.player.containerMenu = f.ticker.new RequestMenuProvider().createMenu(7, f.player.getInventory(), f.player);
        f.service.openSession(f.player, f.ticker);
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Highlights", 10).isEmpty(), "Shipped products do not generate stock highlights");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "demand_feedback")
    public static void cancellingClearsUiAndKeepsInflightAccounting(GameTestHelper helper) {
        KeeperFixture f = keeper(helper, "DemandCancel");
        var network = new com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork(f.ticker.behaviour.freqId);
        network.owner = UUID.randomUUID(); com.simibubi.create.Create.LOGISTICS.logisticsNetworks.put(network.id, network);
        Order order = exampleOrder(f); order.state = State.RETURNING; order.sent = 4;
        f.service.engine.pending.add(new Pending(order.id, order.root, order.recipe().output(), 4));
        var stranger = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DemandStranger"));
        stranger.setPos(f.player.getX(), f.player.getY(), f.player.getZ());
        stranger.containerMenu = f.ticker.new RequestMenuProvider().createMenu(2, stranger.getInventory(), stranger);
        helper.assertTrue(!f.service.cancel(stranger, f.ticker, order.id), "A network user cannot cancel someone else's order");
        helper.assertTrue(f.service.cancel(f.player, f.ticker, order.id) && order.state == State.CANCELLED, "The author can cancel the selected order");
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Orders", 10).isEmpty(), "The cancelled order disappears immediately from the panel");
        helper.assertTrue(f.service.engine.pending.stream().anyMatch(p -> p.order.equals(order.id)), "Deleting the order keeps already dispatched production accounted for");
        f.service.engine.arrived(order.recipe().output(), 4); f.service.collectFinishedOrders();
        helper.assertTrue(!f.service.engine.orders.contains(order) && f.service.engine.pending.stream().noneMatch(p -> p.order.equals(order.id)), "The cancelled job is removed when pending returns drain");
        f.service.openSession(f.player, f.ticker);
        helper.assertTrue(f.service.snapshot(f.player, f.ticker).getList("Highlights", 10).isEmpty(), "Cancellation does not generate a completion highlight");
        Order adminOrder = exampleOrder(f);
        var admin = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(network.owner, "DemandAdmin"));
        admin.setPos(f.player.getX(), f.player.getY(), f.player.getZ());
        admin.containerMenu = f.ticker.new RequestMenuProvider().createMenu(3, admin.getInventory(), admin);
        helper.assertTrue(f.service.cancel(admin, f.ticker, adminOrder.id), "Network administrators can cancel another author's order");
        helper.succeed();
    }
    private static PackagerBlockEntity packager(GameTestHelper h, BlockPos pos, Direction facing) {
        h.getLevel().setBlockAndUpdate(pos, AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, facing));
        return (PackagerBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    private static void link(GameTestHelper h, BlockPos pos, UUID network) {
        h.getLevel().setBlockAndUpdate(pos, AllBlocks.STOCK_LINK.getDefaultState().setValue(PackagerLinkBlock.FACE, AttachFace.FLOOR));
        ((PackagerLinkBlockEntity) h.getLevel().getBlockEntity(pos)).behaviour.freqId = network;
    }
}
