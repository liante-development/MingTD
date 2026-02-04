package com.liante.manager;

import com.liante.config.DefenseState;
import com.liante.config.DefenseConfig;
import com.liante.mixin.MobEntityAccessor;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaveManager {
    private final ServerWorld world;
    private final BlockPos origin; // 플레이어별 기준점 추가
    // 몬스터의 현재 목적지 인덱스를 관리하기 위한 태그 키
    private static final String TARGET_INDEX_TAG = "target_index";
    private static final Logger LOGGER = LoggerFactory.getLogger("MingTD-RTS");

    // [추가] 현재 웨이브를 저장할 변수
    private int currentWave;

    public WaveManager(ServerWorld world, BlockPos origin) {
        this.world = world;
        this.origin = origin;

        DefenseState state = DefenseState.getServerState(world);
        this.currentWave = state.waveStep;

        // 객체가 생성될 때 좌표 로그를 즉시 출력
//        logWaypointCoordinates();
    }

    // 웨이브 단계가 올라갈 때 호출할 메서드 (예시)
    public void nextWave() {
        this.currentWave++;
        // 서버 저장 데이터도 함께 업데이트
        DefenseState state = DefenseState.getServerState(this.world);
        state.waveStep = this.currentWave;
        state.markDirty(); // 파일 저장 예약
    }

    public void spawnMonster() {
        ZombieEntity monster = new ZombieEntity(EntityType.ZOMBIE, this.world);

        if (monster != null) {
            double spawnX = origin.getX() + 12.5;
            double spawnZ = origin.getZ() + 12.5;
            monster.refreshPositionAndAngles(spawnX, DefenseConfig.GROUND_Y, spawnZ, 0, 0);

            // AI 초기화 및 설정
            var accessor = (MobEntityAccessor) monster;
            accessor.getGoalSelector().clear(goal -> true);
            accessor.getTargetSelector().clear(goal -> true);

            monster.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            // 또는 아예 화염 저항 상태 효과 부여 (더 확실함)
            monster.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0, false, false));

            // [수정] 현재 클래스에 존재하는 속성만 설정
            // 1. 넉백 저항: 1.0 (100%)으로 설정하여 화살에 맞아도 뒤로 밀리지 않게 함
            monster.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

            // 2. 이동 속도: TD 밸런스에 맞춰 고정 (예: 0.23)
            monster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.23);


            monster.noClip = false;
            monster.setNoGravity(true);
            monster.setInvulnerable(false);
            monster.setSilent(true);
            monster.setAiDisabled(false);

            // [해결] 팀 설정을 통한 물리적 충돌 제거
            var scoreboard = world.getScoreboard();
            String teamName = "defense_mobs";
            var team = scoreboard.getTeam(teamName);

            if (team == null) {
                team = scoreboard.addTeam(teamName);
                // 같은 팀원끼리 절대로 밀치지 않도록 설정 (핵심)
                team.setCollisionRule(net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER);
            }
            team.setNameTagVisibilityRule(net.minecraft.scoreboard.AbstractTeam.VisibilityRule.ALWAYS); // 매번 명시적으로 설정
            // 좀비를 팀에 가입시킴
            scoreboard.addScoreHolderToTeam(monster.getNameForScoreboard(), team);

            world.spawnEntity(monster);
            // 첫 번째 목표 설정
            setMonsterTarget(monster, 1);
        }
    }

    public void setMonsterTarget(ZombieEntity monster, int pointIndex) {
        // 1. 순환 인덱스 계산
        int nextIndex = pointIndex % DefenseConfig.WAYPOINTS.size();

        // 2. 현재 좀비의 위치 정보 가져오기 (로그용)
//        double currentX = monster.getX();
//        double currentZ = monster.getZ();

        // 3. 인덱스를 엔티티 태그에 저장 (0~3 반복)
        monster.getCommandTags().removeIf(tag -> tag.startsWith(TARGET_INDEX_TAG));
        monster.addCommandTag(TARGET_INDEX_TAG + nextIndex);

        Vec3d targetOffset = DefenseConfig.WAYPOINTS.get(nextIndex);
        double targetX = origin.getX() + targetOffset.x;
        double targetZ = origin.getZ() + targetOffset.z;

        // 4. 이동 명령 내리기
        // 2026년 API 기준, 네비게이션이 가끔 경로를 못 찾으면 false를 반환하므로 확인 로그를 찍을 수도 있습니다.
        boolean success = monster.getNavigation().startMovingTo(targetX, DefenseConfig.GROUND_Y, targetZ, 1.4D);
//        if (!success) {
//            LOGGER.warn("  [경고] 좀비가 새 목적지로의 경로를 찾는 데 실패했습니다!");
//        }
    }

    // [오류 해결] 엔티티 태그에서 현재 인덱스를 읽어오는 커스텀 메서드
    private int getMonsterTargetIndex(ZombieEntity monster) {
        for (String tag : monster.getCommandTags()) {
            if (tag.startsWith(TARGET_INDEX_TAG)) {
                try {
                    return Integer.parseInt(tag.substring(TARGET_INDEX_TAG.length()));
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
        }
        return 1; // 기본값: 첫 번째 목적지
    }

    public void tickMonsters(Iterable<ZombieEntity> monsters) {
        for (ZombieEntity monster : monsters) {
            if (!monster.isAlive()) continue;

            int currentIndex = getMonsterTargetIndex(monster);
            Vec3d targetOffset = DefenseConfig.WAYPOINTS.get(currentIndex);

            double tx = origin.getX() + targetOffset.x;
            double tz = origin.getZ() + targetOffset.z;
            double ty = DefenseConfig.GROUND_Y; // 고정 높이

            // 1. 방향 및 거리 계산
            Vec3d currentPos = new Vec3d(monster.getX(), monster.getY(), monster.getZ());
            Vec3d targetPos = new Vec3d(tx, ty, tz);
            Vec3d direction = targetPos.subtract(currentPos).normalize();
            double distance = currentPos.distanceTo(targetPos);

            // 2. 도착 판정 (유령 모드이므로 1.0 정도로 타이트하게 가능)
            if (distance < 1.0) {
                setMonsterTarget(monster, currentIndex + 1);
            } else {
                // 1. 이동 속도 설정 (0.2D 수준의 부드러운 이동)
                double moveSpeed = 0.2;
                Vec3d velocity = direction.multiply(moveSpeed);

                // 2. setPos 대신 setVelocity 사용
                // 이렇게 하면 서버와 클라이언트가 '이 방향으로 움직이는 중'임을 공유하여 부드러워집니다.
                monster.setVelocity(velocity);

                // 3. 물리 패킷 동기화를 위해 velocityDirty 설정 (필요 시)
                monster.velocityDirty = true;

                // 시선 처리는 유지
                Vec3d lookTarget = new Vec3d(tx, monster.getEyeY(), tz);
                monster.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, lookTarget);
            }

            // 1. 체력 정보 및 비율 계산
            Text newName = makeHealthBar(monster);

            // 4. [최적화] 이름이 실제로 바뀌었을 때만 패킷 전송
            // monster.getCustomName()이 null일 수 있으므로 주의해서 비교
            if (monster.getCustomName() == null || !newName.getString().equals(monster.getCustomName().getString())) {
                monster.setCustomName(newName);
                monster.setCustomNameVisible(true);
            }
        }
    }

    private Text makeHealthBar(ZombieEntity monster) {
        float health = monster.getHealth();
        float maxHealth = monster.getMaxHealth();
        float healthRatio = Math.max(0, Math.min(1, health / maxHealth));

        int barCount = 10;
        int activeBar = Math.round(healthRatio * barCount);

        // 체력바와 텍스트를 조합
        String barStr = "§a" + "■".repeat(activeBar) + "§7" + "■".repeat(barCount - activeBar);
        return Text.literal(barStr);
    }
}
