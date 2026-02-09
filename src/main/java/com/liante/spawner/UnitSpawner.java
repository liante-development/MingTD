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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.text2speech.Narrator.LOGGER;

public class UnitSpawner {
    public enum DefenseUnit {
        // 우선순위 예시: 법사(10) > 궁수(5) > 도적(3) > 전사(1)
        WARRIOR("normal_warrior", "§f[전사]", Items.IRON_SWORD, 8.0f, 4.0f, 1, 0),
        ARCHER("normal_archer", "§e[궁수]", Items.BOW, 5.0f, 16.0f, 5, 0),
        MAGE("normal_mage", "§b[법사]", Items.STICK, 12.0f, 12.0f, 10, 0),
        ROGUE("normal_rogue", "§6[도적]", Items.IRON_AXE, 6.0f, 6.0f, 3, 0),

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
//                if (unit.id.equals(id)) {
//                    return unit;
//                }
                if (unit.id.equalsIgnoreCase(id) || unit.name().equalsIgnoreCase(id)) {
                    return unit;
                }
            }
            // 해당하는 ID가 없을 경우 기본값으로 WARRIOR를 반환하거나 에러 로그를 남깁니다.
            return WARRIOR;
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

//        DefenseUnit selectedUnit = DefenseUnit.values()[world.random.nextInt(DefenseUnit.values().length)];
        MingtdUnit unitEntity = Mingtd.MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);

//        ZombieEntity monster = new ZombieEntity(EntityType.ZOMBIE, world);
////        좀비 소환 테스트
//        if (monster != null) {
//            double spawnX = Mingtd.SPAWN_POS.getX() + 0.5;
//            double spawnY = Mingtd.SPAWN_POS.getY() + 1.0;
//            double spawnZ = Mingtd.SPAWN_POS.getZ() + 0.5;
//
//            monster.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0, 0);
//            // [로그 추가] 소환 정보 출력
//            LOGGER.info(String.format("[MingtdSpawn] 유닛 소환: %s | 위치: [X:%.2f, Y:%.2f, Z:%.2f]",
//                    selectedUnit.getDisplayName(), spawnX, spawnY, spawnZ));
//            // AI 초기화 및 설정
//            var accessor = (MobEntityAccessor) monster;
//            accessor.getGoalSelector().clear(goal -> true);
//            accessor.getTargetSelector().clear(goal -> true);
//
//            monster.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
//            // 또는 아예 화염 저항 상태 효과 부여 (더 확실함)
//            monster.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0, false, false));
//
//            // [수정] 현재 클래스에 존재하는 속성만 설정
//            // 1. 넉백 저항: 1.0 (100%)으로 설정하여 화살에 맞아도 뒤로 밀리지 않게 함
//            monster.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
//
//            // 2. 이동 속도: TD 밸런스에 맞춰 고정 (예: 0.23)
//            monster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0);
//            // 1. 직업 데이터 주입 (내부에서 아이템 장착 + AI 갱신 수행)
////            monster.setUnitType(selectedUnit);
//
//            // 2. 이름 설정
////            monster.setCustomName(Text.literal(selectedUnit.getDisplayName()));
//            monster.setCustomNameVisible(true);
//            monster.noClip = false;
//            monster.setNoGravity(true);
//            monster.setInvulnerable(false);
//            monster.setSilent(true);
//            monster.setAiDisabled(false);
//            // [해결] 팀 설정을 통한 물리적 충돌 제거
//            var scoreboard = world.getScoreboard();
//            String teamName = "defense_mobs";
//            var team = scoreboard.getTeam(teamName);
//
//            if (team == null) {
//                team = scoreboard.addTeam(teamName);
//                // 같은 팀원끼리 절대로 밀치지 않도록 설정 (핵심)
//                team.setCollisionRule(net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER);
//            }
//            team.setNameTagVisibilityRule(net.minecraft.scoreboard.AbstractTeam.VisibilityRule.ALWAYS); // 매번 명시적으로 설정
//            // 좀비를 팀에 가입시킴
//            scoreboard.addScoreHolderToTeam(monster.getNameForScoreboard(), team);
//
//            world.spawnEntity(monster);
//
//            // 3. 월드에 소환
//            world.spawnEntity(monster);
//
//            player.sendMessage(Text.literal("§a 좀비 유닛이 소환되었습니다!"), true);
//        }

        if (unitEntity != null) {
            double spawnX = Mingtd.SPAWN_POS.getX() + 0.5;
            double spawnY = Mingtd.SPAWN_POS.getY() + 1.0;
            double spawnZ = Mingtd.SPAWN_POS.getZ() + 0.5;

            unitEntity.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0, 0);
            // [로그 추가] 소환 정보 출력
//            LOGGER.info(String.format("[MingtdSpawn] 유닛 소환: %s | 위치: [X:%.2f, Y:%.2f, Z:%.2f]",
//                    selectedUnit.getDisplayName(), spawnX, spawnY, spawnZ));

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

    public static Map<String, Integer> countPlayerUnits(ServerWorld world) {
        Map<String, Integer> unitCounts = new HashMap<>();

        // 월드 내의 모든 MingtdUnit 탐색 (1차 개발: 1인 플레이 기준)
        List<MingtdUnit> units = world.getEntitiesByClass(MingtdUnit.class,
                new Box(Mingtd.SPAWN_POS).expand(50), // 소환지 기준 반경 50블록 내
                entity -> true);

        for (MingtdUnit unit : units) {
            String id = unit.getUnitId(); // MingtdUnit에 getUnitId() 메서드가 필요합니다.
            unitCounts.put(id, unitCounts.getOrDefault(id, 0) + 1);
        }
        return unitCounts;
    }

    public static void spawnSpecificUnit(ServerPlayerEntity player, ServerWorld world, DefenseUnit unitType, BlockPos pos) {
        // Mingtd.MINGTD_UNIT_TYPE는 본인의 메인 클래스에 등록된 EntityType 변수명으로 확인하세요!
        MingtdUnit unitEntity = Mingtd.MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);

        if (unitEntity != null) {
            // 소환 위치 설정 (발 밑 블록 중심을 위해 +0.5)
            unitEntity.refreshPositionAndAngles(
                    pos.getX() + 0.5,
                    pos.getY() + 1.0, // 지면보다 약간 위
                    pos.getZ() + 0.5,
                    0, 0
            );

            // 1. 유닛 데이터 주입 (Enum 정보 전달)
            unitEntity.setUnitType(unitType);

            // 2. 이름 설정
            unitEntity.setCustomName(Text.literal(unitType.getDisplayName()));
            unitEntity.setCustomNameVisible(true);

            // 3. 월드 스폰
            world.spawnEntity(unitEntity);

            // [로그] 승급 결과 로그 출력
            // LOGGER.info("[MingtdUpgrade] 승급 소환: " + unitType.getDisplayName());
        }
    }
}
