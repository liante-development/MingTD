package com.liante;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class HomingProjectile extends ArrowEntity { // 또는 SmallFireballEntity 상속
    private LivingEntity target;

    public HomingProjectile(World world, LivingEntity owner, LivingEntity target) {
        super(EntityType.ARROW, world);
        this.setOwner(owner);
        this.target = target;
        this.setNoGravity(true); // 유도 성능 극대화를 위해 중력 제거
    }

    @Override
    public void tick() {
        super.tick();

        // 서버 사이드에서만 유도 로직 실행 및 타겟 생존 확인
        if (!this.getEntityWorld().isClient() && target != null && target.isAlive()) {
            // 타겟의 중심 좌표 계산
            double dx = target.getX() - this.getX();
            double dy = target.getBodyY(0.5) - this.getY();
            double dz = target.getZ() - this.getZ();

            // 방향 벡터 정규화 후 속도 부여 (원하는 속도 배율 곱함)
            Vec3d velocity = new Vec3d(dx, dy, dz).normalize().multiply(1.5);
            this.setVelocity(velocity);

            // 화살이 날아가는 방향을 바라보게 설정 (비주얼)
            this.velocityDirty = true; // 서버가 클라이언트에 속도 변화를 강제 동기화하게 함
        }
    }
}
