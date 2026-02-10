package com.liante.manager;

import com.liante.MingtdUnit;
import com.liante.network.UnitInventoryPayload;
import com.liante.recipe.UpgradeRecipe;
import com.liante.recipe.UpgradeRecipeLoader;
import com.liante.spawner.UnitSpawner;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.stream.Collectors;

public class UpgradeManager {

    /**
     * 클라이언트가 보낸 재료 리스트(ingredientIds)를 기반으로 합성을 시도합니다.
     */
    public static void tryUpgrade(ServerPlayerEntity player, String recipeId, int mainUnitId, List<Integer> ingredientIds) {
        UpgradeRecipe recipe = UpgradeRecipeLoader.RECIPES.get(recipeId);
        ServerWorld world = player.getEntityWorld();
        Entity mainUnit = world.getEntityById(mainUnitId);

        // 1. 기본 검증 (레시피 및 메인 유닛 존재 여부)
        if (recipe == null || mainUnit == null || !mainUnit.isAlive()) {
            player.sendMessage(Text.literal("§c오류: 합성 대상이 올바르지 않습니다."), true);
            return;
        }

        // 2. 재료 유효성 검증 (보안: 클라이언트가 보낸 ID들이 실제로 존재하고 내 소유인지 확인)
        List<Entity> targetsToRemove = new ArrayList<>();
        for (int id : ingredientIds) {
            Entity entity = world.getEntityById(id);
            // 내 유닛이고, 살아있으며, 메인 유닛과 다른 개체인지 확인
            if (entity instanceof MingtdUnit unit &&
                    unit.getOwnerUuid().equals(player.getUuid()) &&
                    unit.isAlive() &&
                    unit != mainUnit) {
                targetsToRemove.add(unit);
            }
        }

        // 레시피 요구량과 클라이언트가 보낸 유효 재료 수가 일치하는지 확인
        // (레시피 ingredients의 총합과 비교하거나, 각 타입별 개수 검증 가능)
        if (isSelectionValid(recipe, targetsToRemove, mainUnit)) {
            executeUpgrade(player, world, recipe, mainUnit, targetsToRemove);
        } else {
            player.sendMessage(Text.literal("§c합성 실패: 선택된 재료가 레시피와 일치하지 않습니다."), true);
        }
    }

    /**
     * 클라이언트가 보낸 재료들이 레시피 요구 사항을 충족하는지 검사
     */
    private static boolean isSelectionValid(UpgradeRecipe recipe, List<Entity> targets, Entity mainUnit) {
        Map<String, Integer> counts = new HashMap<>();

        // 메인 유닛 종류 카운트
        String mainId = ((MingtdUnit)mainUnit).getUnitId();
        counts.put(mainId, 1);

        // 보낸 재료들 종류 카운트
        for (Entity e : targets) {
            String id = ((MingtdUnit)e).getUnitId();
            counts.put(id, counts.getOrDefault(id, 0) + 1);
        }

        // 레시피와 대조
        for (var entry : recipe.ingredients().entrySet()) {
            if (counts.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    /**
     * 실제 유닛 제거 및 상위 유닛 생성 로직 (판단 로직 제거됨)
     */
    private static void executeUpgrade(ServerPlayerEntity player, ServerWorld world, UpgradeRecipe recipe, Entity mainUnit, List<Entity> targets) {
        BlockPos spawnPos = mainUnit.getBlockPos();

        try {
            // 1. [목록 기반 제거] 클라이언트가 지목하여 검증된 재료들 즉시 제거
            targets.forEach(Entity::discard);

            // 2. 메인 유닛 제거
            mainUnit.discard();

            // 3. 결과물 생성
            UnitSpawner.DefenseUnit resultEnum = UnitSpawner.DefenseUnit.fromId(recipe.resultId());
            UnitSpawner.spawnSpecificUnit(player, world, resultEnum, spawnPos);

            // 4. 성공 피드백
            triggerUpgradeEffects(player);

        } catch (Exception e) {
            player.sendMessage(Text.literal("§c[MingTD] 합성 엔진 실행 오류"), false);
            e.printStackTrace();
        }
    }

    private static void triggerUpgradeEffects(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§a§l[MingTD] 승급 성공!"), true);
    }
}