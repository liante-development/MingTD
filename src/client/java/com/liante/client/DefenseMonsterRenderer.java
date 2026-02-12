package com.liante.client;

import com.liante.DefenseMonsterEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.ZombieEntityModel;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.util.Identifier;

// 1.21.2에서는 <엔티티, 상태, 모델> 세 가지를 인자로 받습니다.
public class DefenseMonsterRenderer extends LivingEntityRenderer<DefenseMonsterEntity, ZombieEntityRenderState, ZombieEntityModel<ZombieEntityRenderState>> {

    // 좀비 텍스처 경로
    private static final Identifier ZOMBIE_TEXTURE = Identifier.of("minecraft", "textures/entity/zombie/zombie.png");

    public DefenseMonsterRenderer(EntityRendererFactory.Context context) {
        // 좀비 모델 레이어를 사용하여 초기화
        super(context, new ZombieEntityModel<>(context.getPart(EntityModelLayers.ZOMBIE)), 0.5f);
    }

    // [핵심] MingtdUnitRenderer 처럼 createRenderState 구현
    @Override
    public ZombieEntityRenderState createRenderState() {
        return new ZombieEntityRenderState();
    }

    // [핵심] MingtdUnitRenderer 처럼 updateRenderState 구현
    @Override
    public void updateRenderState(DefenseMonsterEntity entity, ZombieEntityRenderState state, float f) {
        super.updateRenderState(entity, state, f);
        // 필요한 경우 여기서 애니메이션이나 추가 상태를 동기화합니다.
    }

    // [에러 해결 포인트] 1.21.2 LivingEntityRenderer가 요구하는 추상 메서드 구현
    // MingtdUnitRenderer의 getTexture(PlayerEntityRenderState state)와 동일한 역할입니다.
    @Override
    public Identifier getTexture(ZombieEntityRenderState state) {
        return ZOMBIE_TEXTURE;
    }
}