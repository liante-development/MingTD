package com.liante.recipe;

import java.util.Map;

public record UpgradeRecipe(String baseId, Map<String, Integer> ingredients, String resultId) {
    // 승급 가능 여부 확인 로직
    public boolean canUpgrade(Map<String, Integer> ownedUnits) {
        for (var entry : ingredients.entrySet()) {
            if (ownedUnits.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
