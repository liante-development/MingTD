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
    public record UnitEntry(int entityId, String id, String name, int priority) {
        public UnitEntry {
            id = id.toLowerCase(java.util.Locale.ROOT);
        }

        public static final PacketCodec<RegistryByteBuf, UnitEntry> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, UnitEntry::entityId,
                PacketCodecs.STRING, UnitEntry::id,
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