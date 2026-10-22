package com.radaeli.createondemand.neoforge.mixin;

import com.radaeli.createondemand.demand.*;
import com.radaeli.createondemand.demand.DemandEngine.Mode;
import com.radaeli.createondemand.neoforge.demand.DemandService;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class DemandGaugeMixin implements DemandGauge {
    @Unique private UUID onDemand$demandId;
    @Unique private Mode onDemand$mode = Mode.ORDERS;
    @Override public UUID demandId() {
        if (onDemand$demandId == null) {
            onDemand$demandId = UUID.randomUUID();
            ((FactoryPanelBehaviour) (Object) this).panelBE().setChanged();
        }
        return onDemand$demandId;
    }
    @Override public Mode demandMode() { return onDemand$mode; }
    @Override public void demandMode(Mode mode) { onDemand$mode = mode; ((FactoryPanelBehaviour) (Object) this).panelBE().setChanged(); }
    @Inject(method = "tick", at = @At("HEAD"))
    private void onDemand$register(CallbackInfo ci) { DemandService.register((FactoryPanelBehaviour) (Object) this); }
    @Inject(method = "moveTo", at = @At("TAIL"))
    private void onDemand$moved(FactoryPanelPosition position, net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        DemandService.register((FactoryPanelBehaviour) (Object) this);
    }
    @Inject(method = "write", at = @At("TAIL"))
    private void onDemand$write(CompoundTag tag, HolderLookup.Provider registries, boolean packet, CallbackInfo ci) {
        FactoryPanelBehaviour g = (FactoryPanelBehaviour) (Object) this;
        if (g.isActive()) { CompoundTag slot = tag.getCompound(CreateLang.asId(g.slot.name())); slot.putUUID("DemandId", demandId()); slot.putString("DemandMode", onDemand$mode.name()); }
    }
    @Inject(method = "writeSafe", at = @At("TAIL"))
    private void onDemand$schematic(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        FactoryPanelBehaviour g = (FactoryPanelBehaviour) (Object) this;
        if (g.isActive()) tag.getCompound(CreateLang.asId(g.slot.name())).putString("DemandMode", onDemand$mode.name());
    }
    @Inject(method = "read", at = @At("TAIL"))
    private void onDemand$read(CompoundTag tag, HolderLookup.Provider registries, boolean packet, CallbackInfo ci) {
        FactoryPanelBehaviour g = (FactoryPanelBehaviour) (Object) this;
        CompoundTag slot = tag.getCompound(CreateLang.asId(g.slot.name()));
        onDemand$demandId = slot.hasUUID("DemandId") ? slot.getUUID("DemandId") : null;
        onDemand$mode = Mode.fromSaved(slot.getString("DemandMode"));
    }
    @Inject(method = "disable", at = @At("TAIL"))
    private void onDemand$reset(CallbackInfo ci) { onDemand$demandId = null; onDemand$mode = Mode.ORDERS; }
}
