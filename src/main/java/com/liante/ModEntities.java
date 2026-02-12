package com.liante;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

public class ModEntities {
    // ID 정의
    public static final Identifier DEFENSE_MONSTER_ID = Identifier.of("mingtd", "defense_monster");
    // 1. 유닛의 고유 ID를 상수로 정의
    public static final Identifier DEFENSE_UNIT_ID = Identifier.of("mingtd", "defense_unit");

    // 2. 엔티티 타입 등록 (PathAwareEntity에 맞춰 빌더 수정)
    public static final EntityType<MingtdUnit> MINGTD_UNIT_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            DEFENSE_UNIT_ID,
            EntityType.Builder.create(MingtdUnit::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.95f) // 플레이어와 동일한 크기
                    .build(RegistryKey.of(Registries.ENTITY_TYPE.getKey(), DEFENSE_UNIT_ID))
    );

    // 몬스터 엔티티 타입 등록
    public static final EntityType<DefenseMonsterEntity> DEFENSE_MONSTER_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            DEFENSE_MONSTER_ID,
            EntityType.Builder.create(DefenseMonsterEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f) // 기본 크기 설정
                    .build(RegistryKey.of(Registries.ENTITY_TYPE.getKey(), DEFENSE_MONSTER_ID))
    );

    public static void registerEntities() {
        // 1. 유닛 속성 등록 (Mingtd.java에서 이리로 옮겨오는 것을 추천)
        FabricDefaultAttributeRegistry.register(MINGTD_UNIT_TYPE, MingtdUnit.createAttributes());

        // 2. 몬스터 속성 등록
        FabricDefaultAttributeRegistry.register(DEFENSE_MONSTER_TYPE, DefenseMonsterEntity.createMonsterAttributes());
    }
}