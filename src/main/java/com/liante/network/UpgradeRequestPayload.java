package com.liante.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs; // 이 클래스를 임포트해야 합니다.
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record UpgradeRequestPayload(
        String recipeId,
        int mainUnitEntityId,
        List<Integer> ingredientIds // [추가] 클라이언트가 선정한 재료들의 Entity ID 리스트
) implements CustomPayload {
    public static final Id<UpgradeRequestPayload> ID = new Id<>(Identifier.of("mingtd", "upgrade_request"));

    // 1.21.2 규격: List<Integer>를 처리하기 위한 PacketCodecs.VAR_INT.collect 활용
    public static final PacketCodec<RegistryByteBuf, UpgradeRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, UpgradeRequestPayload::recipeId,
            PacketCodecs.VAR_INT, UpgradeRequestPayload::mainUnitEntityId,
            PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), UpgradeRequestPayload::ingredientIds, // List 코덱
            UpgradeRequestPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
