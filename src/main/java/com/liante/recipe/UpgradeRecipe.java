package com.liante.recipe;

import com.liante.MingtdUnit;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mojang.text2speech.Narrator.LOGGER;

public record UpgradeRecipe(String baseId, Map<String, Integer> ingredients, String resultId) {
    public String id() { return this.baseId; }

    // [기존] 개수 기반 체크
    public boolean canUpgrade(Map<String, Integer> ownedUnits) {
        LOGGER.info("[MingtdDebug] Checking Recipe: {} (Base: {})", this.resultId(), this.baseId());
        if (ownedUnits == null) {
            LOGGER.warn("[MingtdDebug] canUpgrade failed: ownedUnits is null");
            return false;
        }

        LOGGER.info("[MingtdDebug] === Checking Upgrade Recipe: {} ===", this.resultId());

        for (var entry : ingredients.entrySet()) {
            String reqId = entry.getKey();
            int requiredAmount = entry.getValue();
            int currentlyOwned = ownedUnits.getOrDefault(reqId, 0);

            // 상세 비교 로그 출력
            LOGGER.info("[MingtdDebug]  - ID: {} | Required: {} | Owned: {}",
                    reqId, requiredAmount, currentlyOwned);

            if (currentlyOwned < requiredAmount) {
                LOGGER.info("[MingtdDebug]  -> Result: FALSE (Insufficient '{}')", reqId);
                return false;
            }
        }

        LOGGER.info("[MingtdDebug]  -> Result: TRUE (All ingredients met)");
        return true;
    }

    /**
     * [추가] 실제 엔티티 목록에서 재료로 사용할 Entity ID들을 선별
     * @param ownedList ClientUnitManager에서 관리하는 전체 유닛 객체 리스트
     * @param mainUnitId 제외할 기준 유닛 ID
     */
    public List<Integer> collectIngredientIds(List<MingtdUnit> ownedList, int mainUnitId) {
        List<Integer> resultIds = new ArrayList<>();
        LOGGER.info("[MingtdDebug] UpgradeRecipe collectIngredientIds ");
        for (var entry : ingredients.entrySet()) {
            String reqId = entry.getKey();
            int needed = entry.getValue();

            // 현재 유닛 목록에서 종류가 같고, 살아있으며, 기준 유닛(본인)이 아닌 것 추출
            List<Integer> found = ownedList.stream()
                    .filter(u -> u.getUnitId().equals(reqId))
                    .filter(u -> u.getId() != mainUnitId)
                    .filter(Entity::isAlive)
                    .limit(needed)
                    .map(Entity::getId)
                    .toList();

            resultIds.addAll(found);
        }
        return resultIds;
    }
}
