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

import java.util.*;

public record UnitInventoryPayload(Map<String, List<Integer>> unitEntityMap) implements CustomPayload {
    public static final Id<UnitInventoryPayload> ID = new Id<>(Identifier.of("mingtd", "unit_inventory"));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    // 복잡한 구조를 위한 코덱 정의 (Map<String, List<Integer>>)
    public static final PacketCodec<RegistryByteBuf, UnitInventoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_INT.collect(PacketCodecs.toList())),
            UnitInventoryPayload::unitEntityMap,
            UnitInventoryPayload::new
    );

    /**
     * [동기화 메서드] 서버의 실제 엔티티들을 수집해서 ID 목록을 전송
     */
    public static void sendSync(ServerPlayerEntity player) {
        // 종류별 엔티티 ID 리스트를 담을 맵
        Map<String, List<Integer>> unitEntityMap = new HashMap<>();

        for (Entity entity : player.getEntityWorld().iterateEntities()) {
            if (entity instanceof MingtdUnit unit && player.getUuid().equals(unit.getOwnerUuid())) {
                String unitId = unit.getUnitId();

                // 종류별로 리스트에 엔티티 ID 추가
                unitEntityMap.computeIfAbsent(unitId, k -> new ArrayList<>()).add(unit.getId());
            }
        }

        ServerPlayNetworking.send(player, new UnitInventoryPayload(unitEntityMap));
    }
}
