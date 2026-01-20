package com.liante.client;

import com.liante.MoveUnitPayload;
import com.liante.SelectUnitPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
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
        ClientPlayNetworking.send(new SelectUnitPayload(new ArrayList<>()));

        this.dragStartWorldPos = worldPos;
        this.startX = screenX;
        this.startY = screenY;
        this.isDragging = true;
    }

    // 좌클릭 뗌: 드래그 영역 내 유닛 확정
    public void endDragging(Vec3d endWorldPos) {
        LOGGER.info("==== 드래그 종료 디버깅 ====");

        if (dragStartWorldPos == null) {
            LOGGER.warn("드래그 시작 지점(dragStartWorldPos)이 null입니다.");
            return;
        }

        if (endWorldPos == null) {
            LOGGER.warn("드래그 종료 지점(endWorldPos)이 null입니다. (지면을 찍지 못함)");
            return;
        }

        // [수정] expand 값을 0.2~0.5 정도로 줄여서 히트박스에 딱 맞게 조정
        // Y축은 좀비의 키(약 2블록)만 커버하도록 설정합니다.
        double minX = Math.min(dragStartWorldPos.x, endWorldPos.x);
        double minZ = Math.min(dragStartWorldPos.z, endWorldPos.z);
        double maxX = Math.max(dragStartWorldPos.x, endWorldPos.x);
        double maxZ = Math.max(dragStartWorldPos.z, endWorldPos.z);

        // Y축 범위를 좀비가 서 있는 바닥(101)부터 머리 위(103)까지만 잡습니다.
        Box selectionBox = new Box(minX, 100.5, minZ, maxX, 103.5, maxZ)
                .expand(0.3); // 약간의 마진만 추가

        LOGGER.info("정밀 SelectionBox 생성: " + selectionBox.toString());

        // 박스 내 좀비 탐색
        List<ZombieEntity> found = client.world.getEntitiesByClass(
                ZombieEntity.class, selectionBox, e -> !e.isRemoved());

        LOGGER.info("탐색된 좀비 수: " + found.size());

        if (found.isEmpty()) {
            // 박스 근처에 좀비가 실제로 있는지 확인하기 위한 추가 디버깅
            List<ZombieEntity> allZombies = client.world.getEntitiesByClass(
                    ZombieEntity.class, selectionBox.expand(10.0), e -> !e.isRemoved());
            LOGGER.info("반경 10블록 이내 전체 좀비 수: " + allZombies.size());
        }

        List<Integer> ids = new ArrayList<>();
        for (ZombieEntity zombie : found) {
            LOGGER.info("선택 성공! 좀비 ID: " + zombie.getUuid().toString().substring(0, 5));
            zombie.setGlowing(true);
            selectedZombies.add(zombie);
            ids.add(zombie.getId());

        }

        // 서버로 선택된 ID 목록 전송
        if (!ids.isEmpty()) {
            ClientPlayNetworking.send(new SelectUnitPayload(ids));
        }

        this.isDragging = false;
        this.dragStartWorldPos = null;
        LOGGER.info("==== 드래그 종료 처리 완료 ====");
    }

    // 우클릭: 이동 명령 전송
    public void issueMoveCommand(Vec3d targetPos) {
        if (selectedZombies.isEmpty()) {
            LOGGER.info("명령을 내릴 좀비가 선택되지 않았습니다.");
            return;
        }

        LOGGER.info(String.format("서버로 이동 명령 전송: 유닛 %d마리 -> 좌표 [%.2f, %.2f, %.2f]",
                selectedZombies.size(), targetPos.x, targetPos.y, targetPos.z));

        for (ZombieEntity zombie : selectedZombies) {
            // 패킷 전송 로그
            ClientPlayNetworking.send(new MoveUnitPayload(zombie.getId(), targetPos));
        }
    }

    public void clearSelection() {
        for (ZombieEntity z : selectedZombies) z.setGlowing(false);
        selectedZombies.clear();
    }

    // SelectionManager.java 클래스 내부에 추가
    public int getSelectedCount() {
        return this.selectedZombies.size();
    }

    public boolean isDragging() { return isDragging; }
    public Vec3d getDragStart() { return dragStartWorldPos; }
    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public Vec3d getDragStartWorldPos() { return dragStartWorldPos; }
}
