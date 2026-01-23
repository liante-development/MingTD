package com.liante.config;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static com.liante.map.MapGenerator.SCREEN_LEFT;
import static com.liante.map.MapGenerator.SCREEN_TOP;

public class DefenseConfig {
    // [시스템 공통 설정]
    public static final int GROUND_Y = 100;
    public static final int PATH_WIDTH = 2;   // 길 폭
    public static final int PATH_RANGE = 13;  // 경로 중심선 거리 (±13)
    public static final int MAP_SIZE = 25;    // 각 플레이어별 할당 영역 크기

    public static double CAMERA_HEIGHT = 45.0;
    public static float CAMERA_PITCH = 60.0f;

    // [반시계 방향: 파랑 -> 초록 -> 노랑 -> 빨강]
    public static final List<Vec3d> WAYPOINTS = List.of(
            new Vec3d(13.0, 100, 12.5),   // 0. 파랑 (왼쪽 위)
            new Vec3d(12.5, 100, 0.0),    // 1. 왼쪽 중앙
            new Vec3d(12.5, 100, -13.0),  // 2. 초록 (왼쪽 아래)
            new Vec3d(0.0, 100, -12.5),   // 3. 아래쪽 중앙
            new Vec3d(-13.0, 100, -12.5), // 4. 노랑 (오른쪽 아래)
            new Vec3d(-12.5, 100, 0.0),   // 5. 오른쪽 중앙
            new Vec3d(-12.5, 100, 13.0),  // 6. 빨강 (오른쪽 위)
            new Vec3d(0.0, 100, 12.5)     // 7. 위쪽 중앙 (이 값이 0.0, 12.5라면 X축 중앙에서 꺾음)
    );

    // [4인용 위치 오프셋] - 나중에 4인용 구현 시 사용
    // 0: 플레이어1(북), 1: 플레이어2(남) ... 이런 식으로 관리 가능
    public static final BlockPos[] PLAYER_OFFSETS = {
            new BlockPos(0, 0, 0),       // 중앙 또는 1번 구역
            new BlockPos(100, 0, 0),     // 2번 구역
            new BlockPos(0, 0, 100),     // 3번 구역
            new BlockPos(100, 0, 100)    // 4번 구역
    };

    public static Vec3d getSpawnLocation(BlockPos origin) {
        int r = DefenseConfig.PATH_RANGE;
        // 아까 성공한 시작점 계산식 그대로 사용
        double x = origin.getX() + ((r - 0.5) * SCREEN_LEFT);
        double z = origin.getZ() + ((r - 0.5) * SCREEN_TOP);
        return new Vec3d(x, DefenseConfig.GROUND_Y, z);
    }
}
