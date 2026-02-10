package com.liante.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UnitStatPayload(
        int entityId,
        String name,
        float currentMana,
        float maxMana,
        float currentDamage,
        float attackSpeed,
        String jobKey
) implements CustomPayload {
    public static final Id<UnitStatPayload> ID = new Id<>(Identifier.of("mingtd", "unit_stat"));

    // [추가] 컴팩트 생성자: 데이터를 소문자로 강제 변환하여 시스템 일관성 유지
    public UnitStatPayload {
        jobKey = jobKey.toLowerCase(java.util.Locale.ROOT);
    }

    public static final PacketCodec<RegistryByteBuf, UnitStatPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, UnitStatPayload::entityId,
            PacketCodecs.STRING, UnitStatPayload::name,
            PacketCodecs.FLOAT, UnitStatPayload::currentMana,
            PacketCodecs.FLOAT, UnitStatPayload::maxMana,
            PacketCodecs.FLOAT, UnitStatPayload::currentDamage,
            PacketCodecs.FLOAT, UnitStatPayload::attackSpeed,
            PacketCodecs.STRING, UnitStatPayload::jobKey,
            UnitStatPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
