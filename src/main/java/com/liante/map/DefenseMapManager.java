package com.liante.map;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class DefenseMapManager {
    // 영역 크기 설정
    public static final int PLOT_SIZE = 32;
    public static final int GAP = 4;

    public void generateMap(ServerWorld world, BlockPos startPos) {
        // 1. 좌측 영역 (뽑기/관리 공간) - 금 블록 바닥
        fillArea(world, startPos, PLOT_SIZE, PLOT_SIZE, Blocks.GOLD_BLOCK);

        // 2. 우측 영역 (웨이브/전투 공간) - 돌 벽돌 바닥
        BlockPos battlePos = startPos.east(PLOT_SIZE + GAP);
        fillArea(world, battlePos, PLOT_SIZE, PLOT_SIZE, Blocks.STONE_BRICKS);
    }

    private void fillArea(ServerWorld world, BlockPos origin, int width, int length, Block block) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                world.setBlockState(origin.add(x, 0, z), block.getDefaultState());
            }
        }
    }
}
