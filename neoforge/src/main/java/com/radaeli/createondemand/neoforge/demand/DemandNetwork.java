package com.radaeli.createondemand.neoforge.demand;

import com.radaeli.createondemand.neoforge.CreateOnDemand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DemandNetwork {
    public record Request(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateOnDemand.MOD_ID, "demand_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((b, p) -> b.writeNbt(p.data), b -> new Request(b.readNbt()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Response(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateOnDemand.MOD_ID, "demand_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = StreamCodec.of((b, p) -> b.writeNbt(p.data), b -> new Response(b.readNbt()));
        @Override public Type<Response> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Request.TYPE, Request.CODEC, (p, c) -> c.enqueueWork(() -> {
            if (p.data != null && c.player() instanceof ServerPlayer player) DemandCommands.handle(player, p.data);
        }));
        registrar.playToClient(Response.TYPE, Response.CODEC, (p, c) -> c.enqueueWork(() -> DemandClient.receive(p.data)));
    }
    public static void send(CompoundTag tag) { PacketDistributor.sendToServer(new Request(tag)); }
    public static void reply(ServerPlayer player, CompoundTag tag) { PacketDistributor.sendToPlayer(player, new Response(tag)); }
}
