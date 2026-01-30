package com.liante;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class MingtdArrow extends ArrowEntity {
    private LivingEntity target;

    public MingtdArrow(World world, LivingEntity owner, LivingEntity target) {
        super(world, owner, new ItemStack(Items.ARROW), null);
        this.target = target;
        this.setNoGravity(true); // 직선 비행을 위해 중력 제거
    }

    @Override
    public void tick() {
        super.tick();
        // [학습 적용] 서버 사이드 유도 로직
        if (!this.getEntityWorld().isClient() && target != null && target.isAlive()) {
            double dx = target.getX() - this.getX();
            double dy = target.getBodyY(0.5) - this.getY();
            double dz = target.getZ() - this.getZ();

            // 매 틱마다 타겟을 향해 속도 강제 재설정 (90도 꺾기 핵심)
            Vec3d velocity = new Vec3d(dx, dy, dz).normalize().multiply(1.5);
            this.setVelocity(velocity);
            this.velocityDirty = true;
        }
    }
}
