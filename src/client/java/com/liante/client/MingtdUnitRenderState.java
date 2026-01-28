package com.liante.client;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

// PlayerEntityModel이 요구하는 PlayerEntityRenderState를 상속받아야 바운드 오류가 해결됩니다.
public class MingtdUnitRenderState extends PlayerEntityRenderState {
    // 플레이어 모델의 팔 포즈(활쏘기, 막기 등)를 결정합니다.
    public BipedEntityModel.ArmPose mainHandPose = BipedEntityModel.ArmPose.EMPTY;
    public BipedEntityModel.ArmPose offHandPose = BipedEntityModel.ArmPose.EMPTY;
    public boolean isSneaking;

    public boolean hatVisible = true;
    public boolean jacketVisible = true;
    public boolean leftSleeveVisible = true;
    public boolean rightSleeveVisible = true;
    public boolean leftLegVisible = true;
    public boolean rightLegVisible = true;
}
