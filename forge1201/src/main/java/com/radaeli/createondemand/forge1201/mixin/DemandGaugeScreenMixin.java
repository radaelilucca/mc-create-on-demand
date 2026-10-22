package com.radaeli.createondemand.forge1201.mixin;

import com.radaeli.createondemand.demand.*;
import com.radaeli.createondemand.demand.DemandEngine.Mode;
import com.radaeli.createondemand.forge1201.demand.*;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FactoryPanelScreen.class, remap = false)
public abstract class DemandGaugeScreenMixin extends AbstractSimiScreen {
    @Shadow private FactoryPanelBehaviour behaviour;
    @Shadow private boolean restocker;
    @Inject(method = "init", remap = true, at = @At("TAIL"))
    private void onDemand$mode(CallbackInfo ci) {
        if (restocker) return;
        IconButton mode = new IconButton(guiLeft - 21, guiTop + windowHeight - 26, DemandIcons.mode(((DemandGauge) behaviour).demandMode()));
        mode.setToolTip(DemandPanel.text("mode." + ((DemandGauge) behaviour).demandMode().name().toLowerCase(java.util.Locale.ROOT)));
        mode.withCallback(() -> {
            DemandGauge gauge = (DemandGauge) behaviour; Mode next = Mode.values()[(gauge.demandMode().ordinal() + 1) % Mode.values().length];
            gauge.demandMode(next); mode.setIcon(DemandIcons.mode(next)); mode.setToolTip(DemandPanel.text("mode." + next.name().toLowerCase(java.util.Locale.ROOT)));
            CompoundTag tag = new CompoundTag(); tag.putString("Action", "mode"); tag.putLong("Pos", behaviour.getPos().asLong());
            tag.putInt("Slot", behaviour.slot.ordinal()); tag.putString("Mode", next.name()); DemandNetwork.send(tag);
        }); addRenderableWidget(mode);
    }
}
