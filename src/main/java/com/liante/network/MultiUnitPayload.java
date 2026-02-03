package com.liante.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record MultiUnitPayload(List<UnitEntry> units) implements CustomPayload {
    public static final Id<MultiUnitPayload> ID = new Id<>(Identifier.of("mingtd", "multi_unit"));
       // 1.21.2 규격: 반드시 이 메서드가 이 내용을 반환해야 합니다.
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
    // 1. UnitEntry 레코드와 전용 코덱 정의
    public record UnitEntry(int entityId, String jobKey, String name, int priority) {
        public static final PacketCodec<RegistryByteBuf, UnitEntry> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, UnitEntry::entityId,
                PacketCodecs.STRING, UnitEntry::jobKey,
                PacketCodecs.STRING, UnitEntry::name,
                PacketCodecs.VAR_INT, UnitEntry::priority,
                UnitEntry::new
        );
    }

    // 2. MultiUnitPayload 코덱 정의 (UnitEntry.CODEC 리스트 활용)
    public static final PacketCodec<RegistryByteBuf, MultiUnitPayload> CODEC = PacketCodec.tuple(
            UnitEntry.CODEC.collect(PacketCodecs.toList()), MultiUnitPayload::units,
            MultiUnitPayload::new
    );
}