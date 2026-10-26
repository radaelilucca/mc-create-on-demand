package com.radaeli.createondemand.forge1201.demand;

import com.radaeli.createondemand.demand.*;
import com.radaeli.createondemand.demand.DemandEngine.*;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class DemandCommands {
    private record Rate(long tick, int count) {}
    private static final Map<UUID, Rate> LAST = new HashMap<>();
    private DemandCommands() {}
    public static void handle(ServerPlayer player, CompoundTag request) {
        long now = player.serverLevel().getGameTime();
        Rate previous = LAST.get(player.getUUID());
        int count = previous != null && previous.tick == now ? previous.count + 1 : 1;
        if (count > 4) return;
        LAST.put(player.getUUID(), new Rate(now, count));
        BlockPos pos = BlockPos.of(request.getLong("Pos"));
        if (!player.level().hasChunkAt(pos) || player.distanceToSqr(pos.getCenter()) > 64) return;
        DemandService service = DemandService.get(player.server);
        String action = request.getString("Action");
        if (action.equals("mode")) {
            if (!(player.level().getBlockEntity(pos) instanceof FactoryPanelBlockEntity be)) return;
            int slot = request.getInt("Slot"); String mode = request.getString("Mode");
            if (slot < 0 || slot >= 4 || (!mode.equals("STOCK") && !mode.equals("ORDERS")) || be.restocker) return;
            FactoryPanelBehaviour gauge = be.panels.get(FactoryPanelBlock.PanelSlot.values()[slot]);
            if (gauge == null || !gauge.isActive() || !com.simibubi.create.Create.LOGISTICS.mayAdministrate(gauge.network, player)) return;
            ((DemandGauge) gauge).demandMode(Mode.fromSaved(mode)); be.notifyUpdate(); return;
        }
        if (!(player.level().getBlockEntity(pos) instanceof StockTickerBlockEntity ticker) || !service.access(player, ticker)) return;
        service.refresh(); CompoundTag response;
        try {
            if (action.equals("preview") || action.equals("submit")) {
                UUID root = request.getUUID("Root"); int quantity = request.getInt("Quantity");
                Recipe r = service.recipes().get(root);
                if (r == null || r.mode() != Mode.ORDERS || !r.output().network().equals(ticker.behaviour.freqId)
                        || quantity < 1 || quantity > 65536) return;
                // Authorize every ingredient network, not only the final product network.
                for (Recipe recipe : DemandEngine.graph(root, service.recipes()).values())
                    for (Input i : recipe.inputs()) if (!com.simibubi.create.Create.LOGISTICS.mayInteract(i.key().network(), player)) throw new IllegalArgumentException("network_access");
                if (action.equals("submit")) {
                    if (service.engine.orders.stream().filter(o -> !o.terminal()).count() >= 256) throw new IllegalArgumentException("queue_full");
                    String address = request.getString("Address");
                    if (address.length() > 1024) throw new IllegalArgumentException("invalid_order");
                    service.engine.submit(player.getUUID(), root, quantity, address);
                    response = service.snapshot(player, ticker); response.putBoolean("Accepted", true);
                } else {
                    response = service.snapshot(player, ticker); response.put("Preview", service.preview(root, quantity));
                    response.putUUID("Root", root); response.putInt("Quantity", quantity);
                }
            } else {
                if (action.equals("cancel")) {
                    if (!service.cancel(player, ticker, request.getUUID("Order"))) throw new IllegalArgumentException("invalid_order");
                } else if (action.equals("open")) service.openSession(player, ticker);
                else if (!action.equals("catalog")) return;
                response = service.snapshot(player, ticker);
            }
        } catch (IllegalArgumentException | ArithmeticException e) {
            String error = Objects.toString(e.getMessage(), "invalid_order");
            if (error.startsWith("cycle:")) error = "invalid_chain";
            if (!Set.of("queue_full", "network_access", "invalid_chain", "chain_limit", "amount_limit").contains(error)) error = "invalid_order";
            response = service.snapshot(player, ticker); response.putString("Error", error);
        }
        response.putLong("Pos", pos.asLong()); DemandNetwork.reply(player, response);
    }
}
