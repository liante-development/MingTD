package com.liante.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record MultiUnitPayload(List<UnitEntry> units) implements CustomPayload {
    public static final Id<MultiUnitPayload> ID = new Id<>(Identifier.of("mingtd", "multi_unit"));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    // 1. UnitEntry 레코드 수정
    public record UnitEntry(int entityId, String jobKey, String name, int priority) {
        // [추가] 컴팩트 생성자: 패킷이 생성되거나 수신될 때 jobKey를 소문자로 강제 변환
        public UnitEntry {
            jobKey = jobKey.toLowerCase(java.util.Locale.ROOT);
        }

        public static final PacketCodec<RegistryByteBuf, UnitEntry> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, UnitEntry::entityId,
                PacketCodecs.STRING, UnitEntry::jobKey,
                PacketCodecs.STRING, UnitEntry::name,
                PacketCodecs.VAR_INT, UnitEntry::priority,
                UnitEntry::new
        );
    }

    // 2. MultiUnitPayload 코덱 정의
    public static final PacketCodec<RegistryByteBuf, MultiUnitPayload> CODEC = PacketCodec.tuple(
            UnitEntry.CODEC.collect(PacketCodecs.toList()), MultiUnitPayload::units,
            MultiUnitPayload::new
    );
}