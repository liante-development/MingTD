package com.liante.client;

import com.liante.MingtdUnit;
import com.liante.network.MoveUnitPayload;
import com.liante.network.SelectUnitPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SelectionManager {
    private final MinecraftClient client;
    private final List<ZombieEntity> selectedZombies = new ArrayList<>();
    private static final Logger LOGGER = LoggerFactory.getLogger("MingTD-RTS");

    // 드래그 상태 관리
    private Vec3d dragStartWorldPos = null;
    private boolean isDragging = false;
    // [추가] 화면 드래그 시작 좌표 변수
    private double startX;
    private double startY;
    // 전역 변수 타입 변경 권장
    private List<LivingEntity> selectedUnits = new ArrayList<>();
    public SelectionManager(MinecraftClient client) {
        this.client = client;
    }

    // 좌클릭 시작: 드래그 시작 지점(월드 좌표) 저장
    public void startDragging(Vec3d worldPos, double screenX, double screenY) {
        // 새로운 드래그 시작 시 기존 리스트의 발광을 클라이언트에서도 즉시 끔
        for (ZombieEntity z : selectedZombies) {
            z.setGlowing(false);
        }
        selectedZombies.clear();

        for (LivingEntity e : selectedUnits) e.setGlowing(false);
        selectedUnits.clear();

        ClientPlayNetworking.send(new SelectUnitPayload(new ArrayList<>()));

        this.dragStartWorldPos = worldPos;
        this.startX = screenX;
        this.startY = screenY;
        this.isDragging = true;
    }

    // 좌클릭 뗌: 드래그 영역 내 유닛 확정
    public void endDragging(Vec3d endWorldPos) {
        if (dragStartWorldPos == null || endWorldPos == null) return;

        double minX = Math.min(dragStartWorldPos.x, endWorldPos.x);
        double minZ = Math.min(dragStartWorldPos.z, endWorldPos.z);
        double maxX = Math.max(dragStartWorldPos.x, endWorldPos.x);
        double maxZ = Math.max(dragStartWorldPos.z, endWorldPos.z);

        // [로그 추가] 박스 크기 계산
        double width = maxX - minX;
        double length = maxZ - minZ;
//        LOGGER.info(String.format("[MingtdBox] 박스 생성 -> 가로(X): %.3f, 세로(Z): %.3f", width, length));


        // Y축 범위는 맵 지면(100) 기준으로 넉넉히 잡습니다.
        double expansion = (width < 0.1 && length < 0.1) ? 0.1 : 0.3; // 단순 클릭 시 1.0 -> 0.1로 축소
        Box selectionBox = new Box(minX, 100.0, minZ, maxX, 105.0, maxZ).expand(expansion);
//        Box selectionBox = new Box(minX, 0.0, minZ, maxX, 320.0, maxZ).expand(0.5);

        // 1. 모든 살아있는 엔티티 탐색 (LivingEntity)
        List<LivingEntity> found = client.world.getEntitiesByClass(
                LivingEntity.class, selectionBox, e -> !e.isRemoved() && e.isAlive());

        // [로그 추가] 결과 확인
//        LOGGER.info(String.format("[MingtdBox] 최종 박스 범위: %s | 잡힌 유닛 수: %d", selectionBox.toString(), found.size()));

        // [추가] 박스 안에 들어온 유닛들의 상세 좌표 로깅
        for (LivingEntity entity : found) {
            // 1. getPos()가 안될 경우 getX(), getY(), getZ()를 직접 사용
            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();

//            LOGGER.info(String.format("[MingtdUnitPos] 발견됨: %s | 위치: [X:%.2f, Y:%.2f, Z:%.2f]",
//                    entity.getName().getString(), x, y, z));
        }


        // 기존 선택 해제
        for (LivingEntity e : selectedUnits) e.setGlowing(false);
        selectedUnits.clear();

        if (!found.isEmpty()) {
            // 2. 우선순위 판별: 아군 유닛(MingtdUnit)이 하나라도 포함되어 있는가?
            boolean hasPlayerUnit = found.stream().anyMatch(e -> e instanceof MingtdUnit);

            if (hasPlayerUnit) {
                // [규칙 1] 내 유닛이 있으면 내 유닛들만 '다중 선택'
                for (LivingEntity e : found) {
                    if (e instanceof MingtdUnit) {
                        e.setGlowing(true);
                        selectedUnits.add(e);
                    }
                }
            } else {
                // [규칙 2] 내 유닛이 없고 몬스터(Zombie)만 있다면 가장 첫 번째 몬스터만 '단일 선택'
                LivingEntity firstMonster = found.get(0);
                if (firstMonster instanceof ZombieEntity) {
                    firstMonster.setGlowing(true);
                    selectedUnits.add(firstMonster);
                }
            }
        }

        // [추가] 최종적으로 선택 리스트에 담긴 유닛들 확인
        if (!selectedUnits.isEmpty()) {
//            LOGGER.info(String.format("[MingtdSelection] 최종 선택 확정: %d마리", selectedUnits.size()));
        }

        // 서버에 선택 정보 동기화 (ID 리스트 전송)
        List<Integer> ids = selectedUnits.stream().map(Entity::getId).toList();
        ClientPlayNetworking.send(new SelectUnitPayload(ids));
    }

    // 우클릭: 이동 명령 전송
    public void issueMoveCommand(Vec3d targetPos) {
        if (selectedUnits.isEmpty()) return;

        for (LivingEntity unit : selectedUnits) {
            // [규칙 3] 아군 유닛(MingtdUnit)일 때만 이동 패킷 전송
            if (unit instanceof MingtdUnit) {
                ClientPlayNetworking.send(new MoveUnitPayload(unit.getId(), targetPos));
            } else {
//                LOGGER.info("몬스터는 이동시킬 수 없습니다.");
            }
        }
    }

    public void clearSelection() {
        for (ZombieEntity z : selectedZombies) z.setGlowing(false);
        selectedZombies.clear();
    }

    public void stopDragging() {
        this.isDragging = false;
        this.dragStartWorldPos = null;
    }

    // SelectionManager.java 클래스 내부에 추가
    public int getSelectedCount() {
        return this.selectedUnits.size();
    }

    public boolean isDragging() { return isDragging; }
    public Vec3d getDragStart() { return dragStartWorldPos; }
    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public Vec3d getDragStartWorldPos() { return dragStartWorldPos; }
}
