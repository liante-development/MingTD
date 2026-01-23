package com.liante.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.VindicatorEntityRenderer;
import net.minecraft.client.render.entity.state.IllagerEntityRenderState;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.util.Identifier;

public class MingtdUnitRenderer extends VindicatorEntityRenderer {
    // 스킨 파일 위치: src/main/resources/assets/mingtd/textures/entity/defense_unit.png
    private static final Identifier TEXTURE = Identifier.of("mingtd", "textures/entity/defense_unit.png");

    public MingtdUnitRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    // 1.21.2+ 버전에서는 Entity 대신 RenderState를 인자로 받습니다.
    @Override
    public Identifier getTexture(IllagerEntityRenderState state) {
        return TEXTURE;
    }

    // [핵심 추가] 서버의 엔티티 데이터를 클라이언트 렌더링 상태(State)로 복사하는 로직
    @Override
    public void updateRenderState(VindicatorEntity entity, IllagerEntityRenderState state, float f) {
        super.updateRenderState(entity, state, f);

        state.attacking = true;
        // 엔티티가 메인핸드에 들고 있는 아이템 정보를 렌더링 상태에 반영
        // 1.21.2+ 최신 API 규격에 맞춰 아이템 스택 정보를 전달합니다.
        // (참고: super.updateRenderState 내부에서 기본적으로 처리되지만,
        // 커스텀 엔티티 타입의 경우 명시적으로 호출하거나 확인이 필요할 수 있습니다.)
    }
}
