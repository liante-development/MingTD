package com.liante.manager;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CameraMovePayload(float deltaX, float deltaY, float deltaZ) implements CustomPayload {
    public static final Id<CameraMovePayload> ID = new Id<>(Identifier.of("mingtd", "camera_move"));
    public static final PacketCodec<RegistryByteBuf, CameraMovePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, CameraMovePayload::deltaX,
            PacketCodecs.FLOAT, CameraMovePayload::deltaY,
            PacketCodecs.FLOAT, CameraMovePayload::deltaZ,
            CameraMovePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
