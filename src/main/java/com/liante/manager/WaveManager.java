package com.liante.manager;

import com.liante.config.DefenseConfig;
import com.liante.mixin.MobEntityAccessor;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
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

    public WaveManager(ServerWorld world, BlockPos origin) {
        this.world = world;
        this.origin = origin;

        // 객체가 생성될 때 좌표 로그를 즉시 출력
//        logWaypointCoordinates();
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

            // [핵심] 블록 통과 및 물리 무시 설정
            monster.noClip = true;           // 블록 통과 허용
            monster.setNoGravity(true);      // 바닥으로 추락 방지 (noClip 시 필수)
            monster.setInvulnerable(true);   // 블록 안에서 질식사 방지
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

        // [로그 추가] 목적지가 변경되는 시점의 좀비 위치와 새 목적지 출력
//        LOGGER.info(String.format("=== [목적지 변경] 좀비ID:%d ===", monster.getId()));
//        LOGGER.info(String.format("  - 현재 위치: [%.2f, %.2f]", currentX, currentZ));
//        LOGGER.info(String.format("  - 다음 목적지: 지점 %d [%.2f, %.2f]", nextIndex, targetX, targetZ));

        // 만약 이전 목적지에서 너무 멀리서 꺾었다면 이 로그의 '현재 위치'를 보고 판정 거리를 조절할 수 있습니다.

        // 4. 이동 명령 내리기
        // 2026년 API 기준, 네비게이션이 가끔 경로를 못 찾으면 false를 반환하므로 확인 로그를 찍을 수도 있습니다.
        boolean success = monster.getNavigation().startMovingTo(targetX, DefenseConfig.GROUND_Y, targetZ, 1.4D);
        if (!success) {
            LOGGER.warn("  [경고] 좀비가 새 목적지로의 경로를 찾는 데 실패했습니다!");
        }
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
                // 3. [물리 무시 이동] 좌표 직접 세팅 (Teleport 방식의 부드러운 이동)
                // 초당 약 4~5블록 이동 속도 (0.2D)
                double moveSpeed = 0.2;
                monster.setPos(
                        monster.getX() + direction.x * moveSpeed,
                        ty, // Y축 고정 (추락 방지)
                        monster.getZ() + direction.z * moveSpeed
                );

                // 좀비가 가는 방향을 바라보게 설정
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

    // 로그 가독성을 위한 보조 메서드
//    private String getPointColorName(int index) {
//        return switch (index) {
//            case 0 -> "파랑-왼쪽위";
//            case 1 -> "초록-왼쪽아래";
//            case 2 -> "노랑-오른쪽아래";
//            case 3 -> "빨강-오른쪽위";
//            default -> "알 수 없음";
//        };
//    }
//
//    public void logWaypointCoordinates() {
//        int r = DefenseConfig.PATH_RANGE; // 13
//
//        // [A] 논리적으로 계산된 2x2 양털의 실제 정중앙 (우리의 목표 좌표)
//        double targetPos = (double)r;
//        double targetNeg = -(double)r;
//
//        LOGGER.info("=== [2026 MINGTD 좌표 정밀 검증 로그] ===");
//
//        for (int i = 0; i < DefenseConfig.WAYPOINTS.size(); i++) {
//            // [B] 현재 DefenseConfig에 설정되어 실제 이동 명령에 쓰이는 좌표 (현재 값)
//            Vec3d offset = DefenseConfig.WAYPOINTS.get(i);
//            double currentWorldX = origin.getX() + offset.x;
//            double currentWorldZ = origin.getZ() + offset.z;
//
//            // [C] 양털 중심점과 현재 설정값의 오차 계산용 데이터
//            String colorName = getPointColorName(i);
//
//            LOGGER.info(String.format("지점 %d [%s]", i, colorName));
//            LOGGER.info(String.format("  -> 현재 명령 좌표: X=%.2f, Z=%.2f", currentWorldX, currentWorldZ));
//
//            // 참고: i에 따른 이론적 중앙값 출력
//            double idealX = (i == 0 || i == 1) ? targetPos : targetNeg;
//            double idealZ = (i == 0 || i == 3) ? targetPos : targetNeg;
//
//            LOGGER.info(String.format("  -> 양털 중앙 기대값: X=%.1f, Z=%.1f", origin.getX() + idealX, origin.getZ() + idealZ));
//        }
//        LOGGER.info("===========================================");
//    }
//
//    private void logPoint(int i, String name, double x, double z) {
//        LOGGER.info(String.format("지점 %d [%s] -> 월드좌표: X=%.1f, Z=%.1f", i, name, x, z));
//    }
}
