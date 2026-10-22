package com.radaeli.createondemand.forge1201;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CreateOnDemand.MOD_ID)
public final class CreateOnDemand {
    public static final String MOD_ID = "create_on_demand";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CreateOnDemand() {
        com.radaeli.createondemand.forge1201.demand.DemandNetwork.register();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
                    if (event.phase == net.minecraftforge.event.TickEvent.Phase.END)
                        com.radaeli.createondemand.forge1201.demand.DemandService.get(event.getServer()).tick();
                });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppedEvent event) ->
                        com.radaeli.createondemand.forge1201.demand.DemandService.stop(event.getServer()));
        LOGGER.info("{} initialized on Forge", MOD_ID);
    }

}
