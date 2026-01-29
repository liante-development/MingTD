package com.liante;

import com.liante.spawner.UnitSpawner;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
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
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static com.mojang.text2speech.Narrator.LOGGER;

public class MingtdUnit extends PathAwareEntity implements RangedAttackMob {
    private static final TrackedData<Integer> UNIT_TYPE = DataTracker.registerData(MingtdUnit.class, TrackedDataHandlerRegistry.INTEGER);

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

    public void refreshGoals() {
        this.goalSelector.getGoals().clear();
        this.targetSelector.getGoals().clear();
        this.initGoals();
    }

    public void setUnitType(UnitSpawner.DefenseUnit type) {
        this.dataTracker.set(UNIT_TYPE, type.ordinal());
        this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(type.getMainItem()));
        this.refreshGoals();
    }

    public UnitSpawner.DefenseUnit getUnitType() {
        return UnitSpawner.DefenseUnit.values()[this.dataTracker.get(UNIT_TYPE)];
    }

    @Override
    public void shootAt(LivingEntity target, float pullProgress) {
        UnitSpawner.DefenseUnit type = this.getUnitType();
        // [수정] getEntityWorld() 사용 및 ServerWorld 캐스팅
        ServerWorld world = (ServerWorld) this.getEntityWorld();

        if (type == UnitSpawner.DefenseUnit.WARRIOR || type == UnitSpawner.DefenseUnit.ROGUE) {
            applyInstantDamage(world, target, type);
        } else if (type == UnitSpawner.DefenseUnit.ARCHER) {
            spawnHomingArrow(world, target);
        } else if (type == UnitSpawner.DefenseUnit.MAGE) {
            spawnHomingFireball(world, target);
        }

        this.swingHand(Hand.MAIN_HAND);
    }

    private void applyInstantDamage(ServerWorld world, LivingEntity target, UnitSpawner.DefenseUnit type) {
        target.damage(world, world.getDamageSources().mobAttack(this), 5.0F);

        // [수정] getPos() 대신 개별 좌표 메서드 사용
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

    private void spawnHomingArrow(World world, LivingEntity target) {
        ArrowEntity arrow = new ArrowEntity(world, this, new ItemStack(Items.ARROW), null);
        arrow.setNoGravity(true);

        double dx = target.getX() - this.getX();
        double dy = (target.getY() + target.getHeight() * 0.5) - this.getEyeY();
        double dz = target.getZ() - this.getZ();

        arrow.setVelocity(dx, dy, dz, 1.8F, 0.0F);
        world.spawnEntity(arrow);
    }

    private void spawnHomingFireball(World world, LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dy = (target.getY() + target.getHeight() * 0.5) - this.getEyeY();
        double dz = target.getZ() - this.getZ();
        Vec3d dir = new Vec3d(dx, dy, dz).normalize();

        SmallFireballEntity fireball = new SmallFireballEntity(world, this, dir);
        fireball.refreshPositionAndAngles(this.getX(), this.getEyeY(), this.getZ(), this.getYaw(), this.getPitch());
        fireball.setVelocity(dir.x, dir.y, dir.z, 1.5F, 0.0F);

        world.spawnEntity(fireball);
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.NEUTRAL, 1.0F, 1.2F);
    }
}
