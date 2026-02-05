package com.liante.manager;

import com.liante.MingtdUnit;
import com.liante.recipe.UpgradeRecipe;
import com.liante.recipe.UpgradeRecipeLoader;
import com.liante.spawner.UnitSpawner;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpgradeManager {

    public static void tryUpgrade(ServerPlayerEntity player, String recipeId) {
        UpgradeRecipe recipe = UpgradeRecipeLoader.RECIPES.get(recipeId);
        if (recipe == null) return;

        ServerWorld world = player.getEntityWorld();

        // [수정] UnitSpawner의 countPlayerUnits를 호출 (월드 인자 전달)
        Map<String, Integer> ownedCounts = UnitSpawner.countPlayerUnits(world);

        if (recipe.canUpgrade(ownedCounts)) {
            executeUpgrade(player, world, recipe);
        } else {
            player.sendMessage(Text.literal("§c재료가 부족합니다!"), true);
        }
    }

    private static void executeUpgrade(ServerPlayerEntity player, ServerWorld world, UpgradeRecipe recipe) {
        // 1. 재료 유닛 거리순 소모
        recipe.ingredients().forEach((unitId, count) -> {
            List<MingtdUnit> targets = world.getEntitiesByClass(MingtdUnit.class,
                            player.getBoundingBox().expand(50),
                            unit -> unit.getUnitId().equals(unitId))
                    .stream()
                    .sorted(Comparator.comparingDouble(unit -> unit.distanceTo(player)))
                    .limit(count)
                    .collect(Collectors.toList());

            targets.forEach(MingtdUnit::discard); // 재료 제거
        });

        // 2. [추가 위치] 결과 유닛 생성
        // JSON의 resultId(String)를 DefenseUnit(Enum)으로 변환하여 소환합니다.
        try {
            UnitSpawner.DefenseUnit resultEnum = UnitSpawner.DefenseUnit.fromId(recipe.resultId());
            UnitSpawner.spawnSpecificUnit(player, world, resultEnum, player.getBlockPos());

            // 3. 시각 효과 및 메시지
            triggerUpgradeEffects(player);
        } catch (IllegalArgumentException e) {
            // JSON에 적힌 ID가 Enum에 없을 경우 대비한 예외 처리
            player.sendMessage(Text.literal("§c오류: 존재하지 않는 유닛 ID입니다 (" + recipe.resultId() + ")"), false);
            System.err.println("[MingTD] Invalid resultId in JSON: " + recipe.resultId());
        }
    }

    private static void triggerUpgradeEffects(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§a§l승급 완료!"), true);
    }
}