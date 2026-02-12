package com.liante.client;

import com.liante.Mingtd;
import com.liante.ModEntities;
import com.liante.network.MultiUnitPayload;
import com.liante.network.UnitInventoryPayload;
import com.liante.network.UnitStatPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.ZombieEntityRenderer;

import static com.liante.ModEntities.DEFENSE_MONSTER_TYPE;
import static com.liante.ModEntities.MINGTD_UNIT_TYPE;
import static com.mojang.text2speech.Narrator.LOGGER;

public class MingtdClient implements ClientModInitializer {
    private static boolean shouldReturnToRts = false;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MINGTD_UNIT_TYPE, MingtdUnitRenderer::new);
        // 몬스터 렌더러 등록: 일단 좀비 모델을 빌려 쓰도록 설정합니다.
        EntityRendererRegistry.register(DEFENSE_MONSTER_TYPE, DefenseMonsterRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(Mingtd.OpenRtsScreenPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new RtsScreen());
            });
        });

        // MingtdClient.java 내부에 추가
        ClientPlayNetworking.registerGlobalReceiver(UnitStatPayload.ID, (payload, context) -> {
            System.out.println("[MingTD] 패킷 수신 성공! 대상 ID: " + payload.entityId()); // 로그 추가
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof RtsScreen rtsScreen) {
                    rtsScreen.updateTarget(payload);
                } else {
//                    LOGGER.info("[MingTD] 현재 RtsScreen이 열려있지 않아 데이터를 표시할 수 없습니다."); // 로그 추가
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UnitInventoryPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // [수정] payload.inventory() 대신 payload.unitEntityMap()을 사용하며, 클라이언트 월드 객체를 전달합니다.
                System.out.println("[MingTD-Client] 패킷 수신됨! 종류 수: " + payload.unitEntityMap().size());

                // ClientUnitManager에서 실제 엔티티 객체 리스트를 구축할 수 있도록 world를 함께 넘깁니다.
                ClientUnitManager.updateInventory(payload, context.client().world);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MultiUnitPayload.ID, (payload, context) -> {
            // 로그 추가: 패킷이 도착했는지 확인
//            System.out.println("[MingTD] 다중 유닛 패킷 수신! 유닛 수: " + payload.units().size());

            context.client().execute(() -> {
                if (context.client().currentScreen instanceof RtsScreen rtsScreen) {
                    rtsScreen.updateMultiTarget(payload);
                }
            });
        });

        // 매 틱마다 화면 상태를 감지
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // 1. 현재 화면 상태 파악
            if (client.currentScreen != null) {
                // 메뉴창이나 RtsScreen이 떠있으면 나중에 다시 열 수 있도록 상태 저장
                if (client.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen ||
                        client.currentScreen instanceof RtsScreen) {
                    shouldReturnToRts = true;
                }
                return; // 어떤 화면이든 떠있다면 여기서 중단
            }

            // 2. 화면이 null(비어있음)인 상태일 때
            if (shouldReturnToRts) {
                // 채팅창을 닫았거나, 메뉴창에서 '게임으로 돌아가기'를 눌러 null이 된 경우
                if (RtsScreen.isChatting || client.player.isSpectator()) {
                    RtsScreen.isChatting = false;
                    // 다시 RTS 화면으로 복귀
                    client.setScreen(new RtsScreen());
                }
            }

            if (client.player == null || client.currentScreen != null) return; // UI 열려있을 땐 중단
        });
    }

    // 외부에서 복귀 기능을 끌 수 있도록 하는 메서드 (필요 시)
    public static void setShouldReturnToRts(boolean value) {
        shouldReturnToRts = value;
    }
}
