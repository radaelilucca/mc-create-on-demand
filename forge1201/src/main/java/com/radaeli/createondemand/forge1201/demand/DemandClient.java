package com.radaeli.createondemand.forge1201.demand;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public final class DemandClient {
    private DemandClient() {}
    public interface Host { DemandPanel demandPanel(); }
    public static void receive(CompoundTag tag) {
        if (tag != null && Minecraft.getInstance().screen instanceof Host host && host.demandPanel() != null)
            host.demandPanel().receive(tag);
    }
}
