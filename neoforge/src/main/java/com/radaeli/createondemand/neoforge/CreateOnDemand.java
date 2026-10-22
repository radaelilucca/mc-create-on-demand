package com.radaeli.createondemand.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal NeoForge entry point for Create: On Demand.
 */
@Mod(CreateOnDemand.MOD_ID)
public final class CreateOnDemand {
    public static final String MOD_ID = "create_on_demand";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CreateOnDemand(IEventBus modEventBus) {
        modEventBus.addListener(com.radaeli.createondemand.neoforge.demand.DemandNetwork::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                        com.radaeli.createondemand.neoforge.demand.DemandService.get(event.getServer()).tick());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                        com.radaeli.createondemand.neoforge.demand.DemandService.stop(event.getServer()));
        LOGGER.info("{} initialized on NeoForge", MOD_ID);
    }

}
