package com.liante.unit;

import com.liante.config.DefenseConfig;

import java.util.List;

// 모든 유닛 데이터의 핵심 구조
public record UnitInfo(
        String id,
        String name,
        Rarity rarity,
        String mainItem,
        float baseDamage,
        float attackSpeed,
        float attackRange,
        DefenseConfig.AttackType attackType,
        List<UnitRecipe> recipes, // 여러 개의 조합법 수용
        List<UnitSkill> skills
) {}

