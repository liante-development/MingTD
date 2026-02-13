package com.liante.config;

import com.liante.DefenseMonsterEntity;
import com.liante.ModAttributes;
import com.liante.unit.UnitInfo;

public class DamageCalculator {

    /**
     * 타워의 정보와 몬스터의 방어력을 계산하여 최종 데미지 산출
     */
    public static float calculate(DefenseMonsterEntity victim, UnitInfo tower) {
        double baseDamage = tower.baseDamage();
        double defense = 0;

        // 1. 공격 타입에 따른 방어력 수치 획득
        if (tower.attackType() == DefenseConfig.AttackType.PHYSICAL) {
            defense = victim.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE);
        } else if (tower.attackType() == DefenseConfig.AttackType.MAGIC) {
            defense = victim.getAttributeValue(ModAttributes.MAGIC_DEFENSE);
        }

        // 2. 방어력 감쇄 공식 적용 (예: 워크래프트 3 방식)
        // 데미지 감소율 = (방어력 * 0.06) / (1 + 방어력 * 0.06)
        // 여기서는 간단한 TD 공식을 적용: Damage * (100 / (100 + 방어력))
        double reduction;
        if (defense >= 0) {
            reduction = 100.0 / (100.0 + defense);
        } else {
            // 방어력이 음수인 경우 (방깎 디버프 등) 데미지 증가
            reduction = 2.0 - (100.0 / (100.0 - defense));
        }

        return (float) (baseDamage * reduction);
    }
}
