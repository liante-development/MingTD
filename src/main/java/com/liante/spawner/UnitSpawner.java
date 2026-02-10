package com.liante.spawner;

import com.liante.config.DefenseState;
import com.liante.Mingtd;
import com.liante.MingtdUnit;
import com.liante.network.UnitInventoryPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;

public class UnitSpawner {
    public enum DefenseUnit {
        // 우선순위 예시: 법사(10) > 궁수(5) > 도적(3) > 전사(1)
        WARRIOR("warrior", "§f[전사]", Items.IRON_SWORD, 8.0f, 4.0f, 1, 0),
        ARCHER("archer", "§e[궁수]", Items.BOW, 5.0f, 16.0f, 5, 0),
        MAGE("mage", "§b[법사]", Items.STICK, 12.0f, 12.0f, 10, 0),
        ROGUE("rogue", "§6[도적]", Items.IRON_AXE, 6.0f, 6.0f, 3, 0),

        // --- MAGIC 등급 (아이디와 디스플레이 네임에 등급 강조) ---
        MAGIC_WARRIOR("magic_warrior", "§9[매직 전사]", Items.DIAMOND_SWORD, 18.0f, 4.0f, 2, 1),
        MAGIC_ARCHER("magic_archer", "§9[매직 궁수]", Items.CROSSBOW, 12.0f, 18.0f, 6, 1),
        MAGIC_MAGE("magic_mage", "§9[매직 법사]", Items.BLAZE_ROD, 25.0f, 12.0f, 11, 1),
        MAGIC_ROGUE("magic_rogue", "§9[매직 도적]", Items.IRON_SWORD, 15.0f, 6.0f, 4, 1);

        private final String id;
        private final String displayName;
        private final Item defaultItem;
        private final float damage;
        private final float range;
        private final int priority; // 우선순위 필드 추가
        private final int rank;

        DefenseUnit(String id, String displayName, Item defaultItem, float damage, float range, int priority, int rank) {
            this.id = id;
            this.displayName = displayName;
            this.defaultItem = defaultItem;
            this.damage = damage;
            this.range = range;
            this.priority = priority;
            this.rank = rank;
        }

        public Item getMainItem() { return this.defaultItem; }
        public String getDisplayName() { return this.displayName; }
        public float getDamage() { return this.damage; }
        public float getRange() { return this.range; }

        // [해결] Getter 메서드 추가
        public int getPriority() { return this.priority; }
        public String getId() { return this.id; }
        public int getRank() { return this.rank; }

        public static DefenseUnit fromId(String id) {
            for (DefenseUnit unit : values()) {
                if (unit.id.equals(id)) {
                    return unit;
                }
            }
            throw new IllegalArgumentException("Unknown DefenseUnit ID: " + id);
        }
    }

    public static void spawnRandomUnit(ServerPlayerEntity player, ServerWorld world) {
        DefenseState state = DefenseState.getServerState(world);
        if (!state.consumeWisp(world, 1)) {
            player.sendMessage(Text.literal("§c위습이 부족합니다!"), true);
            return;
        }

        // 1. 일반 등급(Rank 0)인 유닛들만 모으기
        List<DefenseUnit> normalUnits = Arrays.stream(DefenseUnit.values())
                .filter(unit -> unit.getRank() == 0)
                .toList();

        if (normalUnits.isEmpty()) return;

        // 2. 일반 등급 중에서 랜덤 선택
        DefenseUnit selectedUnit = normalUnits.get(world.random.nextInt(normalUnits.size()));
        MingtdUnit unitEntity = Mingtd.MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);

        if (unitEntity != null) {
            double spawnX = Mingtd.SPAWN_POS.getX() + 0.5;
            double spawnY = Mingtd.SPAWN_POS.getY() + 1.0;
            double spawnZ = Mingtd.SPAWN_POS.getZ() + 0.5;

            unitEntity.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0, 0);
            // 1. 직업 데이터 주입 (내부에서 아이템 장착 + AI 갱신 수행)
            unitEntity.setUnitType(selectedUnit);

            // 2. 이름 설정
            unitEntity.setCustomName(Text.literal(selectedUnit.getDisplayName()));
            unitEntity.setCustomNameVisible(true);
            unitEntity.setOwnerUuid(player.getUuid()); // 주인 각인

            // 3. 월드에 소환
            world.spawnEntity(unitEntity);


            UnitInventoryPayload.sendSync(player);

            player.sendMessage(Text.literal(selectedUnit.getDisplayName() + "§a 유닛이 소환되었습니다!"), true);
        }
    }

    public static void spawnSpecificUnit(ServerPlayerEntity player, ServerWorld world, DefenseUnit unitType, BlockPos pos) {
        // Mingtd.MINGTD_UNIT_TYPE는 본인의 메인 클래스에 등록된 EntityType 변수명으로 확인하세요!
        MingtdUnit unitEntity = Mingtd.MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);

        if (unitEntity != null) {
            // 소환 위치 설정 (발 밑 블록 중심을 위해 +0.5)
            double spawnX = Mingtd.SPAWN_POS.getX() + 0.5;
            double spawnY = Mingtd.SPAWN_POS.getY() + 1.0;
            double spawnZ = Mingtd.SPAWN_POS.getZ() + 0.5;

            unitEntity.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0, 0);

            // 1. 유닛 데이터 주입 (Enum 정보 전달)
            unitEntity.setUnitType(unitType);

            // 2. 이름 설정
            unitEntity.setCustomName(Text.literal(unitType.getDisplayName()));
            unitEntity.setCustomNameVisible(true);
            unitEntity.setOwnerUuid(player.getUuid()); // 주인 각인

            // 3. 월드 스폰
            world.spawnEntity(unitEntity);

            // [로그] 승급 결과 로그 출력
            // LOGGER.info("[MingtdUpgrade] 승급 소환: " + unitType.getDisplayName());
        }
    }
}
