package com.liante.manager;

import com.liante.spawner.UnitSpawner;
import com.liante.unit.MingtdUnits;
import com.liante.unit.UnitInfo;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public class UpgradeManager {

    /**
     * 클라이언트가 보낸 재료 리스트(ingredientIds)를 기반으로 합성을 시도합니다.
     */
    public static void tryUpgrade(ServerPlayerEntity player, String recipeId, String resultId, int mainUnitId, List<Integer> ingredientIds) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // 1. 결과물 정보 가져오기
        UnitInfo resultInfo = MingtdUnits.UNIT_REGISTRY.get(resultId);
        if (resultInfo == null) return;

        // 2. 메인 유닛 찾아서 위치 저장하고 지우기
        Entity mainUnit = world.getEntityById(mainUnitId);
        if (mainUnit == null) return;

        BlockPos spawnPos = mainUnit.getBlockPos();
        mainUnit.discard();

        // 3. 클라이언트가 보낸 재료 리스트: 있으면 지우고 없으면 통과
        for (int id : ingredientIds) {
            Entity ingredient = world.getEntityById(id);
            if (ingredient != null) {
                ingredient.discard();
            }
        }

        // 4. 결과물 유닛 소환
        try {
            UnitSpawner.spawnSpecificUnit(player, world, resultInfo, spawnPos);

            // 피드백 (이펙트)
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);
            player.sendMessage(Text.literal("§a§l[MingTD] 승급 성공!"), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}