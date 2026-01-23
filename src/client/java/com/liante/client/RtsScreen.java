package com.liante.client;

import com.liante.manager.CameraMovePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RtsScreen extends Screen {
    // 1. 로그 기록을 위한 Logger 선언
    private static final Logger LOGGER = LoggerFactory.getLogger("MingTD-RTS");

    private final SelectionManager selectionManager;

    // 클래스 내부에 정적 변수 추가
    public static boolean isChatting = false;

    public RtsScreen() {
        super(Text.literal("RTS Screen"));
        this.selectionManager = new SelectionManager(MinecraftClient.getInstance());
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        Vec3d mouseWorldPos = getMouseWorldPos(click.x(), click.y());
        if (mouseWorldPos == null) return false;

        if (click.button() == 0) { // 좌클릭 시작
            selectionManager.startDragging(mouseWorldPos, click.x(), click.y());
            return true;
        }
        else if (click.button() == 1) { // 우클릭 명령
            selectionManager.issueMoveCommand(mouseWorldPos);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        // 1. 좌클릭(0) 버튼이 떼어졌을 때
        if (click.button() == 0) {
            // 드래그 종료를 위해 현재 마우스 위치의 월드 좌표 계산
            Vec3d mouseWorldPos = getMouseWorldPos(click.x(), click.y());

            if (mouseWorldPos != null) {
                // SelectionManager에 드래그 종료 알림 및 유닛 선택 확정
                selectionManager.endDragging(mouseWorldPos);
            }

            // 화면 자체의 드래그 상태 해제 (상위 클래스 필드)
            this.setDragging(false);
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public void tick() {
        super.tick();

        if (!isChatting && client != null && client.player != null) {
            float moveSpeed = 0.8f;
            float verticalSpeed = 0.5f;
            float dx = 0, dy = 0, dz = 0;

            net.minecraft.client.util.Window window = client.getWindow();

            // 방향키 (X, Z축)
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W)) dz += moveSpeed;
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S)) dz -= moveSpeed;
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_A)) dx += moveSpeed;
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_D)) dx -= moveSpeed;

            // 높낮이 (Y축) - Left Alt: 상승 / Left Control: 하강
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT)) dy += verticalSpeed;
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)) dy -= verticalSpeed;

            if (dx != 0 || dy != 0 || dz != 0) {
                ClientPlayNetworking.send(new CameraMovePayload(dx, dy, dz));
            }
        }
    }

    // 마우스 커서 위치의 '지면' 좌표를 구하는 유틸리티
    private Vec3d getMouseWorldPos(double mouseX, double mouseY) {
        Camera camera = client.gameRenderer.getCamera();
        Vec3d rayDir = getRayFromClick(mouseX, mouseY);
        Vec3d start = camera.getCameraPos();
        Vec3d end = start.add(rayDir.multiply(200.0));

        // 1. 카메라 정보 및 레이 방향 설정
        Vec3d cameraPos = camera.getCameraPos();
        // camera 정보 로깅 추가
        LOGGER.info(String.format("카메라 위치: [X:%.2f, Y:%.2f, Z:%.2f]",
                cameraPos.x, cameraPos.y, cameraPos.z));
        LOGGER.info(String.format("카메라 회전: Yaw=%.2f, Pitch=%.2f",
                camera.getYaw(), camera.getPitch()));
        LOGGER.info(String.format("마우스 좌표: X=%f, Y=%f", mouseX, mouseY));
        LOGGER.info(String.format("레이 방향: [X:%.3f, Y:%.3f, Z:%.3f]",
                rayDir.x, rayDir.y, rayDir.z));
        LOGGER.info("3인칭 모드: " + camera.isThirdPerson());
        // 카메라 전방 벡터 확인
        Vector3f forward = (Vector3f) camera.getHorizontalPlane();
        LOGGER.info(String.format("전방 벡터: [X:%.3f, Y:%.3f, Z:%.3f]",
                forward.x(), forward.y(), forward.z()));

        HitResult hit = client.world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, client.player));

        return hit.getType() == HitResult.Type.BLOCK ? hit.getPos() : null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 배경 제외 렌더링
        renderRtsHud(context);

        // 드래그 박스 시각화 (선택 사항)
        if (selectionManager.isDragging()) {
            renderSelectionBox(context, mouseX, mouseY);
        }
    }

    // 1. 드래그 박스 렌더링 (마우스 클릭 시작점부터 현재 커서까지 사각형 그리기)
    private void renderSelectionBox(DrawContext context, int mouseX, int mouseY) {
        if (this.selectionManager.getDragStart() == null) return;

        // 드래그 시작 지점의 '화면 좌표'가 필요합니다.
        // 하지만 현재 selectionManager는 '월드 좌표'를 저장하고 있으므로,
        // 가장 간단한 방법은 mouseClicked 시점의 screenX, screenY를 저장해두는 것입니다.
        // (아래 팁 섹션의 '드래그 좌표 수정' 참고)
    }

    // 2. RTS 하단/상단 HUD 렌더링
    private void renderRtsHud(DrawContext context) {
        int width = this.client.getWindow().getScaledWidth();
        int height = this.client.getWindow().getScaledHeight();

        // 상단 검은색 바 (상태 표시용)
        context.fill(0, 0, width, 25, 0x88000000);
        context.drawTextWithShadow(this.textRenderer, "MingTD RTS Mode", 10, 8, 0xFFFFFF);

        // 선택된 유닛 수 표시
        int selectedCount = selectionManager.getSelectedCount();
        context.drawTextWithShadow(this.textRenderer, "Units Selected: " + selectedCount, width - 120, 8, 0x00FF00);

        // 하단 조작 가이드
        context.drawTextWithShadow(this.textRenderer, "[L-Click] Select / [R-Click] Move", 10, height - 20, 0xAAAAAA);
    }


//    public RtsScreen() {
//        super(Text.literal("RTS Control Screen"));
//    }
//
    @Override
    protected void init() {
        super.init();
        // 화면이 열릴 때 로그 출력
        LOGGER.info("RTS 화면이 성공적으로 열렸습니다!");
        if (this.client != null) {
            this.client.mouse.unlockCursor();
            LOGGER.info("마우스 커서 해제 시도 완료");
        }

        // 화면 왼쪽 하단에 리셋 버튼 추가
        this.addDrawableChild(ButtonWidget.builder(Text.literal("게임 리셋"), button -> {
                    // 서버로 리셋 패킷 전송 (MoveUnitPayload 같은 방식으로 커스텀 패킷 정의 필요)
                    // ClientPlayNetworking.send(new ResetGamePayload());

                    // 임시로 명령어를 직접 실행하게 하고 싶다면:
                    if (this.client != null && this.client.player != null) {
                        this.client.player.networkHandler.sendChatCommand("mingtd reset");
                    }
                })
                .dimensions(10, this.height - 30, 80, 20) // 위치 및 크기
                .build());
    }
//
////    @Override
////    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
////        // 화면이 작동 중인지 실시간으로 텍스트 표시 (왼쪽 상단)
////        context.drawTextWithShadow(this.textRenderer, "RTS Mode: Active", 10, 10, 0xFFFFFF);
////        context.drawTextWithShadow(this.textRenderer, "Mouse: " + mouseX + ", " + mouseY, 10, 20, 0xAAAAAA);
////    }
//    @Override
//    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
//        // 아무것도 하지 않음으로써 배경이 검게 변하는 것을 막고 게임 화면을 그대로 노출합니다.
//    }
//
//    @Override
//    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
//        // super.render(context, mouseX, mouseY, delta); // 이 줄이 배경을 검게 만들 수 있으므로 주석 처리하거나 확인이 필요합니다.
//
//        // 화면 작동 여부 확인용 텍스트만 직접 그림
//        context.drawTextWithShadow(this.textRenderer, "RTS MODE: ACTIVE", 10, 10, 0xFFFFFF);
//        context.drawTextWithShadow(this.textRenderer, "Mouse: " + mouseX + ", " + mouseY, 10, 20, 0xAAAAAA);
//    }
//
    // 화면을 닫을 때 다시 마우스를 잠궈주는 것이 매너입니다.
    @Override
    public void close() {
        LOGGER.info("마우스 커서 해제 시도 종료");
        if (this.client != null) {
            this.client.mouse.lockCursor();
        }
        super.close();
    }
//
//    @Override
//    public boolean mouseClicked(Click click, boolean doubled) {
//        if (this.client == null || this.client.player == null || this.client.world == null) return false;
//
//        if (click.button() == 0) { // 좌클릭
//            LOGGER.info("==== RTS 카메라 시점 레이캐스트 시작 ====");
//
//            // 1. 카메라 정보 및 레이 방향 설정
//            Camera camera = this.client.gameRenderer.getCamera();
//            Vec3d cameraPos = camera.getCameraPos();
//            Vec3d mouseRayDir = getRayFromClick(click.x(), click.y());
//
//            // camera 정보 로깅 추가
//            LOGGER.info(String.format("카메라 위치: [X:%.2f, Y:%.2f, Z:%.2f]",
//                    cameraPos.x, cameraPos.y, cameraPos.z));
//            LOGGER.info(String.format("카메라 회전: Yaw=%.2f, Pitch=%.2f",
//                    camera.getYaw(), camera.getPitch()));
//            LOGGER.info(String.format("마우스 좌표: X=%d, Y=%d", click.x(), click.y()));
//            LOGGER.info(String.format("레이 방향: [X:%.3f, Y:%.3f, Z:%.3f]",
//                    mouseRayDir.x, mouseRayDir.y, mouseRayDir.z));
//            LOGGER.info("3인칭 모드: " + camera.isThirdPerson());
//            // 카메라 전방 벡터 확인
//            Vector3f forward = (Vector3f) camera.getHorizontalPlane();
//            LOGGER.info(String.format("전방 벡터: [X:%.3f, Y:%.3f, Z:%.3f]",
//                    forward.x(), forward.y(), forward.z()));
//
//            double maxDistance = 200.0D;
//            Vec3d endPos = cameraPos.add(mouseRayDir.multiply(maxDistance));
//
//            // 2. 일차적인 ProjectileUtil 레이캐스트 시도
//            Box selectionBox = new Box(cameraPos, endPos).expand(2.0D);
//            EntityHitResult entityHit = ProjectileUtil.raycast(
//                    this.client.player,
//                    cameraPos,
//                    endPos,
//                    selectionBox,
//                    entity -> entity instanceof ZombieEntity && !entity.isRemoved(),
//                    maxDistance * maxDistance
//            );
//
//            if (entityHit != null) {
//                LOGGER.info("결과: 좀비 직접 클릭 성공! ID: " + entityHit.getEntity().getUuid().toString().substring(0, 5));
//            } else {
//                // 3. 직접 클릭 실패 시 보정 로직 호출
//                ZombieEntity closest = checkManualSelection(cameraPos, mouseRayDir, selectionBox);
//
//                if (closest != null) {
//                    LOGGER.info("결과: [보정 성공] 근처 좀비 클릭! ID: " + closest.getUuid().toString().substring(0, 5));
//                } else {
//                    // 4. 좀비가 없으면 지형 확인
//                    checkBlockHit(cameraPos, endPos);
//                }
//            }
//
//            LOGGER.info("==== RTS 레이캐스트 종료 ====");
//            return true;
//        }
//        return super.mouseClicked(click, doubled);
//    }
//
//    /**
//     * 레이(Ray)와 엔티티 사이의 거리를 계산하여 가장 가까운 좀비를 반환하는 보정 메서드
//     */
//    private ZombieEntity checkManualSelection(Vec3d cameraPos, Vec3d mouseRayDir, Box selectionBox) {
//        ZombieEntity closestZombie = null;
//        double tolerance = 1.5D; // 클릭 인정 범위 (블록 단위)
//
//        List<ZombieEntity> zombies = this.client.world.getEntitiesByClass(
//                ZombieEntity.class,
//                selectionBox,
//                e -> !e.isRemoved()
//        );
//
//        for (ZombieEntity zombie : zombies) {
//            Vec3d zombiePos = zombie.getBoundingBox().getCenter();
//
//            // 카메라에서 좀비를 향하는 벡터
//            Vec3d targetVec = zombiePos.subtract(cameraPos);
//
//            // 레이 방향 벡터와 좀비 위치 벡터 사이의 수직 거리(Cross Product 이용) 계산
//            // 수식: |dir x target| / |dir|
//            double dist = mouseRayDir.crossProduct(targetVec).length();
//
//            if (dist < tolerance) {
//                tolerance = dist;
//                closestZombie = zombie;
//            }
//        }
//        return closestZombie;
//    }
//
//    /**
//     * 지형 클릭 디버깅용 메서드
//     */
//    private void checkBlockHit(Vec3d start, Vec3d end) {
//        HitResult blockHit = this.client.world.raycast(new net.minecraft.world.RaycastContext(
//                start, end,
//                RaycastContext.ShapeType.VISUAL,
//                RaycastContext.FluidHandling.NONE,
//                this.client.player
//        ));
//
//        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
//            BlockHitResult bhr = (BlockHitResult) blockHit;
//            LOGGER.info("결과: 좀비 없음, 블록 클릭됨: " + bhr.getBlockPos().toShortString());
//        } else {
//            LOGGER.info("결과: 허공 클릭");
//        }
//    }
//
    private Vec3d getRayFromClick(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. 마우스 위치를 화면 비율로 정규화 (-1.0 ~ 1.0)
        float x = (float)((2.0D * mouseX) / client.getWindow().getScaledWidth() - 1.0D);
        float y = (float)(1.0D - (2.0D * mouseY) / client.getWindow().getScaledHeight());

        // 2. 카메라의 회전 정보를 가져옴
        // 마인크래프트의 공식적인 카메라 회전 쿼터니언입니다.
        Quaternionf rotation = client.gameRenderer.getCamera().getRotation();

        // 3. 로컬 방향 벡터 (마우스 위치 투영)
        // FOV를 반영하기 위해 tan(fov/2)를 곱해줍니다.
        float fov = client.options.getFov().getValue().floatValue();
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov / 2.0));
        float aspectRatio = (float)client.getWindow().getScaledWidth() / client.getWindow().getScaledHeight();

        // 마우스 커서 방향의 로컬 벡터 생성
        Vector3f localRay = new Vector3f(
                x * tanHalfFov * aspectRatio,
                y * tanHalfFov,
                -1.0f // 마인크래프트는 -Z가 앞쪽입니다.
        );

        // 4. 카메라 회전(쿼터니언)을 로컬 벡터에 적용하여 월드 방향으로 변환
        rotation.transform(localRay);

        return new Vec3d(localRay.x(), localRay.y(), localRay.z()).normalize();
    }

    // 일부 매핑에서 배경을 어둡게 하는 투명도를 결정하는 메서드
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // 1.21.1 최신 매핑에서 추가된 인게임 배경 렌더링도 비워줍니다.
    @Override
    public void renderInGameBackground(DrawContext context) {
        // 공백 유지
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 배경을 검게 칠하거나 블러를 넣는 기능을 완전히 차단합니다.
        // 아무것도 호출하지 마세요.
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // 1. ESC 키 처리
        // 1. ESC 입력 시 즉시 메뉴창 호출
        if (input.isEscape() && this.shouldCloseOnEsc()) {
            if (this.client != null) {
                // RtsScreen을 닫으면서 동시에 메뉴창을 엽니다.
                // 이렇게 하면 관전자 모드 시점으로 나가지 않고 바로 메뉴가 뜹니다.
                this.client.setScreen(new GameMenuScreen(true));
            }
            return true;
        }

        // 2. 채팅 및 명령어 창 열기
        if (this.client != null) {
            // [수정] matchesKey에 input 객체를 직접 전달합니다.
            if (this.client.options.chatKey.matchesKey(input) ||
                    this.client.options.commandKey.matchesKey(input)) {
                isChatting = true; // 채팅 시작 상태 기록
                // 명령어 키(/)인 경우 "/"를, 아니면 빈 문자열 설정
                String initialText = this.client.options.commandKey.matchesKey(input) ? "/" : "";

                // [확인 필요] ChatScreen의 생성자 인수가 2개인 경우
                // 예: new ChatScreen(Text.literal("제목"), initialText) 또는
                //     new ChatScreen(initialText, 다른인자)
                // 일단 에러가 났던 부분을 임시로 주석 처리하거나, 생성자 정보를 알려주시면 바로 고쳐드립니다.
                this.client.setScreen(new ChatScreen(initialText, false));

                return true;
            }
        }

        return super.keyPressed(input);
    }
}
