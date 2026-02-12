package com.liante.map;

import com.liante.DefenseMonsterEntity;
import com.liante.config.DefenseConfig;
import com.liante.mixin.MobEntityAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MapGenerator {
    public static final int SIZE = 16;
    public static final int GAP = 5;
    public static final int CLEAR_RADIUS = 40;
    public static final int DIRT_DEPTH = 3; // 흙을 쌓을 높이
    private static final Logger LOGGER = LoggerFactory.getLogger("MingTD-RTS");

    // 맵 설정 상수화
    private static final int MAP_RADIUS = 25;    // 전체 흙 평지 반경
    private static final int PATH_RANGE = 13;    // 경로 중심선 (±13)
    private static final int GROUND_Y = 100;     // 지면의 높이 (원하는 높이로 설정)

    // 1. 화면 기준 위치(Anchor)를 상수로 관리하여 혼동 방지
    // [시각적 보정] 오른쪽 위(북동)에 생기는 현상을 해결하기 위해 상수를 반전
    public static final int SCREEN_LEFT = 1;    // 왼쪽 (X+)
    public static final int SCREEN_RIGHT = -1;   // 오른쪽 (X-)
    public static final int SCREEN_TOP = 1;     // 위쪽 (Z+) -> 다시 1로 변경
    public static final int SCREEN_BOTTOM = -1;  // 아래쪽 (Z-)

    public static void setupDefenseWorld(ServerWorld world, BlockPos origin) {
        // 모든 좌표 계산 시 origin(기준점)을 더해줌으로써 4인용 위치 대응 가능
        clearAndPrepareTerrain(world, origin);
        clearExistingEntities(world, origin);
        generateTerrain(world, origin);
        generatePath(world, origin);
        setupWorldBorder(world, origin);

        System.out.println("RTS 디펜스 월드 생성 완료 (기준점: " + origin.toShortString() + ")");
    }

    private static void clearAndPrepareTerrain(ServerWorld world, BlockPos origin) {
        // 할당된 맵 크기보다 약간 더 넓게(예: +5) 초기화하여 잔상을 제거합니다.
        int r = DefenseConfig.MAP_SIZE + 5;
        int groundY = DefenseConfig.GROUND_Y;

        // 초기화 범위: 지면 아래 5칸부터 공중 15칸까지
        BlockPos minCorner = origin.add(-r, -5, -r);
        BlockPos maxCorner = origin.add(r, 15, r);

        for (BlockPos pos : BlockPos.iterate(minCorner, maxCorner)) {
            if (pos.getY() < groundY) {
                // 지면 아래는 흙으로 채움 (기존 경로 블록 삭제)
                world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 3);
            } else {
                // 지면 위는 공기로 채움
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

    // 1. 기존 엔티티 제거 (지정한 기준점 주변의 좀비들 청소)
    private static void clearExistingEntities(ServerWorld world, BlockPos origin) {
        int radius = DefenseConfig.MAP_SIZE + 10;
        Box clearBox = new Box(origin).expand(radius, 256, radius);

        List<DefenseMonsterEntity> targets = world.getEntitiesByClass(
                DefenseMonsterEntity.class, clearBox, entity -> true
        );

        for (DefenseMonsterEntity monster : targets) {
            monster.discard();
        }
        System.out.println("기존 유닛 제거 완료: " + targets.size() + "마리");
    }

    /**
     * 2. 기반 지형 생성 (평지화 및 흙 채우기)
     */
    private static void generateTerrain(ServerWorld world, BlockPos origin) {
        int r = DefenseConfig.MAP_SIZE;
        int groundY = DefenseConfig.GROUND_Y;

        // 지형 범위 설정: Y값은 고정된 높이(groundY)를 기준으로 아래는 흙, 위는 공기
        BlockPos minCorner = origin.add(-r, -5, -r);
        BlockPos maxCorner = origin.add(r, 10, r);

        for (BlockPos pos : BlockPos.iterate(minCorner, maxCorner)) {
            BlockState state = (pos.getY() < groundY) ? Blocks.DIRT.getDefaultState() : Blocks.AIR.getDefaultState();

            // 동일한 블록일 경우 교체하지 않도록 최적화 (필요 시)
            if (world.getBlockState(pos) != state) {
                world.setBlockState(pos, state, 3);
            }
        }
    }

    /**
     * 3. 이동 경로(Path) 설치 (화면 기준 좌표 보정)
     */
    private static void generatePath(ServerWorld world, BlockPos origin) {
        BlockState pathBlock = Blocks.SMOOTH_STONE.getDefaultState();
        int r = DefenseConfig.PATH_RANGE; // 13
        int yOffset = -1;

        // 1. 기본 경로 (매끄러운 돌) 설치 - 두께 2칸
        for (int i = -r; i <= r; i++) {
            // 가로 선 (상단/하단)
            setBlock(world, origin.add(i, yOffset, r), pathBlock);
            setBlock(world, origin.add(i, yOffset, r - 1), pathBlock);
            setBlock(world, origin.add(i, yOffset, -r), pathBlock);
            setBlock(world, origin.add(i, yOffset, -(r - 1)), pathBlock);

            // 세로 선 (좌측/우측)
            setBlock(world, origin.add(r, yOffset, i), pathBlock);
            setBlock(world, origin.add(r - 1, yOffset, i), pathBlock);
            setBlock(world, origin.add(-r, yOffset, i), pathBlock);
            setBlock(world, origin.add(-(r - 1), yOffset, i), pathBlock);
        }

        // 2. [검증 단계] 각 모서리에 2x2 양털 설치 (색상별 위치 확인)
        generateCornerMarkers(world, origin, yOffset);
    }

    private static void generateCornerMarkers(ServerWorld world, BlockPos origin, int yOffset) {
        int r = DefenseConfig.PATH_RANGE; // 예: 13
        int y = DefenseConfig.GROUND_Y - 1;

        // [1] 왼쪽 위 (파란색 양털 위치 확인됨: +X, +Z)
        // 좌표: (origin.X + 12, origin.Z + 12)
        markCorner(world, origin.add(r - 1, yOffset, r - 1), Blocks.BLUE_WOOL.getDefaultState(), 1, 1);

        // [2] 오른쪽 위 (빨간색 양털 위치 확인됨: -X, +Z)
        // 좌표: (origin.X - 12, origin.Z + 12)
        markCorner(world, origin.add(-(r - 1), yOffset, r - 1), Blocks.RED_WOOL.getDefaultState(), -1, 1);

        // [3] 오른쪽 아래 (노란색 양털 위치 확인됨: -X, -Z)
        // 좌표: (origin.X - 12, origin.Z - 12)
        markCorner(world, origin.add(-(r - 1), yOffset, -(r - 1)), Blocks.YELLOW_WOOL.getDefaultState(), -1, -1);

        // [4] 왼쪽 아래 (초록색 양털 위치 확인됨: +X, -Z)
        // 좌표: (origin.X + 12, origin.Z - 12)
        markCorner(world, origin.add(r - 1, yOffset, -(r - 1)), Blocks.GREEN_WOOL.getDefaultState(), 1, -1);
    }

    /**
     * 특정 지점에 2x2 양털을 설치하는 편의 메서드
     * @param pos 시작점 (구석 칸)
     * @param state 양털 블록
     * @param dx 확장 방향 X (1 또는 -1)
     * @param dz 확장 방향 Z (1 또는 -1)
     */
    private static void markCorner(ServerWorld world, BlockPos pos, BlockState state, int dx, int dz) {
        world.setBlockState(pos, state, 3);
        world.setBlockState(pos.add(dx, 0, 0), state, 3);
        world.setBlockState(pos.add(0, 0, dz), state, 3);
        world.setBlockState(pos.add(dx, 0, dz), state, 3);
    }

    // 4. 월드 경계 설정 (각 구역의 중앙 기준)
    private static void setupWorldBorder(ServerWorld world, BlockPos origin) {
        WorldBorder border = world.getWorldBorder();
        // 4인용 확장 시에는 보더를 하나로 크게 잡거나, 플레이어별 카메라 클램핑 로직으로 대체해야 합니다.
        // 현재는 1인용 기준 중앙(origin)으로 설정합니다.
//        border.setCenter(origin.getX(), origin.getZ());
//        border.setSize((DefenseConfig.MAP_SIZE * 2) + 2);
        // 보더 크기를 최대치로 설정하여 제한을 사실상 없앱니다.
        border.setSize(5.9999968E7);
        System.out.println("월드 보더 제한 해제 완료");
    }

    // 블록 설치 편의 메서드 (DefenseConfig.GROUND_Y 강제 적용)
    private static void setBlock(ServerWorld world, BlockPos pos, BlockState state) {
        // 1. 전달받은 pos의 좌표를 그대로 사용하되,
        // 2. 성능과 클라이언트 동기화를 위해 플래그(3)를 추가합니다.
        if (world.isInBuildLimit(pos)) {
            world.setBlockState(pos, state, 3);
        }
    }

//    public static void setupDefenseWorld(ServerWorld world, BlockPos pos) {
//        // 1. 엔티티 강제 초기화
//        clearExistingZombies(world);
//
//        // 2. 기반 지형 생성 (평지화)
//        // 지면 높이(GROUND_Y)를 기준으로 아래는 흙, 위는 공기로 채움
//        BlockPos corner1 = new BlockPos(-MAP_RADIUS, GROUND_Y - 5, -MAP_RADIUS);
//        BlockPos corner2 = new BlockPos(MAP_RADIUS, GROUND_Y + 10, MAP_RADIUS);
//
//        for (BlockPos targetPos : BlockPos.iterate(corner1, corner2)) {
//            if (targetPos.getY() < GROUND_Y) {
//                world.setBlockState(targetPos, Blocks.DIRT.getDefaultState());
//            } else {
//                world.setBlockState(targetPos, Blocks.AIR.getDefaultState());
//            }
//        }
//
//        // 3. 이동 경로(Path) 설치 (지면과 높이 일치)
//        // GROUND_Y - 1 위치의 흙을 길 블록으로 교체하여 평지를 유지합니다.
//        BlockState pathBlock = Blocks.SMOOTH_STONE.getDefaultState();
//        BlockState startBlock = Blocks.RED_WOOL.getDefaultState();
//        int pathY = GROUND_Y - 1;
//
//        for (int i = -PATH_RANGE; i <= PATH_RANGE; i++) {
//            // 가로 경로 (Z축 고정)
//            setPathBlock(world, i, pathY, -(PATH_RANGE - 1), pathBlock);
//            setPathBlock(world, i, pathY, -PATH_RANGE, pathBlock);
//            setPathBlock(world, i, pathY, PATH_RANGE - 1, pathBlock);
//            setPathBlock(world, i, pathY, PATH_RANGE, pathBlock);
//
//            // 세로 경로 (X축 고정)
//            setPathBlock(world, -PATH_RANGE, pathY, i, pathBlock);
//            setPathBlock(world, -(PATH_RANGE - 1), pathY, i, pathBlock);
//            setPathBlock(world, PATH_RANGE, pathY, i, pathBlock);
//            setPathBlock(world, PATH_RANGE - 1, pathY, i, pathBlock);
//        }
//
//        // 4. 북서쪽 시작 지점 표시 (-13, -13 부근)
//        world.setBlockState(new BlockPos(-PATH_RANGE, pathY, -PATH_RANGE), startBlock);
//        world.setBlockState(new BlockPos(-(PATH_RANGE - 1), pathY, -(PATH_RANGE - 1)), startBlock);
//
//        // 5. 월드 경계 설정
//        world.getWorldBorder().setCenter(0, 0);
//        world.getWorldBorder().setSize((MAP_RADIUS * 2) + 2);
//
//        System.out.println(String.format("맵 생성 완료: 반경 %d, 경로 범위 ±%d", MAP_RADIUS, PATH_RANGE));
//    }

    // 블록 설치 편의 메서드
    private static void setPathBlock(ServerWorld world, int x, int y, int z, BlockState state) {
        world.setBlockState(new BlockPos(x, y, z), state);
    }

    private static void clearExistingZombies(ServerWorld world) {
        int totalRemoved = 0;
        for (ServerWorld anyWorld : world.getServer().getWorlds()) {
            java.util.List<DefenseMonsterEntity> targets = anyWorld.getEntitiesByClass(DefenseMonsterEntity.class, new Box(-500, 0, -500, 500, 256, 500), entity -> true);
            for (DefenseMonsterEntity monster : targets) {
                monster.discard();
                totalRemoved++;
            }
        }
        System.out.println("기존 유닛 제거 완료: " + totalRemoved);
    }
}