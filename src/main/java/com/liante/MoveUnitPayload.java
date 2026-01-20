package com.liante;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record MoveUnitPayload(int entityId, Vec3d targetPos) implements CustomPayload {
    // 프로젝트 ID에 맞춰 "mingtd" 부분을 수정하세요
    public static final Id<MoveUnitPayload> ID = new Id<>(Identifier.of("mingtd", "move_unit"));

    public static final PacketCodec<RegistryByteBuf, MoveUnitPayload> CODEC = PacketCodec.tuple(
            net.minecraft.network.codec.PacketCodecs.VAR_INT, MoveUnitPayload::entityId,
            Vec3d.PACKET_CODEC, MoveUnitPayload::targetPos,
            MoveUnitPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
