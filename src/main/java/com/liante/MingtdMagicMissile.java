package com.liante;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MingtdMagicMissile extends SmallFireballEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("MingtdMagicMissile");
    private final LivingEntity target;
    private final float damage;

    public MingtdMagicMissile(World world, LivingEntity owner, LivingEntity target, float damage) {
        // [수정] 부모 생성자 시그니처 대응: (World, owner, Vec3d)
        // 0, 0, 0 대신 Vec3d.ZERO 객체를 전달해야 합니다.
        super(world, owner, Vec3d.ZERO);
        this.target = target;
        this.damage = damage;
    }

    @Override
    public void tick() {
        // 1. 유도 및 강제 타격 로직 (서버 사이드)
        if (!this.getEntityWorld().isClient() && target != null && target.isAlive()) {
            Vec3d targetCenter = target.getBoundingBox().getCenter();

            // [수정] getPos() 대신 getX(), getY(), getZ()를 조합하여 Vec3d 생성
            Vec3d myPos = new Vec3d(this.getX(), this.getY(), this.getZ());
            Vec3d dir = targetCenter.subtract(myPos).normalize();

            this.setVelocity(dir.multiply(1.5));
            this.velocityDirty = true;

            // [명중 보정] 거리 체크
            double distSq = this.squaredDistanceTo(targetCenter.x, targetCenter.y, targetCenter.z);
            if (distSq < 1.44) {
//                LOGGER.info("[MingtdMagic] 타겟 강제 명중: {}", target.getName().getString());
                this.applyCustomDamage(target);
                return;
            }
        }
        super.tick();
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        // [관통 요건] 조준한 타겟 외 무시
        if (hitResult.getType() == HitResult.Type.BLOCK) return;

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (hitEntity != this.target) return;
        }
        super.onCollision(hitResult);
    }

    private void applyCustomDamage(Entity hitEntity) {
        if (this.getEntityWorld().isClient() || !(hitEntity instanceof LivingEntity victim)) return;

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            Entity owner = this.getOwner();
            if (hitEntity == this.target) {
                DamageSource damageSource = this.getDamageSources().mobAttack((LivingEntity) owner);

                if (hitEntity.damage(serverWorld, damageSource, this.damage)) {
//                    LOGGER.info("[MingtdMagic] 대미지 적용 성공");
                    this.discard();
                } else {
                    this.discard();
                }
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (entityHitResult.getEntity() == this.target) {
            this.applyCustomDamage(entityHitResult.getEntity());
        }
    }
}