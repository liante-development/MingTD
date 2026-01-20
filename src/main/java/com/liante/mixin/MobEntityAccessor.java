package com.liante.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEntity.class) // 대상이 될 클래스
public interface MobEntityAccessor {

    // MobEntity 안에 있는 "goalSelector" 필드에 접근하는 Getter 생성
    @Accessor("goalSelector")
    GoalSelector getGoalSelector();

    // MobEntity 안에 있는 "targetSelector" 필드에 접근하는 Getter 생성
    @Accessor("targetSelector")
    GoalSelector getTargetSelector();
}
