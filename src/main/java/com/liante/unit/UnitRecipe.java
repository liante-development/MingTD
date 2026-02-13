package com.liante.unit;

import java.util.Map;

public record UnitRecipe(
        String recipeId,
        Map<String, Integer> ingredients // 예: {"warrior": 2, "archer": 1}
) {}
