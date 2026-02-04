package com.liante.manager;

import com.liante.MingtdUnit;
import com.liante.recipe.UpgradeRecipe;
import com.liante.spawner.UnitSpawner;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mojang.text2speech.Narrator.LOGGER;

public class UpgradeManager {
    // 등록된 모든 조합법을 담는 리스트
    private static final List<UpgradeRecipe> RECIPES = new ArrayList<>();

    // 1. 조합법 등록 (나중에 JSON 로더가 이 리스트를 채우게 됩니다)
    static {
        // 예시: 전사(Normal) 3마리 -> 전사(Magic)
        RECIPES.add(new UpgradeRecipe(
                "normal_warrior",
                Map.of("normal_warrior", 3),
                "magic_warrior"
        ));

        // 추가 예시: 궁수(Normal) 3마리 -> 궁수(Magic)
        RECIPES.add(new UpgradeRecipe(
                "normal_archer",
                Map.of("normal_archer", 3),
                "magic_archer"
        ));
    }

    // 2. 승급 실행 메인 로직
    public static void tryUpgrade(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld(); // getEntityWorld() 대신 권장되는 방식

        // 1. 현재 필드의 유닛 수량 파악 및 로그 출력
        Map<String, Integer> ownedCounts = UnitSpawner.countPlayerUnits(world);
        LOGGER.info("[MingtdUpgrade] 현재 인식된 유닛 목록: " + ownedCounts.toString());

        // 2. 등록된 모든 조합법을 확인
        for (UpgradeRecipe recipe : RECIPES) {
            if (recipe.canUpgrade(ownedCounts)) {
                LOGGER.info("[MingtdUpgrade] 조합 성공: " + recipe.resultId());
                executeUpgrade(player, world, recipe);
                return;
            } else {
                // 조합 실패 시 어떤 재료가 부족한지 로그로 기록 (디버깅용)
                LOGGER.debug("[MingtdUpgrade] 조합 후보 탈락 (" + recipe.resultId() + "): 재료 부족");
            }
        }

        // 3. 루프를 다 돌았는데도 성공하지 못한 경우 (상세 실패 메시지)
        StringBuilder sb = new StringBuilder("§c조합 실패! 보유 현황: ");
        ownedCounts.forEach((id, count) -> sb.append(String.format("[%s: %d마리] ", id, count)));

        player.sendMessage(Text.literal(sb.toString()), false); // 채팅창에 상세 정보 표시
        player.sendMessage(Text.literal("§e필요한 조합법을 다시 확인해주세요."), true);
    }

    // 3. 실제 엔티티 교체 로직
    private static void executeUpgrade(ServerPlayerEntity player, ServerWorld world, UpgradeRecipe recipe) {
        // 3-1. 재료 엔티티 찾아서 지우기
        List<MingtdUnit> allUnits = world.getEntitiesByClass(MingtdUnit.class,
                player.getBoundingBox().expand(100),
                unit -> unit.isAlive() && unit.getUnitId().equals(recipe.baseId()));

        int removedCount = 0;
        BlockPos lastPos = player.getBlockPos();

        for (MingtdUnit unit : allUnits) {
            // 조합법에서 요구하는 재료 수만큼만 삭제
            int required = recipe.ingredients().getOrDefault(recipe.baseId(), 0);
            if (removedCount < required) {
                lastPos = unit.getBlockPos(); // 마지막 유닛 위치 저장 (그 자리에 소환)
                unit.discard();
                removedCount++;
            }
        }

        // 3-2. 결과물 소환
        // DefenseUnit.fromId()는 이전에 Enum에 만든 메서드입니다.
        UnitSpawner.DefenseUnit resultUnit = UnitSpawner.DefenseUnit.fromId(recipe.resultId());
        UnitSpawner.spawnSpecificUnit(player, world, resultUnit, lastPos);

        player.sendMessage(Text.literal("§b[승급 성공] §f" + resultUnit.getDisplayName()), false);
    }
}
