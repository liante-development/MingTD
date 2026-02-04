package com.liante;

import com.liante.network.UnitStatPayload;
import com.liante.spawner.UnitSpawner;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

import static com.mojang.text2speech.Narrator.LOGGER;

public class MingtdUnit extends PathAwareEntity implements RangedAttackMob {
    private static final TrackedData<Integer> UNIT_TYPE = DataTracker.registerData(MingtdUnit.class, TrackedDataHandlerRegistry.INTEGER);
    // MingtdUnit.java 내부
    private boolean isManualMoving = false;
    // 1. 유닛 정보를 저장할 필드 추가
    private UnitSpawner.DefenseUnit unitType;

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

    // 4. ID를 바로 가져오는 편의 메서드
    public String getUnitId() {
        UnitSpawner.DefenseUnit type = this.getUnitType();
        if (type != null) {
            return type.getId(); // Enum에 정의한 "normal_warrior" 등을 반환
        }
        return "unknown";
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

        if (!this.getEntityWorld().isClient()) {
            // [원인 해결] 재접속 시 무효화된 target을 실시간으로 복구
            if (this.getTarget() == null || !this.getTarget().isAlive()) {
                float range = this.getUnitType().getRange();
                // 탐색 범위를 로그로 확인하기 위해 변수화
                Box searchBox = this.getBoundingBox().expand(range);

                List<LivingEntity> targets = this.getEntityWorld().getEntitiesByClass(
                        LivingEntity.class,
                        searchBox,
                        entity -> entity.isAlive() && !entity.isSpectator() && entity != this
                );

                // [로그 1] 주변에 살아있는 LivingEntity가 몇 마리나 검출되는지 확인
                if (!targets.isEmpty()) {
//                    LOGGER.info("[MingtdDebug] {} 주변 엔티티 발견: {}마리 (탐색범위: {})",
//                            this.getUnitType().name(), targets.size(), range);
                }

                LivingEntity closest = null;
                double minDistance = Double.MAX_VALUE;

                for (LivingEntity entity : targets) {
                    // [로그 2] 발견된 엔티티의 타입을 출력하여 좀비 판정 로직 확인
                    if (entity.getType() == EntityType.ZOMBIE) {
                        double dist = this.squaredDistanceTo(entity);

                        // [로그 3] 좀비와의 실제 거리 출력
//                        LOGGER.info("[MingtdDebug] 좀비 포착! 거리: {}m", Math.sqrt(dist));

                        if (dist < minDistance) {
                            minDistance = dist;
                            closest = entity;
                        }
                    } else {
                        // 좀비가 아닌 다른 엔티티(다른 유닛 등)가 잡혔을 때
//                        LOGGER.info("[MingtdDebug] 좀비가 아닌 엔티티 무시: {}", entity.getType().getName().getString());
                    }
                }

                if (closest != null) {
                    this.setTarget(closest);
//                    LOGGER.info("[MingtdDebug] === {} 타겟 재포착 성공: {} ===",
//                            this.getUnitType().name(), closest.getName().getString());
                } else if (!targets.isEmpty()) {
                    // 엔티티는 찾았지만 좀비가 하나도 없을 때
//                    LOGGER.warn("[MingtdDebug] 주변에 엔티티는 있으나 유효한 좀비 타겟이 없음");
                }
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
//        LOGGER.info("[MingtdDebug] 타입 설정 및 저장 완료: {}", type.name());
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
//        LOGGER.info("[MingtdDebug] 유닛 타입: {} | 타겟: {}", type.name(), target.getType().getName().getString());

        if (!(this.getEntityWorld() instanceof ServerWorld world)) return;

        float damage = type.getDamage();

        if (type == UnitSpawner.DefenseUnit.WARRIOR || type == UnitSpawner.DefenseUnit.ROGUE) {
            // [로그 2] 근접 직업 분기 진입 확인
//            LOGGER.info("[MingtdDebug] {} 직업 - applyInstantDamage 실행", type.name());
            applyInstantDamage(world, target, type, damage);
        } else {
            // [로그 3] 원거리 직업 분기 진입 및 전달되는 타입 확인
//            LOGGER.info("[MingtdDebug] {} 직업 - spawnHomingProjectile 진입", type.name());
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
            // 화살 전용 유도 설정 (기존 로직 유지)
            this.setupHoming(arrow, target, 1.8F);
            world.spawnEntity(arrow);
        }
        else if (type == UnitSpawner.DefenseUnit.MAGE) {
            // 생성자에서 damage 값을 넘겨주도록 변경
            MingtdMagicMissile missile = new MingtdMagicMissile(world, this, target, damage);

            Vec3d dir = target.getBoundingBox().getCenter().subtract(this.getEyePos()).normalize();
            missile.refreshPositionAndAngles(this.getX() + dir.x, this.getEyeY() + dir.y, this.getZ() + dir.z, this.getYaw(), this.getPitch());

            missile.setVelocity(dir.multiply(1.5));
            world.spawnEntity(missile);

            this.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
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

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        // 클라이언트/서버 상관없이 무조건 로그를 찍어 호출 여부 확인
//        LOGGER.info("[MingTD] interact 호출됨! 클라이언트 여부: " + this.getEntityWorld().isClient());

        if (!this.getEntityWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
//            LOGGER.info("[MingTD] 서버에서 유닛 데이터 전송 시도: " + this.getUnitType().name());
            this.syncUnitStatsToClient(serverPlayer);
            return ActionResult.SUCCESS;
        }
        return super.interact(player, hand);
    }


    public void syncUnitStatsToClient(ServerPlayerEntity player) {
        // 1. Enum에서 기본 정보 가져오기
        UnitSpawner.DefenseUnit unitType = this.getUnitType(); // 현재 유닛의 타입을 반환하는 getter가 필요합니다.

        // 2. 실시간 스탯 계산 (현재는 마나 필드가 없으므로 임시값 100을 사용, 향후 필드 추가 필요)
        float currentMana = 50.0f;  // TODO: 유닛 필드로 mana 추가 필요
        float maxMana = 100.0f;
        float currentDamage = unitType.getDamage(); // 향후 버프 시스템 연동 시 수정
        float attackSpeed = 1.0f;   // 향후 공속 시스템 연동 시 수정

        // 3. 패킷 전송
        ServerPlayNetworking.send(player, new UnitStatPayload(
                this.getId(),
                unitType.getDisplayName(),
                currentMana,
                maxMana,
                currentDamage,
                attackSpeed,
                unitType.name()
        ));
    }
}
