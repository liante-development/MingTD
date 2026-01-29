package com.liante.client;

import com.liante.MingtdUnit;
import com.liante.spawner.UnitSpawner;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose;

public class MingtdUnitRenderer extends LivingEntityRenderer<MingtdUnit, PlayerEntityRenderState, PlayerEntityModel> {
    private static final AssetInfo.TextureAssetInfo UNIT_SKIN_ASSET =
            new AssetInfo.TextureAssetInfo(Identifier.of("mingtd", "entity/defense_unit"));

    public MingtdUnitRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);

        // 지적하신 대로, 타입 추론이 가능하므로 <>로 생략하여 코드를 간결하게 만듭니다.
        this.addFeature(new HeldItemFeatureRenderer<>(this));
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(MingtdUnit entity, PlayerEntityRenderState state, float f) {
        super.updateRenderState(entity, state, f);

        // 2. SkinTextures 생성 (제공해주신 레코드 구조 적용)
        // 인자 순서: body, cape, elytra, model, secure
        // PlayerSkinType.WIDE는 클래식(4픽셀), SLIM은 슬림(3픽셀) 모델입니다.
        state.skinTextures = new SkinTextures(
                UNIT_SKIN_ASSET,           // body
                null,                // cape
                null,                // elytra
                PlayerSkinType.WIDE, // model (심볼 오류 시 PlayerSkinType에서 자동완성 확인)
                true                 // secure
        );

        // 2. 레이어 활성화 (확인하신 필드명 적용)
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;

        // 3. 무기 업데이트 (기존 코드 유지)
        this.itemModelResolver.updateForLivingEntity(state.rightHandItemState, entity.getMainHandStack(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        this.itemModelResolver.updateForLivingEntity(state.leftHandItemState, entity.getOffHandStack(), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
        state.rightHandItem = entity.getMainHandStack();
        state.leftHandItem = entity.getOffHandStack();

        // 1. 스윙 애니메이션 (휘두르기) 동기화
        state.handSwingProgress = entity.getHandSwingProgress(f);

        // 2. 직업(UnitType)에 따른 ArmPose 결정
        // 단순 아이템 체크가 아니라, 유닛의 직업 데이터(DataTracker)를 기반으로 포즈를 잡습니다.
        UnitSpawner.DefenseUnit type = entity.getUnitType();

        state.rightArmPose = getAttackArmPose(entity, type, Hand.MAIN_HAND);
        state.leftArmPose = getAttackArmPose(entity, type, Hand.OFF_HAND);
    }

    // 직업과 아이템 상황을 조합한 정교한 포즈 결정 헬퍼
    private ArmPose getAttackArmPose(MingtdUnit entity, UnitSpawner.DefenseUnit type, Hand hand) {
        ItemStack stack = entity.getStackInHand(hand);
        if (stack.isEmpty()) return ArmPose.EMPTY;

        // 메인 핸드일 때 직업별 특수 포즈 설정
        if (hand == Hand.MAIN_HAND) {
            return switch (type) {
                // 궁수: 활을 들고 있으면 무조건 조준 자세
                case ARCHER -> ArmPose.BOW_AND_ARROW;
                // 법사: 지팡이를 치켜든 자세 (나팔 부는 포즈 응용)
                case MAGE -> ArmPose.TOOT_HORN;
                // 전사/도적: 검이나 도끼를 들고 전투 태세
                default -> ArmPose.ITEM;
            };
        }
        return ArmPose.ITEM;
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        // UNIT_SKIN_ASSET 내부의 실제 경로를 반환하거나, 기존 TEXTURE를 반환해도 무방합니다.
        // 하지만 Player 모델 렌더링 시 이 값은 무시됩니다.
        return UNIT_SKIN_ASSET.texturePath();
    }

    // 아이템 종류에 따른 포즈 결정 헬퍼 메서드
    private ArmPose getArmPose(MingtdUnit entity, Hand hand) {
        ItemStack stack = entity.getStackInHand(hand);
        if (stack.isEmpty()) return ArmPose.EMPTY;

        // 검이나 도구일 경우 기본 ITEM 포즈, 활/심전도 등은 상황에 맞게 확장
        return ArmPose.ITEM;
    }
}
