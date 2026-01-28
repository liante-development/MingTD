package com.liante;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

public class MingtdUnit extends PathAwareEntity {

    public MingtdUnit(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();    // 자연 디스폰 방지
        this.setSilent(true);    // 사운드 비활성화
    }

    // [중요] 1.21.2 플레이어 수준 속성 설정
    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)      // 플레이어 체력
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)   // 플레이어 속도
                .add(EntityAttributes.ATTACK_DAMAGE, 1.0)    // 기본 공격력
                .add(EntityAttributes.STEP_HEIGHT, 0.6);     // 플레이어와 동일한 턱 오르기 높이
    }

    // AI 설정 비우기 (요청하신 대로 가만히 있게 설정)
    @Override
    protected void initGoals() {
        // 아무런 Goal도 추가하지 않아 움직이지 않습니다.
    }

    // 사운드 제거 오버라이드
    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return null; }

    @Override
    protected SoundEvent getDeathSound() { return null; }
}
