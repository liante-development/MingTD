package com.liante.spawner;

import com.liante.*;
import com.liante.config.DefenseState;
import com.liante.network.UnitInventoryPayload;
import com.liante.unit.MingtdUnits;
import com.liante.unit.Rarity;
import com.liante.unit.UnitInfo;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

import static com.liante.ModEntities.MINGTD_UNIT_TYPE;

public class UnitSpawner {
    private static final Random RANDOM = new Random();
//    public enum DefenseUnit {
//        // 우선순위 예시: 법사(10) > 궁수(5) > 도적(3) > 전사(1)
//        WARRIOR("warrior", "§f[전사]", Items.IRON_SWORD, 8.0f, 4.0f, 1, 0),
//        ARCHER("archer", "§e[궁수]", Items.BOW, 5.0f, 16.0f, 5, 0),
//        MAGE("mage", "§b[법사]", Items.STICK, 12.0f, 12.0f, 10, 0),
//        ROGUE("rogue", "§6[도적]", Items.IRON_AXE, 6.0f, 6.0f, 3, 0),
//
//        // --- MAGIC 등급 (아이디와 디스플레이 네임에 등급 강조) ---
//        MAGIC_WARRIOR("magic_warrior", "§9[매직 전사]", Items.DIAMOND_SWORD, 18.0f, 4.0f, 2, 1),
//        MAGIC_ARCHER("magic_archer", "§9[매직 궁수]", Items.CROSSBOW, 12.0f, 18.0f, 6, 1),
//        MAGIC_MAGE("magic_mage", "§9[매직 법사]", Items.BLAZE_ROD, 25.0f, 12.0f, 11, 1),
//        MAGIC_ROGUE("magic_rogue", "§9[매직 도적]", Items.IRON_SWORD, 15.0f, 6.0f, 4, 1);
//
//        private final String id;
//        private final String displayName;
//        private final Item defaultItem;
//        private final float damage;
//        private final float range;
//        private final int priority; // 우선순위 필드 추가
//        private final int rank;
//
//        DefenseUnit(String id, String displayName, Item defaultItem, float damage, float range, int priority, int rank) {
//            this.id = id;
//            this.displayName = displayName;
//            this.defaultItem = defaultItem;
//            this.damage = damage;
//            this.range = range;
//            this.priority = priority;
//            this.rank = rank;
//        }
//
//        public Item getMainItem() { return this.defaultItem; }
//        public String getDisplayName() { return this.displayName; }
//        public float getDamage() { return this.damage; }
//        public float getRange() { return this.range; }
//
//        // [해결] Getter 메서드 추가
//        public int getPriority() { return this.priority; }
//        public String getId() { return this.id; }
//        public int getRank() { return this.rank; }
//
//        public static DefenseUnit fromId(String id) {
//            for (DefenseUnit unit : values()) {
//                if (unit.id.equals(id)) {
//                    return unit;
//                }
//            }
//            throw new IllegalArgumentException("Unknown DefenseUnit ID: " + id);
//        }
//    }

    public static void spawnRandomUnit(ServerPlayerEntity player, ServerWorld world) {
        // 1. 위습 소비 로직 (기존 유지)
        DefenseState state = DefenseState.getServerState(world);
        if (!state.consumeWisp(world, 1)) {
            player.sendMessage(Text.literal("§c위습이 부족합니다!"), true);
            return;
        }

        // 2. MingtdUnit.UNIT_REGISTRY에서 NORMAL(Rank 0) 등급 유닛만 필터링
        List<UnitInfo> normalUnits = MingtdUnits.UNIT_REGISTRY.values().stream()
                .filter(unit -> unit.rarity() == Rarity.NORMAL) // 등급 기반 필터링
                .toList();

        if (normalUnits.isEmpty()) {
            player.sendMessage(Text.literal("§c소환 가능한 일반 유닛 데이터가 없습니다!"), false);
            return;
        }

        // 3. 일반 등급 유닛 중 랜덤 선택
        UnitInfo selectedInfo = normalUnits.get(world.random.nextInt(normalUnits.size()));
        MingtdUnit unitEntity = MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);

        if (unitEntity != null) {
            // 소환 위치 설정 (기존 좌표 유지)
            double spawnX = Mingtd.SPAWN_POS.getX() + 0.5;
            double spawnY = Mingtd.SPAWN_POS.getY() + 1.0;
            double spawnZ = Mingtd.SPAWN_POS.getZ() + 0.5;

            unitEntity.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0, 0);

            // --- 중요: 데이터 주입 로직 ---
            // 1. 유닛 정보 주입 (기존 setUnitType 대신 setUnitInfo로 변경 필요)
            unitEntity.setUnitInfo(selectedInfo);

            // 2. 이름 및 소유주 설정 (등급 색상 적용)
            unitEntity.setCustomName(Text.literal(selectedInfo.name()).formatted(selectedInfo.rarity().color));
            unitEntity.setCustomNameVisible(true);
            unitEntity.setOwnerUuid(player.getUuid());

            // 3. 월드 소환
            world.spawnEntity(unitEntity);

            // 4. 동기화 및 메시지 전송
            UnitInventoryPayload.sendSync(player);

            // 이름에 등급 색상을 입혀서 메시지 출력
            player.sendMessage(Text.literal(selectedInfo.name())
                    .formatted(selectedInfo.rarity().color)
                    .append(Text.literal("§a 유닛이 소환되었습니다!")), true);
        }
    }

//    public static void spawnRandomUnit(ServerPlayerEntity player, ServerWorld world) {
//        DefenseState state = DefenseState.getServerState(world);
//        if (!state.consumeWisp(world, 1)) {
//            player.sendMessage(Text.literal("§c위습이 부족합니다!"), true);
//            return;
//        }
//
//        // 1. 일반 등급(Rank 0)인 유닛들만 모으기
//        List<DefenseUnit> normalUnits = Arrays.stream(DefenseUnit.values())
//                .filter(unit -> unit.getRank() == 0)
//                .toList();
//
//        if (normalUnits.isEmpty()) return;
//
//        // 2. 일반 등급 중에서 랜덤 선택
//        DefenseUnit selectedUnit = normalUnits.get(world.random.nextInt(normalUnits.size()));
//        MingtdUnit unitEntity = MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);
//
//        if (unitEntity != null) {
//            double spawnX = Mingtd.SPAWN_POS.getX() + 0.5;
//            double spawnY = Mingtd.SPAWN_POS.getY() + 1.0;
//            double spawnZ = Mingtd.SPAWN_POS.getZ() + 0.5;
//
//            unitEntity.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0, 0);
//            // 1. 직업 데이터 주입 (내부에서 아이템 장착 + AI 갱신 수행)
//            unitEntity.setUnitType(selectedUnit);
//
//            // 2. 이름 설정
//            unitEntity.setCustomName(Text.literal(selectedUnit.getDisplayName()));
//            unitEntity.setCustomNameVisible(true);
//            unitEntity.setOwnerUuid(player.getUuid()); // 주인 각인
//
//            // 3. 월드에 소환
//            world.spawnEntity(unitEntity);
//
//
//            UnitInventoryPayload.sendSync(player);
//
//            player.sendMessage(Text.literal(selectedUnit.getDisplayName() + "§a 유닛이 소환되었습니다!"), true);
//        }
//    }

    public static void spawnMannequin(ServerPlayerEntity player, ServerWorld world, BlockPos pos, UnitInfo selectedUnit) {
        // 1. 엔티티 생성 (기존 로직 동일)
        MingtdUnit unitEntity = MINGTD_UNIT_TYPE.create(world, SpawnReason.COMMAND);

        if (unitEntity != null) {
            // 2. 좌표 설정 (입력받은 pos 기준 중앙)
            double spawnX = pos.getX() + 0.5;
            double spawnY = pos.getY(); // 마네킹은 바닥에 딱 붙도록 +0.0
            double spawnZ = pos.getZ() + 0.5;

            unitEntity.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0, 0);

            // 3. 유닛 데이터 주입 (Enum 기반)
            unitEntity.setUnitInfo(selectedUnit);
            unitEntity.setCustomName(Text.literal("§7[마네킹] §f" + selectedUnit.name()));
            unitEntity.setCustomNameVisible(true);
            unitEntity.setOwnerUuid(player.getUuid());

            // 4. [마네킹 핵심 설정] AI 및 무적 처리
            unitEntity.setAiDisabled(true);   // 이동 및 공격 AI 중지
            unitEntity.setInvulnerable(true); // 데미지 면역
            unitEntity.setPersistent();       // 멀리 가도 사라지지 않음

            // 5. 월드에 소환
            world.spawnEntity(unitEntity);

            player.sendMessage(Text.literal("§e마네킹: " + selectedUnit.name() + "§a이(가) 배치되었습니다!"), false);
        }
    }

    public static void spawnSpecificUnit(ServerPlayerEntity player, ServerWorld world, UnitInfo unitType, BlockPos pos) {
        // Mingtd.MINGTD_UNIT_TYPE는 본인의 메인 클래스에 등록된 EntityType 변수명으로 확인하세요!
        MingtdUnit unitEntity = MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);

        if (unitEntity != null) {
            // 소환 위치 설정 (발 밑 블록 중심을 위해 +0.5)
            double spawnX = Mingtd.SPAWN_POS.getX() + 0.5;
            double spawnY = Mingtd.SPAWN_POS.getY() + 1.0;
            double spawnZ = Mingtd.SPAWN_POS.getZ() + 0.5;

            unitEntity.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0, 0);

            // 1. 유닛 데이터 주입 (Enum 정보 전달)
            unitEntity.setUnitInfo(unitType);

            // 2. 이름 설정
            unitEntity.setCustomName(Text.literal(unitType.name()));
            unitEntity.setCustomNameVisible(true);
            unitEntity.setOwnerUuid(player.getUuid()); // 주인 각인

            // 3. 월드 스폰
            world.spawnEntity(unitEntity);

            // [로그] 승급 결과 로그 출력
            // LOGGER.info("[MingtdUpgrade] 승급 소환: " + unitType.getDisplayName());
        }
    }

    public static void spawnDummy(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        // 1. 타입을 DEFENSE_MONSTER_TYPE으로 변경하여 몬스터 모델(좀비 등)로 소환
        DefenseMonsterEntity dummy = ModEntities.DEFENSE_MONSTER_TYPE.create(world, SpawnReason.COMMAND);

        if (dummy != null) {
            dummy.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);

            // 2. 허수아비 기본 설정
            dummy.setCustomName(Text.literal("§6[훈련용 허수아비]"));
            dummy.setCustomNameVisible(true);
            dummy.setAiDisabled(true); // 움직이지 않게 설정
            dummy.setInvulnerable(false);
            dummy.addCommandTag("dummy");
            // 3. 체력 설정 (100만)
            var hpInstance = dummy.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (hpInstance != null) {
                hpInstance.setBaseValue(1000000.0);
                dummy.setHealth(1000000.0f);
            }

            // 4. [중요] 오늘 우리가 만든 방어력 속성 테스트용 설정
            // 이 더미를 때려서 방어력이 잘 작동하는지 확인할 수 있습니다.
            var physDef = dummy.getAttributeInstance(ModAttributes.PHYSICAL_DEFENSE);
            if (physDef != null) {
                physDef.setBaseValue(50.0); // 물리 방어력 50 (데미지 약 33% 감소 예정)
            }

            var magDef = dummy.getAttributeInstance(ModAttributes.MAGIC_DEFENSE);
            if (magDef != null) {
                magDef.setBaseValue(20.0); // 마법 방어력 20
            }

            // 5. 기타 외형 및 상태 설정
            dummy.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            dummy.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.0);

            // 1.21.2의 SCALE 속성 활용
            var scaleAttr = dummy.getAttributeInstance(EntityAttributes.SCALE);
            if (scaleAttr != null) scaleAttr.setBaseValue(1.2);

            // 6. 소환 및 메시지
            world.spawnEntity(dummy);
            player.sendMessage(Text.literal("§6[MingTD] §e물리 방어력 50§a을 가진 테스트 더미가 소환되었습니다!"), false);
        }
    }
}
