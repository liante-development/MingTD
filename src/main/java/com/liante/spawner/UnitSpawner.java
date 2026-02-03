package com.liante.spawner;

import com.liante.DefenseState;
import com.liante.Mingtd;
import com.liante.MingtdUnit;
import com.liante.mixin.MobEntityAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static com.mojang.text2speech.Narrator.LOGGER;

public class UnitSpawner {
    public enum DefenseUnit {
        // 우선순위 예시: 법사(10) > 궁수(5) > 도적(3) > 전사(1)
        WARRIOR("§f[전사]", Items.IRON_SWORD, 8.0f, 4.0f, 1),
        ARCHER("§e[궁수]", Items.BOW, 5.0f, 16.0f, 5),
        MAGE("§b[법사]", Items.STICK, 12.0f, 12.0f, 10),
        ROGUE("§6[도적]", Items.IRON_AXE, 6.0f, 6.0f, 3);

        private final String displayName;
        private final Item defaultItem;
        private final float damage;
        private final float range;
        private final int priority; // 우선순위 필드 추가

        DefenseUnit(String displayName, Item defaultItem, float damage, float range, int priority) {
            this.displayName = displayName;
            this.defaultItem = defaultItem;
            this.damage = damage;
            this.range = range;
            this.priority = priority;
        }

        public Item getMainItem() { return this.defaultItem; }
        public String getDisplayName() { return this.displayName; }
        public float getDamage() { return this.damage; }
        public float getRange() { return this.range; }

        // [해결] Getter 메서드 추가
        public int getPriority() { return this.priority; }
    }

    public static void spawnRandomUnit(ServerPlayerEntity player, ServerWorld world) {
        DefenseState state = DefenseState.getServerState(world);
        if (!state.consumeWisp(world, 1)) {
            player.sendMessage(Text.literal("§c위습이 부족합니다!"), true);
            return;
        }

        DefenseUnit selectedUnit = DefenseUnit.values()[world.random.nextInt(DefenseUnit.values().length)];
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
            LOGGER.info(String.format("[MingtdSpawn] 유닛 소환: %s | 위치: [X:%.2f, Y:%.2f, Z:%.2f]",
                    selectedUnit.getDisplayName(), spawnX, spawnY, spawnZ));

            // 1. 직업 데이터 주입 (내부에서 아이템 장착 + AI 갱신 수행)
            unitEntity.setUnitType(selectedUnit);

            // 2. 이름 설정
            unitEntity.setCustomName(Text.literal(selectedUnit.getDisplayName()));
            unitEntity.setCustomNameVisible(true);

            // 3. 월드에 소환
            world.spawnEntity(unitEntity);

            player.sendMessage(Text.literal(selectedUnit.getDisplayName() + "§a 유닛이 소환되었습니다!"), true);
        }
    }
}
