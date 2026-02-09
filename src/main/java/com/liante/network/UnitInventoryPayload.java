package com.liante.network;

import com.liante.MingtdUnit;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record UnitInventoryPayload(Map<String, Integer> inventory) implements CustomPayload {
    public static final Id<UnitInventoryPayload> ID = new Id<>(Identifier.of("mingtd", "unit_inventory"));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public static final PacketCodec<RegistryByteBuf, UnitInventoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_INT),
            UnitInventoryPayload::inventory,
            UnitInventoryPayload::new
    );

    /**
     * [동기화 메서드] 서버의 최신 상태를 조회해서 패킷을 직접 쏩니다.
     */
    public static void sendSync(ServerPlayerEntity player) {
        Map<String, Integer> currentUnits = new HashMap<>();

        for (Entity entity : player.getEntityWorld().iterateEntities()) {
            if (entity instanceof MingtdUnit unit && player.getUuid().equals(unit.getOwnerUuid())) {
                String unitId = unit.getUnitId();
                // [로그 추가] 어떤 엔티티가 어떤 ID를 주는지 확인
                System.out.println("[MingTD-Server] EntityID: " + unit.getId() + " -> Job: " + unitId);
                currentUnits.put(unitId, currentUnits.getOrDefault(unitId, 0) + 1);
            }
        }
        ServerPlayNetworking.send(player, new UnitInventoryPayload(currentUnits));
    }
}
