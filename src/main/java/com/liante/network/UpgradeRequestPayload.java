package com.liante.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs; // 이 클래스를 임포트해야 합니다.
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record UpgradeRequestPayload(
        String recipeId,            // 선택한 레시피의 고유 ID (예: "warrior_v1")
        String resultId,            // 승급 결과물 유닛의 ID (예: "magic_warrior")
        int mainUnitEntityId,       // 클릭한 메인 엔티티 ID
        List<Integer> ingredientIds // 클라이언트가 선별한 재료 엔티티 ID 리스트
) implements CustomPayload {
    public static final Id<UpgradeRequestPayload> ID = new Id<>(Identifier.of("mingtd", "upgrade_request"));

    public static final PacketCodec<RegistryByteBuf, UpgradeRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, UpgradeRequestPayload::recipeId,
            PacketCodecs.STRING, UpgradeRequestPayload::resultId,
            PacketCodecs.VAR_INT, UpgradeRequestPayload::mainUnitEntityId,
            PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), UpgradeRequestPayload::ingredientIds,
            UpgradeRequestPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
