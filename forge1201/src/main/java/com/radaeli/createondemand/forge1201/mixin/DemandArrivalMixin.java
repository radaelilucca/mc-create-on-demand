package com.radaeli.createondemand.forge1201.mixin;

import com.radaeli.createondemand.forge1201.demand.DemandService;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RequestPromiseQueue.class, remap = false)
public abstract class DemandArrivalMixin {
    @Inject(method = "itemEnteredSystem", at = @At("HEAD"))
    private void onDemand$credit(ItemStack stack, int count, CallbackInfo ci) {
        DemandService.arrival((RequestPromiseQueue) (Object) this, stack, count);
    }
}
