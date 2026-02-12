package com.liante.manager;

import com.liante.DefenseMonsterEntity;
import com.liante.ModAttributes;
import com.liante.ModEntities;
import com.liante.config.DefenseState;
import com.liante.config.DefenseConfig;
import com.liante.mixin.MobEntityAccessor;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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

//    public void spawnMonster() {
//        ZombieEntity monster = new ZombieEntity(EntityType.ZOMBIE, this.world);
//
//        if (monster != null) {
//            double spawnX = origin.getX() + 12.5;
//            double spawnZ = origin.getZ() + 12.5;
//            monster.refreshPositionAndAngles(spawnX, DefenseConfig.GROUND_Y, spawnZ, 0, 0);
//
//            // AI 초기화 및 설정
//            var accessor = (MobEntityAccessor) monster;
//            accessor.getGoalSelector().clear(goal -> true);
//            accessor.getTargetSelector().clear(goal -> true);
//
//            monster.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
//            // 또는 아예 화염 저항 상태 효과 부여 (더 확실함)
//            monster.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0, false, false));
//
//            // [수정] 현재 클래스에 존재하는 속성만 설정
//            // 1. 넉백 저항: 1.0 (100%)으로 설정하여 화살에 맞아도 뒤로 밀리지 않게 함
//            monster.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
//
//            // 2. 이동 속도: TD 밸런스에 맞춰 고정 (예: 0.23)
//            monster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.23);
//
//
//            monster.noClip = false;
//            monster.setNoGravity(true);
//            monster.setInvulnerable(false);
//            monster.setSilent(true);
//            monster.setAiDisabled(false);
//
//            // 1. 물리 방어력 설정
//            var physAttr = monster.getAttributeInstance(ModAttributes.PHYSICAL_DEFENSE);
//            if (physAttr != null) {
//                physAttr.setBaseValue(10.0); // 기본 물리 방어력 10
//            }
//
//            // 2. 마법 방어력 설정
//            var magAttr = monster.getAttributeInstance(ModAttributes.MAGIC_DEFENSE);
//            if (magAttr != null) {
//                magAttr.setBaseValue(5.0); // 기본 마법 방어력 5
//            }
//
//            // [해결] 팀 설정을 통한 물리적 충돌 제거
//            var scoreboard = world.getScoreboard();
//            String teamName = "defense_mobs";
//            var team = scoreboard.getTeam(teamName);
//
//            if (team == null) {
//                team = scoreboard.addTeam(teamName);
//                // 같은 팀원끼리 절대로 밀치지 않도록 설정 (핵심)
//                team.setCollisionRule(net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER);
//            }
//            team.setNameTagVisibilityRule(net.minecraft.scoreboard.AbstractTeam.VisibilityRule.ALWAYS); // 매번 명시적으로 설정
//            // 좀비를 팀에 가입시킴
//            scoreboard.addScoreHolderToTeam(monster.getNameForScoreboard(), team);
//
//            world.spawnEntity(monster);
//            // 첫 번째 목표 설정
//            setMonsterTarget(monster, 1);
//        }
//    }

    public void spawnMonster() {
        // 1. 기존 ZombieEntity 대신 우리가 만든 전용 몬스터 타입 사용
        DefenseMonsterEntity monster = new DefenseMonsterEntity(ModEntities.DEFENSE_MONSTER_TYPE, this.world);

        if (monster != null) {
            // 위치 설정
            double spawnX = origin.getX() + 12.5;
            double spawnZ = origin.getZ() + 12.5;
            monster.refreshPositionAndAngles(spawnX, DefenseConfig.GROUND_Y, spawnZ, 0, 0);

            var accessor = (MobEntityAccessor) monster;
            // 2. AI 초기화 (전용 엔티티이므로 캐스팅이 더 안전함)
            accessor.getGoalSelector().clear(goal -> true);
            accessor.getTargetSelector().clear(goal -> true);

            // 3. 속성 설정 (DefenseMonsterEntity 클래스에서 기본값을 설정했으므로 바로 사용 가능)
            // 넉백 저항 및 이동 속도
            monster.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            monster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.23);

            // 물리/마법 방어력 설정 (이제 getAttributeInstance가 null을 반환하지 않음)
            monster.getAttributeInstance(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(10.0);
            monster.getAttributeInstance(ModAttributes.MAGIC_DEFENSE).setBaseValue(5.0);

            // 4. 상태 설정 (낮에 타지 않도록 설정했으므로 헬멧/화염저항 불필요)
            monster.noClip = false;
            monster.setNoGravity(true);
            monster.setInvulnerable(false);
            monster.setSilent(true);
            monster.setAiDisabled(false);

            // 5. 팀 설정 (충돌 제거 및 이름표 표시)
            var scoreboard = world.getScoreboard();
            String teamName = "defense_mobs";
            var team = scoreboard.getTeam(teamName);

            if (team == null) {
                team = scoreboard.addTeam(teamName);
                team.setCollisionRule(net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER);
            }
            team.setNameTagVisibilityRule(net.minecraft.scoreboard.AbstractTeam.VisibilityRule.ALWAYS);
            scoreboard.addScoreHolderToTeam(monster.getNameForScoreboard(), team);

            // 엔티티 소환
            world.spawnEntity(monster);

            // 첫 번째 목표 설정 (기존 메서드 호출)
            setMonsterTarget(monster, 1);
        }
    }

    public void setMonsterTarget(DefenseMonsterEntity monster, int pointIndex) {
        // 1. 순환 인덱스 계산 (DefenseConfig에 정의된 웨이포인트 개수 기준)
        int nextIndex = pointIndex % DefenseConfig.WAYPOINTS.size();

        // 2. 인덱스를 엔티티 태그에 저장 (기존 로직 유지 - AI 추적용)
        // 팁: 나중에 monster 클래스 내부에 int targetIndex 필드를 만들면 태그보다 더 빠르게 처리 가능합니다.
        monster.getCommandTags().removeIf(tag -> tag.startsWith(TARGET_INDEX_TAG));
        monster.addCommandTag(TARGET_INDEX_TAG + nextIndex);

        // 3. 목적지 좌표 계산
        Vec3d targetOffset = DefenseConfig.WAYPOINTS.get(nextIndex);
        double targetX = origin.getX() + targetOffset.x;
        double targetZ = origin.getZ() + targetOffset.z;

        // 4. 이동 명령 내리기
        // 속도 계수(1.4D)는 monster의 기본 이동 속도 속성에 곱해집니다.
        boolean success = monster.getNavigation().startMovingTo(targetX, DefenseConfig.GROUND_Y, targetZ, 1.4D);

        // 만약 경로 찾기에 실패했다면, 강제로 위치를 고정하거나 다시 시도하는 로직을 여기에 추가할 수 있습니다.
        //        if (!success) {
//            LOGGER.warn("  [경고] 좀비가 새 목적지로의 경로를 찾는 데 실패했습니다!");
//        }
    }

    // [오류 해결] 엔티티 태그에서 현재 인덱스를 읽어오는 커스텀 메서드
    private int getMonsterTargetIndex(DefenseMonsterEntity monster) {
        // 2. 엔티티에 붙은 모든 태그를 확인
        for (String tag : monster.getCommandTags()) {
            // 3. 우리가 설정한 TARGET_INDEX_TAG로 시작하는 태그를 탐색
            if (tag.startsWith(TARGET_INDEX_TAG)) {
                try {
                    // 4. 태그 뒤의 숫자 부분을 잘라내어 정수로 변환
                    return Integer.parseInt(tag.substring(TARGET_INDEX_TAG.length()));
                } catch (NumberFormatException e) {
                    // 숫자가 아닌 값이 들어왔을 경우 0번(첫 번째) 웨이포인트로 복구
                    return 0;
                }
            }
        }
        // 5. 태그가 아예 없다면 첫 번째 목적지(0)를 반환
        return 0;
    }

    public void tickMonsters(Iterable<DefenseMonsterEntity> monsters) {
        for (DefenseMonsterEntity monster : monsters) {
            if (!monster.isAlive()) continue;

            // 1. 현재 목표 웨이포인트 인덱스 가져오기
            int currentIndex = getMonsterTargetIndex(monster);
            Vec3d targetOffset = DefenseConfig.WAYPOINTS.get(currentIndex);

            double tx = origin.getX() + targetOffset.x;
            double tz = origin.getZ() + targetOffset.z;
            double ty = DefenseConfig.GROUND_Y;

            // 2. 위치 및 방향 계산
            Vec3d currentPos = new Vec3d(monster.getX(), monster.getY(), monster.getZ());
            Vec3d targetPos = new Vec3d(tx, ty, tz);
            Vec3d direction = targetPos.subtract(currentPos).normalize();
            double distance = currentPos.distanceTo(targetPos);

            // 3. 도착 판정 및 다음 타겟 설정
            if (distance < 1.0) {
                setMonsterTarget(monster, currentIndex + 1);
            } else {
                // 이동 속도 설정 (0.2D 수준)
                double moveSpeed = 0.2;
                Vec3d velocity = direction.multiply(moveSpeed);

                // 속도 적용 및 동기화 설정
                monster.setVelocity(velocity);
                monster.velocityDirty = true;

                // 시선 처리 (목적지를 바라보게 함)
                Vec3d lookTarget = new Vec3d(tx, monster.getEyeY(), tz);
                monster.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, lookTarget);
            }

            // 4. 이름표(Health Bar) 업데이트
            // 여기에 방어력 수치도 표시하도록 makeHealthBar를 나중에 고도화할 수 있습니다.
            Text newName = makeHealthBar(monster);

            // 이름 변경 시에만 업데이트 (패킷 절약)
            if (monster.getCustomName() == null || !newName.getString().equals(monster.getCustomName().getString())) {
                monster.setCustomName(newName);
                monster.setCustomNameVisible(true);
            }
        }
    }

    private Text makeHealthBar(DefenseMonsterEntity monster) {
        float health = monster.getHealth();
        float maxHealth = monster.getMaxHealth();
        float healthRatio = Math.max(0, Math.min(1, health / maxHealth));

        int barCount = 10;
        int activeBar = Math.round(healthRatio * barCount);

        // 체력 비율에 따른 색상 (초록 -> 노랑 -> 빨강)
        String healthColor = healthRatio > 0.5 ? "§a" : (healthRatio > 0.2 ? "§e" : "§c");

        // 순수하게 체력바 막대만 생성 (숫자와 방어력 텍스트 제거)
        String barStr = healthColor + "■".repeat(activeBar) + "§7" + "■".repeat(barCount - activeBar);

        return Text.literal(barStr);
    }

    public void updateDummyHealthBar(DefenseMonsterEntity dummy) {
        if (dummy == null || !dummy.isAlive()) return;

        // 기존에 만드신 막대기만 나오는 makeHealthBar 호출
        Text healthBar = makeHealthBar(dummy);

        // 이름표 업데이트 (더미는 이름이 항상 보여야 하므로 true)
        if (dummy.getCustomName() == null || !healthBar.getString().equals(dummy.getCustomName().getString())) {
            dummy.setCustomName(healthBar);
            dummy.setCustomNameVisible(true);
        }
    }
}
