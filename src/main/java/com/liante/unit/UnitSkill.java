package com.liante.unit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;

public class UnitSkill {
    private String id;
    private Map<String, Double> params;

    // GSON용 기본 생성자
    public UnitSkill() {}

    public String getId() { return id; }
    public Map<String, Double> getParams() { return params; }

    public void onAttack(Entity owner, LivingEntity target, float damage) {
        if (this.id == null || this.params == null) return;
        if (!(owner.getEntityWorld() instanceof ServerWorld world)) return;

        // 1. 크리티컬 스킬 로직
        if (this.id.equals("critical")) {
            double chance = params.getOrDefault("chance", 0.1);      // 기본 확률 10%
            double multiplier = params.getOrDefault("multiplier", 2.0); // 기본 배율 2배

            if (owner.getEntityWorld().getRandom().nextDouble() < chance) {
                float criticalDamage = (float) (damage * (multiplier - 1.0)); // 추가 데미지 계산

                // 대상에게 추가 데미지 입히기
                target.damage(world, world.getDamageSources().mobAttack((LivingEntity) owner), criticalDamage);

                // 시각적 효과 (파티클 등)
                if (owner.getEntityWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                }

                System.out.println("[MingtdDebug] Critical Hit! Damage: " + (damage + criticalDamage));
            }
        }
    }
}
