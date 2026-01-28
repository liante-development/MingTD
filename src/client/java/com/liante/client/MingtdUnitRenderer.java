package com.liante.client;

import com.liante.MingtdUnit;
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
import net.minecraft.util.Identifier;

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
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        // UNIT_SKIN_ASSET 내부의 실제 경로를 반환하거나, 기존 TEXTURE를 반환해도 무방합니다.
        // 하지만 Player 모델 렌더링 시 이 값은 무시됩니다.
        return UNIT_SKIN_ASSET.texturePath();
    }
}
