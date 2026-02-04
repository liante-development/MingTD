package com.liante.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record SelectUnitPayload(List<Integer> entityIds) implements CustomPayload {
    public static final Id<SelectUnitPayload> ID = new Id<>(Identifier.of("mingtd", "select_unit"));

    public static final PacketCodec<RegistryByteBuf, SelectUnitPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER.collect(PacketCodecs.toList()), SelectUnitPayload::entityIds,
            SelectUnitPayload::new
    );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}