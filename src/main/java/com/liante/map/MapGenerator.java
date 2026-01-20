package com.liante.map;

import com.liante.mixin.MobEntityAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapGenerator {
    public static final int SIZE = 16;
    public static final int GAP = 5;
    public static final int CLEAR_RADIUS = 40;
    public static final int DIRT_DEPTH = 3; // 흙을 쌓을 높이
    private static final Logger LOGGER = LoggerFactory.getLogger("MingTD-RTS");

    public static void setupDefenseWorld(ServerWorld world, BlockPos pos) {
        // [순서 1] 엔티티 제거를 가장 먼저 수행 (지형을 부수기 전)
        int totalRemoved = 0;
        // 모든 차원의 좀비를 안전하게 제거
        for (ServerWorld anyWorld : world.getServer().getWorlds()) {
            // 박스 범위를 충분히 크게 설정
            Box infiniteBox = new Box(pos).expand(500);
            // 리스트를 먼저 복사한 후 제거해야 ConcurrentModificationException을 방지함
            java.util.List<ZombieEntity> targets = anyWorld.getEntitiesByClass(ZombieEntity.class, infiniteBox, entity -> true);
            for (ZombieEntity zombie : targets) {
                zombie.discard();
                totalRemoved++;
            }
        }
        System.out.println("강제 초기화 완료: 총 " + totalRemoved + "마리의 좀비를 제거했습니다.");


        // 1. 주변 정리 및 기반 생성 (공허화 후 흙 채우기)
        BlockPos corner1 = pos.add(-CLEAR_RADIUS, -DIRT_DEPTH - 1, -CLEAR_RADIUS);
        BlockPos corner2 = pos.add(CLEAR_RADIUS, 20, CLEAR_RADIUS);

        for (BlockPos targetPos : BlockPos.iterate(corner1, corner2)) {
            // 발판이 생성될 높이(pos.getY())보다 낮으면 흙으로 채움
            if (targetPos.getY() < pos.getY()) {
                world.setBlockState(targetPos, Blocks.DIRT.getDefaultState());
            } else {
                // 그 외(위쪽)는 공기로 청소
                world.setBlockState(targetPos, Blocks.AIR.getDefaultState());
            }
        }

        // 2. 좌측 영역 (유닛 공간) - 흙 위에 다이아몬드 발판
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                // 발판 설치
                world.setBlockState(pos.add(x, 0, z), Blocks.DIAMOND_BLOCK.getDefaultState());
            }
        }

        // 3. 우측 영역 (전투 공간) - 흙 위에 석재 벽돌 발판
        BlockPos battlePos = pos.add(SIZE + GAP, 0, 0);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                // 발판 설치
                world.setBlockState(battlePos.add(x, 0, z), Blocks.STONE_BRICKS.getDefaultState());
            }
        }

        // 4. 월드 경계 설정
        WorldBorder border = world.getWorldBorder();
        double centerX = pos.getX() + SIZE + (GAP / 2.0);
        double centerZ = pos.getZ() + (SIZE / 2.0);

        border.setCenter(centerX, centerZ);
        border.setSize(SIZE * 2 + GAP + 10);

        Box resetBox = new Box(pos).expand(100); // 범위를 100으로 확장

        spawnZombies(world, pos);

    }

    public static void spawnZombies(ServerWorld world, BlockPos pos) {
        double centerX = pos.getX() + SIZE + GAP + (SIZE / 2.0);
        double centerZ = pos.getZ() + (SIZE / 2.0);

        for (int i = 0; i < 5; i++) {
            ZombieEntity zombie = EntityType.ZOMBIE.create(world, SpawnReason.MOB_SUMMONED);
            if (zombie != null) {
                zombie.refreshPositionAndAngles(centerX + (i * 1.5D), pos.getY() + 1, centerZ, 0, 0);

                // 이제 정상적으로 인터페이스 캐스팅이 작동합니다.
                // (패키지 경로에 맞게 MobEntityAccessor를 import 하세요)
                MobEntityAccessor accessor = (MobEntityAccessor) zombie;

                accessor.getGoalSelector().clear(goal -> true);
                accessor.getTargetSelector().clear(goal -> true);

                zombie.setAiDisabled(false);
                zombie.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
                zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));

                world.spawnEntity(zombie);
            }
        }
    }
}