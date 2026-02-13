package com.liante.unit;

import com.liante.recipe.UpgradeRecipe;

import java.util.*;

public class MingtdUnits {
    // ID로 유닛 정보를 찾기 위한 중앙 레지스트리
    public static final Map<String, UnitInfo> UNIT_REGISTRY = new LinkedHashMap<>();
    public static final Map<String, List<UpgradeRecipe>> RECIPES_BY_INGREDIENT = new HashMap<>();

    public static void register(UnitInfo info) {
        UNIT_REGISTRY.put(info.id(), info);
    }

    public static UnitInfo get(String id) {
        return UNIT_REGISTRY.get(id);
    }
}
