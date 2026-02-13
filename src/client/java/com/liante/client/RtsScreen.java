package com.liante.client;

import com.liante.DefenseMonsterEntity;
import com.liante.MingtdUnit;
import com.liante.manager.CameraMovePayload;
import com.liante.network.MultiUnitPayload;
import com.liante.network.UnitStatPayload;
import com.liante.network.UpgradeRequestPayload;
import com.liante.recipe.UpgradeRecipe;
import com.liante.unit.MingtdUnits;
import com.liante.unit.UnitInfo;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.liante.unit.MingtdUnits.UNIT_REGISTRY;

public class RtsScreen extends Screen {
    // 1. 로그 기록을 위한 Logger 선언
    private static final Logger LOGGER = LoggerFactory.getLogger("MingTD-RTS");

    private final SelectionManager selectionManager;
    private UnitStatPayload selectedUnit; // 현재 선택된 유닛 정보
    // 다중 선택 유닛 정보를 담을 리스트 (MultiUnitPayload.UnitEntry 사용)
    private List<MultiUnitPayload.UnitEntry> unitList = new ArrayList<>();
    // 현재 포커스된 유닛 인덱스 (Tab 키로 순환)
    private int currentIndex = 0;

    // 클래스 내부에 정적 변수 추가
    public static boolean isChatting = false;

    // 전역 또는 클래스 멤버로 선언하여 관리하면 좋습니다.
    private static final int CARD_W = 70;
    private static final int CARD_H = 28;
    private static final int CARD_SPACING = 4;

    private final int width = client.getWindow().getScaledWidth();
    private final int height = client.getWindow().getScaledHeight();

    // 1. HUD 베이스 설정 (하단 중앙)
    private static final int hudW = 220; // 전체 너비
    private static final int hudH = 54;  // 전체 높이
    private final int hudX = (width / 2) - (hudW / 2); // 중앙 정렬
    private final int hudY = height - hudH - 5;

    public RtsScreen() {
        super(Text.literal("RTS Screen"));
        this.selectionManager = new SelectionManager(MinecraftClient.getInstance());
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
//        LOGGER.info("[MingtdDebug] mouseClicked (Screen): x={}, y={}, button={}",
//                click.x(), click.y(), click.button());
        Vec3d mouseWorldPos = getMouseWorldPos(click.x(), click.y());
        // 1. HUD 영역 클릭 체크 (클릭이 HUD 패널 위라면 조작 무시)
        int centerX = this.width / 2;
        int bottomY = this.height - 60;
        boolean isClickOnHud = click.x() >= centerX - 120 && click.x() <= centerX + 120
                && click.y() >= bottomY;

//        if (isClickOnHud) {
//            return true; // HUD 클릭 시 월드 상호작용 차단
//        }
        // 왼쪽 클릭(0)인 경우에만 처리
        if (click.button() == 0 && this.selectedUnit != null) {
            List<UpgradeRecipe> recipes = getAvailableRecipes(this.selectedUnit.jobKey());
            // renderUpgradeStack에서 사용한 좌표 계산 로직과 동일해야 함
            LOGGER.info("[MingtdDebug] RtsScreen mouseClicked this.selectedUnit.jobKey() :: " + this.selectedUnit.jobKey());

            for (int i = 0; i < recipes.size(); i++) {
                int cardX = getUpgradeCardX(i);
                int cardY = getUpgradeCardY();
                LOGGER.info("[MingtdDebug] Mouse: {}, {} | Card{}: {}, {} ~ {}, {}",
                        click.x(), click.y(), i, cardX, cardY, cardX + CARD_W, cardY + CARD_H);
                // 클릭 위치가 해당 카드 영역 안인지 체크
                if (click.x() >= cardX && click.x() <= cardX + CARD_W &&
                        click.y() >= cardY && click.y() <= cardY + CARD_H) {

                    UpgradeRecipe recipe = recipes.get(i);
                    Map<String, Integer> ownedCounts = ClientUnitManager.getOwnedCounts();

                    // 1. 조합 가능 여부 1차 체크 (숫자 기반)
                    LOGGER.info("[MingtdDebug] RtsScreen mouseClicked recipe.canUpgrade :: " + recipe.canUpgrade(ownedCounts));
                    if (recipe.canUpgrade(ownedCounts)) {
                        int mainId = this.selectedUnit.entityId();

                        // 2. [핵심] 서버에 "얘네 지워줘"라고 말할 재료 ID 리스트 추출
                        List<Integer> ingredientsToBurn = recipe.collectIngredientIds(
                                ClientUnitManager.getOwnedList(),
                                mainId
                        );

                        // 3. 확장된 패킷 구조로 전송
                        ClientPlayNetworking.send(new UpgradeRequestPayload(
                                recipe.recipeId(),
                                recipe.resultId(),
                                mainId,
                                ingredientsToBurn // 서버는 이 목록을 그대로 discard() 함
                        ));
                    }
                    return true;
                }
            }
        }

        if (click.button() == 0) {
            this.selectedUnit = null;
            this.unitList.clear(); // 다중 선택 리스트도 초기화
        }

        // 3. 기존 RTS 조작 로직 실행
//        Vec3d mouseWorldPos = getMouseWorldPos(click.x(), click.y());
        if (mouseWorldPos == null) return false;

        if (click.button() == 0) { // 좌클릭 시작 (드래그)
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
            selectionManager.stopDragging();
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
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_UP)) dz += moveSpeed;
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_DOWN)) dz -= moveSpeed;
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT)) dx += moveSpeed;
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT)) dx -= moveSpeed;

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
        Vec3d start = client.player.getEyePos();
        Vec3d rayDir = getRayFromClick(mouseX, mouseY);
        double maxDistance = 200.0;
        Vec3d end = start.add(rayDir.multiply(maxDistance));

        // 1. 수동 엔티티 관통 판정 (모든 유닛의 히트박스 전수 조사)
        Entity closestEntity = null;
        double minDistance = maxDistance;

        // 주변 엔티티 목록 수집 (범위를 충분히 확장)
        Box searchBox = new Box(start, end).expand(0.3);
        List<Entity> targetEntities = client.world.getEntitiesByClass(Entity.class, searchBox, (entity) ->
                !entity.isSpectator() && entity.isAlive() && (entity instanceof net.minecraft.entity.LivingEntity || entity instanceof MingtdUnit)
        );

        for (Entity entity : targetEntities) {
            float delta = client.getRenderTickCounter().getTickProgress(false);
            // 1. 눈에 보이는 부드러운 위치(Lerp) 계산
            double lerpX = net.minecraft.util.math.MathHelper.lerp(delta, entity.lastRenderX, entity.getX());
            double lerpY = net.minecraft.util.math.MathHelper.lerp(delta, entity.lastRenderY, entity.getY());
            double lerpZ = net.minecraft.util.math.MathHelper.lerp(delta, entity.lastRenderZ, entity.getZ());

            // 2. 보간된 위치로 박스 이동
            Box renderBox = entity.getBoundingBox()
                    .offset(lerpX - entity.getX(), lerpY - entity.getY(), lerpZ - entity.getZ())
                    .expand(0.7);

            Optional<Vec3d> hitPos = renderBox.raycast(start, end);

            // [로그] 델타값과 좌표 차이 출력
            if (entity instanceof MingtdUnit || entity instanceof DefenseMonsterEntity) {
                double diffX = Math.abs(lerpX - entity.getX());
                double diffZ = Math.abs(lerpZ - entity.getZ());

//                LOGGER.info(String.format("[MingtdLerp] Delta: %.4f | 대상: %s", delta, entity.getName().getString()));
//                LOGGER.info(String.format("  - 서버 위치: [%.2f, %.2f]", entity.getX(), entity.getZ()));
//                LOGGER.info(String.format("  - 렌더 위치: [%.2f, %.2f] (차이: %.4f)", lerpX, lerpZ, diffX + diffZ));
            }

            if (hitPos.isPresent()) {
                double dist = start.distanceTo(hitPos.get());
                if (dist < minDistance) {
                    minDistance = dist;
                    closestEntity = entity;
                }
            }
        }

        if (closestEntity != null) {
            LOGGER.info(String.format("[MingtdRay] 수동 관통 성공! 대상: %s", closestEntity.getName().getString()));
            // getPos() 에러 방지를 위해 좌표 직접 추출
            return new Vec3d(closestEntity.getX(), closestEntity.getY(), closestEntity.getZ());
        }

        // 2. 엔티티를 못 맞췄을 경우 지면 판정
        HitResult blockHit = client.world.raycast(new net.minecraft.world.RaycastContext(
                start, end, net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, client.player));

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getPos();
        }

        return null;
    }

    public void updateTarget(UnitStatPayload payload) {
        LOGGER.info("[MingTD] HUD 데이터 수신 성공: " + payload.name());
        this.selectedUnit = payload;
    }

    public void updateMultiTarget(MultiUnitPayload payload) {
        this.unitList = new ArrayList<>(payload.units());
        this.currentIndex = 0;
        // 로그 추가
//        LOGGER.info("[MingTD] RtsScreen unitList 업데이트 완료. 현재 개수: " + this.unitList.size());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. [필수] 시스템 렌더링 엔진 초기화 (배경 암전 포함)
        super.render(context, mouseX, mouseY, delta);

        // 배경 제외 렌더링
        renderRtsHud(context);

        if (selectionManager.isDragging()) {
            int startX = (int) selectionManager.getStartX();
            int startY = (int) selectionManager.getStartY();

            // 드래그 방향에 상관없이 좌표 정렬 (min/max)
            int minX = Math.min(startX, mouseX);
            int minY = Math.min(startY, mouseY);
            int maxX = Math.max(startX, mouseX);
            int maxY = Math.max(startY, mouseY);

            // 2. 테두리 그리기 (제공해주신 0xFFFFFFFF 흰색 로직 적용)
            int borderColor = 0xFF00FF00;
            context.fill(minX - 1, minY - 1, maxX + 1, minY, borderColor);         // 상단
            context.fill(minX - 1, maxY, maxX + 1, maxY + 1, borderColor);         // 하단
            context.fill(minX - 1, minY, minX, maxY, borderColor);                 // 좌측
            context.fill(maxX, minY, maxX + 1, maxY, borderColor);                 // 우측

            // 2. 내부: 매우 옅은 초록색 반투명 - 0x2200FF00 (약 13% 투명도)
            // 0x33보다 더 투명하게 하여 내부 유닛 가독성을 높였습니다.
            context.fill(minX, minY, maxX, maxY, 0x2200FF00);
        }

        if (!unitList.isEmpty()) {
            renderUnitHUD(context, (float)mouseX, (float)mouseY);
        }
    }

    private void renderUnitHUD(DrawContext context, float mouseX, float mouseY) {
        if (unitList.isEmpty()) return;
        Map<String, Integer> ownedCounts = ClientUnitManager.getOwnedCounts();

        if (unitList.size() == 1 && selectedUnit != null) {
            // 계산된 보유량을 상세 창으로 전달
            renderSingleUnitDetails(context, mouseX, mouseY, ownedCounts);
        } else {
            renderMultiUnitGrid(context, mouseX, mouseY);
        }
    }

    private void renderMultiUnitGrid(DrawContext context, float mouseX, float mouseY) {
        if (unitList == null || unitList.isEmpty()) return;

        int width = this.client.getWindow().getScaledWidth();
        int height = this.client.getWindow().getScaledHeight();

        int iconSize = 30;
        int gap = 4;
        int maxIcons = Math.min(unitList.size(), 24);
        int columns = Math.min(maxIcons, 12);
        int totalWidth = (iconSize + gap) * columns - gap;
        int startX = (width / 2) - (totalWidth / 2);
        int baseY = height - 45;

        for (int i = 0; i < maxIcons; i++) {
            int row = i / 12;
            int col = i % 12;
            int x = startX + (col * (iconSize + gap));
            int y = baseY - (row * (iconSize + gap));

            MultiUnitPayload.UnitEntry entry = unitList.get(i);

            // 1. 배경
            context.fill(x, y, x + iconSize, y + iconSize, 0xAA000000);

            // 2. 아바타 렌더링 (왼쪽 고정 시선)
            Entity entity = this.client.world.getEntityById(entry.entityId());
            if (entity instanceof LivingEntity livingEntity) {
                float prevBodyYaw = livingEntity.bodyYaw;
                float prevHeadYaw = livingEntity.headYaw;
                float prevPitch = livingEntity.getPitch();

                livingEntity.bodyYaw = 210.0F;
                livingEntity.headYaw = 210.0F;
                livingEntity.setPitch(0.0F);

                InventoryScreen.drawEntity(
                        context,
                        x + 2, y + 2, x + iconSize - 2, y + iconSize - 2,
                        12,
                        0.0625f,
                        (float)(x + 15) - 50,
                        (float)(y + 15),
                        livingEntity
                );

                livingEntity.bodyYaw = prevBodyYaw;
                livingEntity.headYaw = prevHeadYaw;
                livingEntity.setPitch(prevPitch);
            }

            // 3. 유닛 이름 출력 (DefenseUnit의 색상 코드를 그대로 사용)
            String displayName = entry.name();
            int textWidth = this.textRenderer.getWidth(displayName);
            // 텍스트 자체에 색상 코드가 있으므로 0xFFFFFFFF(기본 흰색)를 넘겨도 본래 색으로 출력됩니다.
            context.drawTextWithShadow(this.textRenderer, displayName, x + (iconSize / 2) - (textWidth / 2), y + iconSize - 9, 0xFFFFFFFF);

            // 4. Tab 강조 (이미 별도 메소드로 구현됨)
            if (i == currentIndex) {
                renderSelectionHighlight(context, x, y, iconSize);
            }
        }
    }

    // 가독성을 위해 테두리 로직 분리
    private void renderSelectionHighlight(DrawContext context, int x, int y, int size) {
        context.fill(x - 1, y - 1, x + size + 1, y, 0xFFFFFFFF);
        context.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xFFFFFFFF);
        context.fill(x - 1, y, x, y + size, 0xFFFFFFFF);
        context.fill(x + size, y, x + size + 1, y + size, 0xFFFFFFFF);
        context.fill(x, y, x + size, y + size, 0x33FFFFFF);
    }

    private void renderSingleUnitDetails(DrawContext context, float mouseX, float mouseY, Map<String, Integer> ownedCounts) {
        if (unitList.isEmpty()) return;
        if (selectedUnit == null) return;
        MultiUnitPayload.UnitEntry mainUnit = unitList.get(0);

        // 2. 배경 사각형 (스타크래프트 특유의 어두운 패널 느낌)
        context.fill(hudX, hudY, hudX + hudW, hudY + hudH, 0xCC000000); // 메인 패널
        context.fill(hudX, hudY, hudX + hudW, hudY + 1, 0xFF555555);    // 상단 테두리 선

        // 월드에서 실제 엔티티 객체를 가져와 렌더링
        // 3. 유닛 아바타 영역 (좌측)
        Entity entity = this.client.world.getEntityById(mainUnit.entityId());
        if (entity instanceof LivingEntity livingEntity) {
            // 기존 상태 저장
            float prevBodyYaw = livingEntity.bodyYaw;
            float prevHeadYaw = livingEntity.headYaw;
            float prevPitch = livingEntity.getPitch();

            // 시선 고정 설정 (정면에서 약간 측면을 보게 하여 입체감 부여)
            livingEntity.bodyYaw = 210.0F; // 몸체 회전
            livingEntity.headYaw = 210.0F; // 머리 회전
            livingEntity.setPitch(0.0F);   // 상하 각도 고정

            // 공유해주신 1.21.2 시그니처에 맞춘 인자 전달
            // drawEntity(context, x1, y1, x2, y2, size, scale, mouseX, mouseY, entity)
            InventoryScreen.drawEntity(
                    context,
                    hudX + 5, hudY + 5, hudX + 45, hudY + 45,
                    20,       // 크기
                    0.0625f,  // 스케일 오프셋
                    // mouseX, mouseY 대신 고정된 가상의 좌표를 전달하여 시선 흔들림 방지
                    (float)(hudX + 25) - 100, // 고정된 가상 X (엔티티가 쳐다볼 지점)
                    (float)(hudY + 25),      // 고정된 가상 Y
                    livingEntity
            );

            // 상태 복구 (월드 내 실제 엔티티에 영향이 없도록)
            livingEntity.bodyYaw = prevBodyYaw;
            livingEntity.headYaw = prevHeadYaw;
            livingEntity.setPitch(prevPitch);
        } else {
            // 엔티티를 찾지 못했을 때의 대체 텍스트
            context.fill(hudX + 5, hudY + 5, hudX + 45, hudY + 45, 0xFF222222);
            context.drawTextWithShadow(this.textRenderer, "No Ent", hudX + 10, hudY + 22, 0xFF666666);
        }
        // 4. 유닛 이름 및 마나 바 (중앙)
        int infoX = hudX + 50;
        context.drawTextWithShadow(this.textRenderer, "§l" + mainUnit.name(), infoX, hudY + 8, 0xFFFFFFFF);
        // 마나 바 디자인
        int barW = 100;
        int barH = 5;
        float manaRatio = selectedUnit.currentMana() / selectedUnit.maxMana();

        context.fill(infoX, hudY + 20, infoX + barW, hudY + 20 + barH, 0xFF333333); // 바 배경
        context.fill(infoX, hudY + 20, infoX + (int)(barW * manaRatio), hudY + 20 + barH, 0xFF00AAFF); // 푸른색 마나

        String manaText = String.format("%.0f/%.0f", selectedUnit.currentMana(), selectedUnit.maxMana());
        context.drawText(this.textRenderer, "§b" + manaText, infoX, hudY + 28, 0xFFFFFFFF, false);

        // 5. 스탯 정보 (우측)
        int statX = hudX + 155;
        context.drawTextWithShadow(this.textRenderer, "§eATK: " + (int)selectedUnit.currentDamage(), statX, hudY + 15, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§bSPD: " + String.format("%.1f", selectedUnit.attackSpeed()), statX, hudY + 27, 0xFFFFFFFF);

//        logUpgradeRequirements(mainUnit);
        // 추가된 부분: 승급 스택 렌더링
        renderUpgradeStack(context, mainUnit, hudX, hudY, mouseX, mouseY, ownedCounts);
    }

    private void renderUpgradeStack(DrawContext context, MultiUnitPayload.UnitEntry mainUnit, int hudX, int hudY, float mouseX, float mouseY, Map<String, Integer> ownedCounts) {
        String jobKey = mainUnit.id();
        // 레시피 필터링
        List<UpgradeRecipe> possibleRecipes = MingtdUnits.RECIPES_BY_INGREDIENT
                .getOrDefault(jobKey, new ArrayList<>())
                .stream()
                // 2. 정렬 로직 적용
                .sorted(
                        // [1] 결과물 유닛의 등급(Rarity)이 높은 순 (UNIQUE > RARE > MAGIC > NORMAL)
                        Comparator.comparingInt((UpgradeRecipe r) -> {
                                    UnitInfo resultUnit = MingtdUnits.UNIT_REGISTRY.get(r.resultId());
                                    // resultUnit.rarity()가 record 메서드라고 가정 (필요 시 getRarity()로 수정)
                                    return resultUnit != null ? resultUnit.rarity().ordinal() : -1;
                                }).reversed()

                                // [2] 해당 재료(jobKey)를 더 많이 소모하는 레시피 우선
                                .thenComparing(r -> r.ingredients().getOrDefault(jobKey, 0), Comparator.reverseOrder())

                                // [3] 이름순(결과 ID)으로 최종 고정
                                .thenComparing(UpgradeRecipe::resultId)
                )
                .toList();

        if (possibleRecipes.isEmpty()) return;

        for (int i = 0; i < possibleRecipes.size(); i++) {
            UpgradeRecipe recipe = possibleRecipes.get(i);
            int cardX = getUpgradeCardX(i);
            int cardY = getUpgradeCardY();

            // [시각적 피드백] 조합 가능 여부 체크
            boolean canUpgrade = true;
            for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
                int owned = ownedCounts.getOrDefault(entry.getKey(), 0);
                if (ownedCounts.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    canUpgrade = false;
                    break;
                }
            }

            boolean isHovered = mouseX >= cardX && mouseX <= cardX + CARD_W &&
                    mouseY >= cardY && mouseY <= cardY + CARD_H;

            // 카드 배경
            int bgColor = isHovered ? 0xDD333333 : 0xAA000000;
            context.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, bgColor);

            // 상단 테두리 색상 강조 (조합 가능 시 금색)
            int borderColor = canUpgrade ? 0xFFFFD700 : 0xFF555555;
            context.fill(cardX, cardY, cardX + CARD_W, cardY + 1, borderColor);

            // [수정] 결과물 ID(resultId)를 통해 실제 표시할 이름을 가져옵니다.
            UnitInfo resultUnit = UNIT_REGISTRY.get(recipe.resultId());
            String displayName = resultUnit.name();

            // 단축키 매핑용 배열 정의
            String[] hotkeys = {"Q", "W", "E", "R"};
            String hotkey = (i < hotkeys.length) ? hotkeys[i] : String.valueOf(i + 1);

            // 결과물 이름 출력 (조합 가능 시 밝은 노란색 강조)
            context.drawText(this.textRenderer, (canUpgrade ? "§e§l" : "§6") + displayName, cardX + 5, cardY + 5, 0xFFFFFFFF, false);
            context.drawText(this.textRenderer, "§b[" + hotkey + "]", cardX + 5, cardY + 16, 0xFFFFFFFF, false);
//            context.drawText(this.textRenderer, "§b[U" + (i + 1) + "]", cardX + 5, cardY + 16, 0xFFFFFFFF, false);

            if (isHovered) {
                renderUpgradeTooltip(context, recipe, jobKey, mouseX, mouseY, ownedCounts);
            }
        }
    }

    // 아이디를 보기 좋게 포맷팅 (예: MAGIC_WARRIOR -> Magic Warrior)
    private String formatId(String id) {
        if (id == null || id.isEmpty()) return "Unknown";
        String cleanId = id.contains(":") ? id.split(":")[id.split(":").length - 1] : id;
        String[] words = cleanId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(w.substring(0, 1).toUpperCase()).append(w.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void renderUpgradeTooltip(DrawContext context, UpgradeRecipe recipe, String currentId, float mouseX, float mouseY, Map<String, Integer> ownedCounts) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("§e§l[승급 재료 현황]"));

        recipe.ingredients().forEach((reqId, reqCount) -> {
            // [참고하신 로직 적용]
            UnitInfo unitType;
            try {
                unitType = UNIT_REGISTRY.get(reqId);
            } catch (IllegalArgumentException e) {
                return; // 존재하지 않는 유닛일 경우 스킵
            }

            // 2. 보유량 조회 (패킷으로 받은 ID 기반)
            int owned = ownedCounts.getOrDefault(unitType.id(), 0);

            boolean isComplete = owned >= reqCount;
            boolean isMain = unitType.id().equals(currentId);

            String statusColor = isComplete ? "§a" : "§c";
            String prefix = isComplete ? "§a✔ " : "§7- ";

            // 3. [핵심] HUD에서 사용한 것과 동일하게 unitType.getDisplayName()(또는 .name())을 사용
            // 만약 DefenseUnit Enum에 displayName이 있다면 그것을,
            // HUD에서 사용한 name()이 Enum 고유 이름이라면 그것을 사용합니다.
            String displayName = unitType.name();

            lines.add(Text.literal(prefix + displayName + " : " + statusColor + owned + "§7/" + reqCount + (isMain ? " §6(본인)" : "")));
        });

        context.drawTooltip(this.textRenderer, lines, (int)mouseX, (int)mouseY);
    }

    private void logUpgradeRequirements(MultiUnitPayload.UnitEntry mainUnit) {
        if (mainUnit == null) return;

        String currentUnitId = mainUnit.id();

        // 1. 해당 유닛이 재료로 포함된 모든 레시피 수집
        List<UpgradeRecipe> possibleRecipes = new ArrayList<>(
                MingtdUnits.RECIPES_BY_INGREDIENT.getOrDefault(currentUnitId, List.of())
        );

        possibleRecipes.sort(Comparator.comparingInt((UpgradeRecipe r) -> {
            UnitInfo resultUnit = MingtdUnits.UNIT_REGISTRY.get(r.resultId());
            return resultUnit != null ? resultUnit.rarity().ordinal() : -1;
        }).reversed());

        // 2. 로그 출력 시작
        System.out.println("---- [Unit Upgrade Path Check] ----");
        System.out.println("대상 유닛: " + mainUnit.name() + " (" + currentUnitId + ")");

        if (possibleRecipes.isEmpty()) {
            System.out.println("결과: 이 유닛으로 시작하는 승급 경로가 없습니다.");
        } else {
            System.out.println("총 " + possibleRecipes.size() + "개의 승급 경로 발견:");

            for (UpgradeRecipe recipe : possibleRecipes) {
                System.out.println("------------------------------------");
                System.out.println("▶ 경로 ID: " + recipe.recipeId()); // v1, v2, v3 구분용
                System.out.println("▶ 결과물: " + recipe.resultId());
                System.out.println("▶ 필요 재료:");

                recipe.ingredients().forEach((reqId, reqCount) -> {
                    // 강조 표시: 현재 유닛인 경우 [본인] 표시
                    String identity = reqId.equals(currentUnitId) ? " [본인]" : "";
                    System.out.println("   - " + reqId + " : " + reqCount + "마리" + identity);
                });
            }
        }
        System.out.println("------------------------------------");
    }

    // 2. RTS 하단/상단 HUD 렌더링
    private void renderRtsHud(DrawContext context) {
        int width = this.client.getWindow().getScaledWidth();
        int height = this.client.getWindow().getScaledHeight();

        // 1. 상단 검은색 바 (투명도 0x88 반영)
        context.fill(0, 0, width, 25, 0x88000000);

        // 2. 상단 텍스트 (Alpha 0xFF 추가)
        // "MingTD RTS Mode" - 흰색 (0xFFFFFFFF)
        context.drawTextWithShadow(this.textRenderer, "MingTD RTS Mode", 10, 8, 0xFFFFFFFF);

        // 선택된 유닛 수 표시 - 녹색 (0xFF00FF00)
        int selectedCount = selectionManager.getSelectedCount();
        String countText = "Units Selected: " + selectedCount;
        context.drawTextWithShadow(this.textRenderer, countText, width - this.textRenderer.getWidth(countText) - 10, 8, 0xFF00FF00);

        // 3. 하단 조작 가이드 - 회색 (0xFFAAAAAA)
        // 위치를 하단 끝에서 살짝 위로 조정 (20 -> 25)
//        context.drawTextWithShadow(this.textRenderer, "[L-Click] Select / [R-Click] Move", 10, height - 25, 0xFFAAAAAA);
    }

    @Override
    protected void init() {
        super.init();
        // 화면이 열릴 때 로그 출력
//        LOGGER.info("RTS 화면이 성공적으로 열렸습니다!");
        if (this.client != null) {
            this.client.mouse.unlockCursor();
//            LOGGER.info("마우스 커서 해제 시도 완료");
        }
//
//        // 화면 왼쪽 하단에 리셋 버튼 추가
//        this.addDrawableChild(ButtonWidget.builder(Text.literal("게임 리셋"), button -> {
//                    // 서버로 리셋 패킷 전송 (MoveUnitPayload 같은 방식으로 커스텀 패킷 정의 필요)
//                    // ClientPlayNetworking.send(new ResetGamePayload());
//
//                    // 임시로 명령어를 직접 실행하게 하고 싶다면:
//                    if (this.client != null && this.client.player != null) {
//                        this.client.player.networkHandler.sendChatCommand("mingtd reset");
//                    }
//                })
//                .dimensions(10, this.height - 30, 80, 20) // 위치 및 크기
//                .build());
    }

    // 화면을 닫을 때 다시 마우스를 잠궈주는 것이 매너입니다.
    @Override
    public void close() {
        LOGGER.info("마우스 커서 해제 시도 종료");
        if (this.client != null) {
            this.client.mouse.lockCursor();
        }
        super.close();
    }

    private Vec3d getRayFromClick(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. 마우스 정규화 (NDC: -1.0 ~ 1.0)
        float nx = (float) ((2.0D * mouseX) / client.getWindow().getScaledWidth() - 1.0D);
        float ny = (float) (1.0D - (2.0D * mouseY) / client.getWindow().getScaledHeight());

        // 2. 현재 렌더링에 사용된 Projection 행렬 가져오기
        // FOV와 AspectRatio가 모두 포함된 엔진의 행렬을 직접 사용합니다.
        float fov = client.options.getFov().getValue().floatValue();
        Matrix4f projMatrix = client.gameRenderer.getBasicProjectionMatrix(fov);

        // 3. 카메라 회전 행렬 구성
        // 카메라가 바라보는 방향을 월드로 되돌리는 회전 행렬입니다.
        Quaternionf rotation = new Quaternionf(client.gameRenderer.getCamera().getRotation());
        Matrix4f viewMatrix = new Matrix4f().rotation(rotation);

        // 4. 역행렬 계산 (화면 -> 월드 방향 변환)
        // Projection과 View를 결합한 뒤 뒤집어서 마우스 좌표를 쏩니다.
        Matrix4f combinedInverse = new Matrix4f(projMatrix).mul(viewMatrix).invert();

        // 5. 레이 방향 산출 (-Z가 앞쪽)
        Vector4f rayDir = new Vector4f(nx, ny, -1.0f, 1.0f);
        rayDir.mul(combinedInverse);

        Vec3d finalDir = new Vec3d(rayDir.x(), rayDir.y(), rayDir.z()).normalize();

        // 디버그 로그: 카메라가 이동해도 이 벡터는 마우스 위치에 따라 정확히 변해야 합니다.
        LOGGER.info(String.format("[MingtdMatrix] 레이 방향: X:%.3f, Y:%.3f, Z:%.3f",
                finalDir.x, finalDir.y, finalDir.z));

        return finalDir;
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

        if (input.key() == GLFW.GLFW_KEY_TAB && !unitList.isEmpty()) {
            // 1. 인덱스 순환
            this.currentIndex = (this.currentIndex + 1) % unitList.size();

            // 2. [추가] 강조된 유닛의 상세 정보를 서버에 요청 (또는 서버가 보낸 리스트에서 추출)
            // 대표 유닛이 바뀌었으므로 상세 정보를 다시 가져와야 합니다.
            MultiUnitPayload.UnitEntry mainEntry = unitList.get(this.currentIndex);

            // 서버에 이 유닛의 상세 정보를 달라고 요청하는 패킷 전송 (선택 사항)
//             ClientPlayNetworking.send(new RequestUnitDetailPayload(mainEntry.entityId()));

            return true;
        }


        if (this.selectedUnit != null) {
            // 2. Q, W, E, R 키 매핑 (GLFW 키 코드 활용)
            int[] hotkeys = {GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R};
            for (int i = 0; i < hotkeys.length; i++) {
                if (input.key() == hotkeys[i]) {
                    // [수정] getAvailableRecipes 로직 (mainUnit의 jobKey가 재료에 포함되는지 확인)
                    List<UpgradeRecipe> recipes = getAvailableRecipes(this.selectedUnit.jobKey());
                    LOGGER.info("[MingtdDebug] RtsScreen hotkeys :: " + hotkeys[i]);
                    LOGGER.info("[MingtdDebug] RtsScreen recipes.size() :: " + recipes.size());
                    if (i < recipes.size()) {
                        UpgradeRecipe recipe = recipes.get(i);
                        Map<String, Integer> ownedCounts = ClientUnitManager.getOwnedCounts();

                        // 1. 조합 가능 여부 1차 체크 (숫자 기반)
                        LOGGER.info("[MingtdDebug] RtsScreen hotkeys recipe.canUpgrade :: " + recipe.canUpgrade(ownedCounts));
                        if (recipe.canUpgrade(ownedCounts)) {
                            int mainId = this.selectedUnit.entityId();

                            // 2. [핵심] 서버에 "얘네 지워줘"라고 말할 재료 ID 리스트 추출
                            List<Integer> ingredientsToBurn = recipe.collectIngredientIds(
                                    ClientUnitManager.getOwnedList(),
                                    mainId
                            );

                            // 3. 확장된 패킷 구조로 전송
                            ClientPlayNetworking.send(new UpgradeRequestPayload(
                                    recipe.recipeId(),
                                    recipe.resultId(),
                                    mainId,
                                    ingredientsToBurn // 서버는 이 목록을 그대로 discard() 함
                            ));

                            return true;
                        }
                    }
                }
            }
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

    // 현재 선택된 유닛이 재료로 들어가는 레시피 리스트 반환
    private List<UpgradeRecipe> getAvailableRecipes(String jobKey) {
        // 1. 역방향 인덱스 맵에서 jobKey(재료)를 사용하는 레시피 리스트를 즉시 가져옴
        // 없으면 빈 리스트를 반환하여 NullPointerException 방지
        List<UpgradeRecipe> recipes = MingtdUnits.RECIPES_BY_INGREDIENT.getOrDefault(jobKey, new ArrayList<>());

        return recipes.stream()
                .sorted(
                        // 1. 결과물 유닛의 등급(Rarity) 높은 순 (UNIQUE > RARE > MAGIC > NORMAL)
                        Comparator.comparingInt((UpgradeRecipe r) -> {
                                    UnitInfo resultUnit = MingtdUnits.UNIT_REGISTRY.get(r.resultId());
                                    // rarity()가 메서드라면 resultUnit.rarity() 호출
                                    return resultUnit != null ? resultUnit.rarity().ordinal() : -1;
                                }).reversed()

                                // 2. 해당 재료(jobKey)를 더 많이 소모하는 레시피 우선
                                .thenComparing(r -> r.ingredients().getOrDefault(jobKey, 0), Comparator.reverseOrder())

                                // 3. 이름순(결과 ID)으로 최종 고정
                                .thenComparing(UpgradeRecipe::resultId)
                )
                .toList();
    }

    // 1. 공통 좌표 계산 메소드 생성
    private int getUpgradeCardX(int index) {
        return hudX + (index * (CARD_W + CARD_SPACING));
    }

    private int getUpgradeCardY() {
        return hudY - CARD_H - 6;
    }
}
