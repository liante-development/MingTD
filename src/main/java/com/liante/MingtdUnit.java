package com.liante;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

public class MingtdUnit extends VindicatorEntity {
    public MingtdUnit(EntityType<? extends VindicatorEntity> entityType, World world) {
        super(entityType, world);

        this.setPersistent();    // 자연 디스폰 방지
        this.setSilent(true);    // [추가] 모든 사운드(숨소리, 피격음 등) 비활성화
    }

    // 1. [AI 제거] 모든 목표(AI) 설정을 비워서 가만히 있게 만듭니다.
    @Override
    protected void initGoals() {
        // super.initGoals()를 호출하지 않음으로써 변명자의 기본 공격 AI를 차단합니다.
        // 시민 유닛이므로 아무런 Goal도 추가하지 않습니다.
    }

    // 2. [사운드 제거] 사운드 관련 메서드들을 오버라이드하여 무음 처리합니다.
    @Override
    protected SoundEvent getAmbientSound() {
        return null; // 평소 내는 소리 제거
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null; // 피격 시 소리 제거
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null; // 사망 시 소리 제거
    }

    @Override
    public boolean canJoinRaid() {
        return false;
    }
}
