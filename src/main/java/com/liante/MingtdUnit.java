package com.liante;

import com.liante.spawner.UnitSpawner;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static com.mojang.text2speech.Narrator.LOGGER;

public class MingtdUnit extends PathAwareEntity implements RangedAttackMob {
    private static final TrackedData<Integer> UNIT_TYPE = DataTracker.registerData(MingtdUnit.class, TrackedDataHandlerRegistry.INTEGER);
    // MingtdUnit.java 내부
    private boolean isManualMoving = false;

    public MingtdUnit(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setSilent(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.ATTACK_DAMAGE, 1.0)
                .add(EntityAttributes.STEP_HEIGHT, 0.6);
    }

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true));
        // 모든 직업이 원거리 AI 기반으로 작동
        this.goalSelector.add(1, new ProjectileAttackGoal(this, 0.0D, 20, 10.0F));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isManualMoving) {
            // 경로가 없거나, 목적지에 매우 근접했거나, 내비게이션이 쉬고 있을 때 해제
            if (this.getNavigation().isIdle() || this.getNavigation().getCurrentPath() == null) {
                this.isManualMoving = false;
            }
        }

        // [수정] 모든 world 참조를 getEntityWorld()로 통일
        if (!this.getEntityWorld().isClient() && this.age % 50 == 0) {
            LivingEntity target = this.getTarget();
            if (target != null) {
                LOGGER.info("🔍 타겟: {} | 거리: {}", target.getType().getName().getString(), this.distanceTo(target));
            }
        }
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(UNIT_TYPE, UnitSpawner.DefenseUnit.ARCHER.ordinal());
    }

    public void setUnitType(UnitSpawner.DefenseUnit type) {
        this.dataTracker.set(UNIT_TYPE, type.ordinal());
        this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(type.getMainItem()));

        // [추가] 설정 시점에 DefenseState에 자신의 정보를 명시적으로 저장
        if (!this.getEntityWorld().isClient()) {
            DefenseState state = DefenseState.getServerState((ServerWorld) this.getEntityWorld());
            state.saveUnitInfo(this.getUuid(), type); // 이 메서드가 DefenseState에 있어야 함
        }

        this.refreshGoals();
        LOGGER.info("[MingtdDebug] 타입 설정 및 저장 완료: {}", type.name());
    }

    public UnitSpawner.DefenseUnit getUnitType() {
        return UnitSpawner.DefenseUnit.values()[this.dataTracker.get(UNIT_TYPE)];
    }

    public void refreshGoals() {
        this.goalSelector.getGoals().clear();
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true));

        float currentRange = this.getUnitType().getRange(); // 직업별 사거리

        this.goalSelector.add(1, new ProjectileAttackGoal(this, 1.0D, 20, currentRange) { // 10.0F 대신 적용
            @Override
            public boolean canStart() {
                return !isManualMoving() && super.canStart();
            }
            @Override
            public boolean shouldContinue() {
                return !isManualMoving() && super.shouldContinue();
            }
        });
    }

    @Override
    public void shootAt(LivingEntity target, float pullProgress) {
        UnitSpawner.DefenseUnit type = this.getUnitType();
        // [로그 1] 현재 공격을 시도하는 유닛의 실제 타입 확인
        LOGGER.info("[MingtdDebug] 유닛 타입: {} | 타겟: {}", type.name(), target.getType().getName().getString());

        if (!(this.getEntityWorld() instanceof ServerWorld world)) return;

        float damage = type.getDamage();

        if (type == UnitSpawner.DefenseUnit.WARRIOR || type == UnitSpawner.DefenseUnit.ROGUE) {
            // [로그 2] 근접 직업 분기 진입 확인
            LOGGER.info("[MingtdDebug] {} 직업 - applyInstantDamage 실행", type.name());
            applyInstantDamage(world, target, type, damage);
        } else {
            // [로그 3] 원거리 직업 분기 진입 및 전달되는 타입 확인
            LOGGER.info("[MingtdDebug] {} 직업 - spawnHomingProjectile 진입", type.name());
            spawnHomingProjectile(world, target, type, damage);
        }
        this.swingHand(Hand.MAIN_HAND);
    }

    private void applyInstantDamage(ServerWorld world, LivingEntity target, UnitSpawner.DefenseUnit type, float damage) {
        // 고정값 5.0F 대신 전달받은 damage 변수 사용
        target.damage(world, world.getDamageSources().mobAttack(this), damage);

        double tx = target.getX();
        double ty = target.getBodyY(0.5);
        double tz = target.getZ();

        if (type == UnitSpawner.DefenseUnit.WARRIOR) {
            world.spawnParticles(ParticleTypes.CRIT, tx, ty, tz, 15, 0.2, 0.2, 0.2, 0.5);
            this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
        } else {
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, tx, ty, tz, 1, 0, 0, 0, 0);
            this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
        }
    }

    private void spawnHomingProjectile(ServerWorld world, LivingEntity target, UnitSpawner.DefenseUnit type, float damage) {
        if (type == UnitSpawner.DefenseUnit.ARCHER) {
            MingtdArrow arrow = new MingtdArrow(world, this, target);
            arrow.setDamage(damage);
            this.setupHoming(arrow, target, 1.8F);
            world.spawnEntity(arrow);
        }
        else if (type == UnitSpawner.DefenseUnit.MAGE) {
            double dx = target.getX() - this.getX();
            double dy = target.getBodyY(0.5) - this.getEyeY();
            double dz = target.getZ() - this.getZ();
            Vec3d dir = new Vec3d(dx, dy, dz).normalize();

            SmallFireballEntity fireball = new SmallFireballEntity(world, this, dir);
            fireball.refreshPositionAndAngles(this.getX(), this.getEyeY(), this.getZ(), this.getYaw(), this.getPitch());

            world.spawnEntity(fireball);
        }
    }

    private void setupHoming(ProjectileEntity projectile, LivingEntity target, float speed) {
        // [학습 적용] getX, getY, getZ를 사용하여 타겟과의 벡터 계산
        double dx = target.getX() - this.getX();
        double dy = target.getBodyY(0.5) - this.getEyeY();
        double dz = target.getZ() - this.getZ();

        // 초기 속도 설정
        projectile.setVelocity(dx, dy, dz, speed, 0.0F);
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        DefenseState state = DefenseState.getServerState((ServerWorld) this.getEntityWorld());
        UnitSpawner.DefenseUnit savedType = state.getUnitInfo(this.getUuid());

        // 저장된 데이터가 있을 때만 동기화 수행
        if (savedType != null) {
            this.setUnitType(savedType);
        }
    }

    public void startManualMove(double x, double y, double z, double speed) {
        this.isManualMoving = true;
        this.setTarget(null); // 타겟 초기화
        this.getNavigation().startMovingTo(x, y, z, speed);
    }

    public boolean isManualMoving() {
        return isManualMoving;
    }

    @Override
    public boolean tryAttack(ServerWorld world, Entity target) {
        // 엔진이 시도하는 모든 '근접 물리 타격'을 강제로 취소합니다.
        // 이렇게 하면 오직 shootAt 메서드를 통한 '검기' 대미지만 들어갑니다.
        return false;
    }

    @Override
    public boolean canBeSpectated(ServerPlayerEntity viewer) {
        return super.canBeSpectated(viewer);
    }

    // [수정] 대미지 판정 자체를 조건부로 막기
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // 1. 화살(Projectile)에 의한 대미지 차단
        if (source.getSource() instanceof ProjectileEntity) {
            return false;
        }
        // 2. 좀비(Mob)가 직접 때리는 대미지 차단
        if (source.getAttacker() instanceof HostileEntity) {
            return false;
        }
        return super.damage(world, source, amount);
    }
}
