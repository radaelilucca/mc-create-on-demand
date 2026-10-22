package com.radaeli.createondemand.forge1201.demand;

import com.radaeli.createondemand.forge1201.CreateOnDemand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

public final class DemandNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(CreateOnDemand.MOD_ID, "demand"), () -> "1", "1"::equals, "1"::equals);
    public record Request(CompoundTag data) {}
    public record Response(CompoundTag data) {}
    public static void register() {
        CHANNEL.registerMessage(0, Request.class, (p, b) -> b.writeNbt(p.data), b -> new Request(b.readNbt()), (p, supplier) -> {
            var context = supplier.get(); ServerPlayer player = context.getSender();
            context.enqueueWork(() -> { if (player != null && p.data != null) DemandCommands.handle(player, p.data); }); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Response.class, (p, b) -> b.writeNbt(p.data), b -> new Response(b.readNbt()), (p, supplier) -> {
            var context = supplier.get(); context.enqueueWork(() -> DemandClient.receive(p.data)); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(CompoundTag tag) { CHANNEL.sendToServer(new Request(tag)); }
    public static void reply(ServerPlayer player, CompoundTag tag) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Response(tag)); }
}
