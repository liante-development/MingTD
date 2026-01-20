package com.liante;

import com.liante.map.MapGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.rule.GameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Mingtd implements ModInitializer {
    // 맵의 기준 좌표 (0, 100, 0 등 고정된 위치 권장)
    public static final BlockPos SPAWN_POS = new BlockPos(0, 100, 0);
    public static final Identifier OPEN_SCREEN_PACKET = Identifier.of("mingtd", "open_screen");

    @Override
    public void onInitialize() {
        // 패킷 등록 (S2C)
        PayloadTypeRegistry.playS2C().register(OpenRtsScreenPayload.ID, OpenRtsScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MoveUnitPayload.ID, MoveUnitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectUnitPayload.ID, SelectUnitPayload.CODEC);

        // 날씨 및 시간 고정 로직
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld overworld = server.getOverworld();
            var gameRules = overworld.getGameRules();
            // 1. 시간 흐름 정지
            gameRules.setValue(GameRules.ADVANCE_TIME, false, server);

            // 2. 날씨 변화 정지
            gameRules.setValue(GameRules.ADVANCE_WEATHER, false, server);

            overworld.setTimeOfDay(6000L); // 낮 12시로 설정
            overworld.setWeather(100000, 0, false, false); // 맑은 날씨 유지

            Scoreboard scoreboard = server.getScoreboard();
            Team selectionTeam = scoreboard.getTeam("selected_units");

            if (selectionTeam == null) {
                selectionTeam = scoreboard.addTeam("selected_units");
            }
            selectionTeam.setColor(Formatting.GREEN); // 발광 색상을 초록색으로!
        });

        // 플레이어가 접속할 때 시점 설정
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                // 첫 플레이어 접속 시 맵을 한 번 초기화/생성
                MapGenerator.setupDefenseWorld(server.getOverworld(), SPAWN_POS);
            });

            var player = handler.getPlayer();
            player.changeGameMode(GameMode.SPECTATOR);

            var world = server.getOverworld();

            // 맵의 중앙 위쪽으로 이동
            double x = (double)SPAWN_POS.getX() + MapGenerator.SIZE + (MapGenerator.GAP / 2.0);
            double y = (double)SPAWN_POS.getY() + 15; // 고도를 30 정도로 높임
            double z = (double)SPAWN_POS.getZ() + (MapGenerator.SIZE / 2.0);

            // pitch를 70~90으로 설정하면 바닥을 내려다봅니다.
            player.teleport(world, x, y, z, Collections.emptySet(), 0.0f, 75.0f, false);

            // 서버측 로직: 플레이어가 접속하면 패킷을 보냄
            ServerPlayNetworking.send(handler.getPlayer(), new OpenRtsScreenPayload());
        });

        // onInitialize 또는 전용 매니저에 추가
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) {
                // 플레이어가 맵 중심(SPAWN_POS)에서 너무 멀어지면 중심으로 다시 당김
                if (player.squaredDistanceTo(SPAWN_POS.getX(), SPAWN_POS.getY(), SPAWN_POS.getZ()) > 2500) { // 반경 50블록
                    player.sendMessage(Text.literal("§c맵 경계를 벗어날 수 없습니다!"), true);
                    // 부드러운 이동 대신 강제 좌표 고정
                }
            }
        });

        // [초기화 명령어] /mingtd reset 입력 시 맵 다시 생성
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("mingtd")
                    .then(CommandManager.literal("reset")
                            .executes(context -> {
                                MapGenerator.setupDefenseWorld(context.getSource().getWorld(), SPAWN_POS);
                                context.getSource().sendFeedback(() -> Text.literal("디펜스 맵이 초기화되었습니다!"), false);
                                return 1;
                            })));
        });

        // --- 1. 유닛 이동 명령 처리 (MoveUnitPayload) ---
        ServerPlayNetworking.registerGlobalReceiver(MoveUnitPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerWorld world = (ServerWorld) context.player().getEntityWorld();
                Entity entity = world.getEntityById(payload.entityId());

                if (entity instanceof ZombieEntity zombie) {
                    Vec3d target = payload.targetPos();

                    // Y값을 1.0 더해서 블록 위쪽 공간으로 확실히 인지하게 함
                    // startMovingTo는 목표 지점의 발밑을 계산하므로 살짝 높은 게 안전합니다.
                    boolean success = zombie.getNavigation().startMovingTo(
                            target.x, target.y + 1.0D, target.z, 1.3D
                    );

                    zombie.setTarget(null); // 플레이어 추적 방지
                    System.out.println("[Server] 좀비 이동 결과: " + (success ? "성공" : "실패(좌표보정필요)"));
                }
            });
        });

// --- 2. 유닛 선택 및 팀 색상 처리 (SelectUnitPayload) ---
        ServerPlayNetworking.registerGlobalReceiver(SelectUnitPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerWorld world = (ServerWorld) context.player().getEntityWorld();
                Scoreboard scoreboard = context.server().getScoreboard();
                Team team = scoreboard.getTeam("selected_units");
                if (team == null) return;

                // [핵심] 기존 모든 좀비의 발광을 끄고 팀에서 제거
                // team.getPlayerList()를 직접 순회하면 ConcurrentModificationException이 날 수 있으므로 복사해서 사용
                List<String> toRemove = new ArrayList<>(team.getPlayerList());
                for (String name : toRemove) {
                    // 월드에서 해당 이름을 가진 엔티티를 찾아 발광 해제
                    for (Entity e : world.iterateEntities()) {
                        if (e.getNameForScoreboard().equals(name)) {
                            e.setGlowing(false);
                            break;
                        }
                    }
                    scoreboard.removeScoreHolderFromTeam(name, team);
                }

                // 새로운 좀비 추가
                for (int id : payload.entityIds()) {
                    Entity entity = world.getEntityById(id);
                    if (entity != null) {
                        entity.setGlowing(true);
                        scoreboard.addScoreHolderToTeam(entity.getNameForScoreboard(), team);
                    }
                }
            });
        });
    }

    // 1. 패킷 데이터 정의
    public record OpenRtsScreenPayload() implements CustomPayload {
        public static final Id<OpenRtsScreenPayload> ID = new Id<>(Identifier.of("mingtd", "open_screen"));
        public static final PacketCodec<RegistryByteBuf, OpenRtsScreenPayload> CODEC = PacketCodec.unit(new OpenRtsScreenPayload());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}
