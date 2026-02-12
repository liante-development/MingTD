package com.liante;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.World;

public class DefenseMonsterEntity extends HostileEntity {

    public DefenseMonsterEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    // 1. 이 엔티티가 가질 기본 속성 정의 (물리/마법 방어력 포함)
    public static DefaultAttributeContainer.Builder createMonsterAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0) // TD이므로 밀치기 저항 100%
                .add(ModAttributes.PHYSICAL_DEFENSE, 0.0) // 기본 물리 방어력 0
                .add(ModAttributes.MAGIC_DEFENSE, 0.0);   // 기본 마법 방어력 0
    }

    // 추가: 몬스터가 죽었을 때 골드를 지급하는 로직 등을 여기에 넣을 수 있습니다.
}
