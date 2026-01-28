package com.liante;

import com.liante.config.DefenseConfig;
import com.liante.manager.CameraMovePayload;
import com.liante.manager.WaveManager;
import com.liante.map.MapGenerator;
import com.liante.spawner.UnitSpawner;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.rule.GameRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.mojang.text2speech.Narrator.LOGGER;

public class Mingtd implements ModInitializer {
    // 맵의 기준 좌표 (0, 100, 0 등 고정된 위치 권장)
    public static final BlockPos SPAWN_POS = new BlockPos(0, 100, 0);
    // 클래스 레벨에서 변수 선언 (나중에 초기화)
    private WaveManager waveManager;
    private int spawnTimer = 0;

    public static final int MAX_MONSTER_COUNT = 100;

    // 1. 유닛의 고유 ID를 상수로 정의
    public static final Identifier DEFENSE_UNIT_ID = Identifier.of("mingtd", "defense_unit");

    // 2. 엔티티 타입 등록 (PathAwareEntity에 맞춰 빌더 수정)
    public static final EntityType<MingtdUnit> MINGTD_UNIT_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            DEFENSE_UNIT_ID,
            EntityType.Builder.create(MingtdUnit::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.95f) // 플레이어와 동일한 크기
                    .build(RegistryKey.of(Registries.ENTITY_TYPE.getKey(), DEFENSE_UNIT_ID))
    );

    @Override
    public void onInitialize() {
        // 패킷 등록 (S2C)
        PayloadTypeRegistry.playS2C().register(OpenRtsScreenPayload.ID, OpenRtsScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MoveUnitPayload.ID, MoveUnitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectUnitPayload.ID, SelectUnitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CameraMovePayload.ID, CameraMovePayload.CODEC);

        // onInitialize에서 속성 등록
        FabricDefaultAttributeRegistry.register(MINGTD_UNIT_TYPE, MingtdUnit.createAttributes());

        // 날씨 및 시간 고정 로직
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld overworld = server.getOverworld();
            DefenseState state = DefenseState.getServerState(overworld);
            var gameRules = overworld.getGameRules();

            if (state.waveStep == 1 && state.monsterCount == 0 && !state.isGameOver) {
                MapGenerator.setupDefenseWorld(overworld, SPAWN_POS);
                // 만약 '중복 생성'을 확실히 막고 싶다면 state에 boolean 변수를 하나 추가하는 것이 가장 좋습니다.
                state.addWisp(overworld, 5);
                state.markDirty();
            }

            // 전역 변수가 있다면 저장된 값으로 동기화 (없다면 생략)
            this.spawnTimer = 0;
            this.waveManager = null; // 매니저는 새로 생성되도록 유지

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

            // 1. 기존에 존재하던 목적지가 있다면 제거 (초기화)
            ScoreboardObjective oldObj = scoreboard.getNullableObjective("monster_count");
            if (oldObj != null) {
                scoreboard.removeObjective(oldObj);
            }

            // 2. 새로 생성
            ScoreboardObjective obj = scoreboard.addObjective(
                    "monster_count",
                    ScoreboardCriterion.DUMMY,
                    Text.literal("👾 몬스터 수").formatted(Formatting.RED, Formatting.BOLD),
                    ScoreboardCriterion.RenderType.INTEGER,
                    true,
                    null
            );

            scoreboard.getOrCreateScore(ScoreHolder.fromName("§b⚡ 보유 위습"), obj).setScore(state.getWispCount());
            scoreboard.getOrCreateScore(ScoreHolder.fromName("§c👾 남은 몬스터"), obj).setScore(0);

                        // 3. 사이드바에 표시
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
        });

        // 플레이어가 접속할 때 시점 설정
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                // [수정] 텔레포트와 게임모드 설정도 이 execute 안에서 하는 것이 더 안전합니다.
                var player = handler.getPlayer();
                player.changeGameMode(GameMode.SPECTATOR);
                var world = server.getOverworld();
                DefenseState state = DefenseState.getServerState(world);

                double x = 0.0;
                double y = DefenseConfig.GROUND_Y + DefenseConfig.CAMERA_HEIGHT;
                double z = -30.0;
                float yaw = 0.0f;
                float pitch = DefenseConfig.CAMERA_PITCH;

                player.teleport(world, x, y, z, Collections.emptySet(), yaw, pitch, false);

                // 2. [추가] 접속한 플레이어에게 스코어보드 강제 동기화
                Scoreboard scoreboard = server.getScoreboard();
                ScoreboardObjective obj = scoreboard.getNullableObjective("monster_count");
                if (obj != null) {
                    // 개인별 점수 칸을 0으로 초기화하거나 현재 몬스터 수로 설정
                    scoreboard.getOrCreateScore(player, obj).setScore(state.monsterCount);
                }

                // 패킷 전송은 여기서 한 번만!
                ServerPlayNetworking.send(player, new OpenRtsScreenPayload());

                // [추가] 환영 메시지 및 현재 자원 안내
                player.sendMessage(Text.literal("§e MingTD에 오신 것을 환영합니다!"), false);
                player.sendMessage(Text.literal("§b 현재 보유 위습: §f" + state.getWispCount() + "개"), false);

            });
        });

        // [초기화 명령어] /mingtd 명령어 세트 등록
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("mingtd")
                    .then(CommandManager.literal("reset")
                            .executes(context -> {
                                ServerWorld world = context.getSource().getWorld();
                                DefenseState state = DefenseState.getServerState(world);

                                // 1. 삭제할 엔티티를 임시 리스트에 수집 (플레이어 제외)
                                List<Entity> toRemove = new ArrayList<>();
                                for (Entity entity : world.iterateEntities()) {
                                    // 플레이어(ServerPlayerEntity)가 아닌 경우에만 삭제 리스트에 추가
                                    if (!(entity instanceof ServerPlayerEntity)) {
                                        toRemove.add(entity);
                                    }
                                }

                                // 2. 수집된 엔티티들을 안전하게 제거
                                toRemove.forEach(Entity::discard);

                                // 3. 데이터 초기화
                                state.isGameOver = false;
                                state.status = DefenseState.GameStatus.RUNNING;
                                state.waveStep = 1;
                                state.monsterCount = 0;
                                state.setWispCount(5); // 기본 위습 지급
                                state.markDirty();

                                // 4. 맵 재생성
                                MapGenerator.setupDefenseWorld(world, SPAWN_POS);

                                context.getSource().sendFeedback(() -> Text.literal("§a디펜스 맵 초기화 및 모든 유닛이 제거되었습니다!"), false);
                                return 1;
                            }))
                    .then(CommandManager.literal("pause")
                            .executes(context -> {
                                DefenseState state = DefenseState.getServerState(context.getSource().getWorld());
                                state.status = DefenseState.GameStatus.PAUSED;
                                state.markDirty();
                                context.getSource().sendFeedback(() -> Text.literal("⏸ 게임이 일시정지되었습니다."), false);
                                return 1;
                            }))
                    .then(CommandManager.literal("resume")
                            .executes(context -> {
                                DefenseState state = DefenseState.getServerState(context.getSource().getWorld());
                                state.status = DefenseState.GameStatus.RUNNING;
                                state.markDirty();
                                context.getSource().sendFeedback(() -> Text.literal("▶️ 게임이 재개되었습니다."), false);
                                return 1;
                            }))
                    // [신규 추가] 랜덤 유닛 소환 명령어
                    .then(CommandManager.literal("summon")
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                ServerWorld world = context.getSource().getWorld();

                                if (player != null) {
                                    // 위습 1개를 소모하여 유닛 소환 (UnitSpawner 연동)
                                    UnitSpawner.spawnRandomUnit(player, world);
                                }
                                return 1;
                            }))
                    .then(CommandManager.literal("camera")
                            .then(CommandManager.argument("height", DoubleArgumentType.doubleArg(10.0, 150.0))
                                    .executes(context -> {
                                        // 1. 입력받은 높이 값 가져오기
                                        double newHeight = DoubleArgumentType.getDouble(context, "height");

                                        // 2. 설정 업데이트 (Pitch는 기존 DefenseConfig 값 유지)
                                        DefenseConfig.CAMERA_HEIGHT = newHeight;

                                        // 3. 실행한 플레이어 시점 즉시 갱신
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        if (player != null) {
                                            ServerWorld world = context.getSource().getWorld();
                                            // X, Z, Yaw는 현재 플레이어 상태 유지, Y와 Pitch만 설정값 적용
                                            player.teleport(world,
                                                    player.getX(),
                                                    DefenseConfig.GROUND_Y + DefenseConfig.CAMERA_HEIGHT,
                                                    player.getZ(),
                                                    java.util.Collections.emptySet(),
                                                    player.getYaw(),
                                                    DefenseConfig.CAMERA_PITCH,
                                                    false
                                            );
                                        }

                                        context.getSource().sendFeedback(() ->
                                                Text.literal("§a카메라 높이가 §e" + newHeight + "§a로 변경되었습니다."), false);
                                        return 1;
                                    })
                            )
                    )
            ); // dispatcher.register 닫기
        }); // Event.register 닫기

        // --- 1. 유닛 이동 명령 처리 (MoveUnitPayload) ---
        ServerPlayNetworking.registerGlobalReceiver(MoveUnitPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                // 1.21.1 표준: context.player().getServerWorld() 또는 getWorld() 사용
                ServerWorld world = (ServerWorld) context.player().getEntityWorld();
                Entity entity = world.getEntityById(payload.entityId());

                // [규칙 4] 오직 우리가 정의한 아군 유닛(MingtdUnit)만 명령을 수행함
                // 만약 몬스터(ZombieEntity)가 패킷으로 들어와도 여기서 차단됨
                if (entity instanceof MingtdUnit unit) {
                    Vec3d target = payload.targetPos();

                    // 유닛 이동 명령 수행 (지면 위 1.0 보정)
                    boolean success = unit.getNavigation().startMovingTo(
                            target.x, target.y + 1.0D, target.z, 1.3D
                    );

                    // 몬스터 추적 AI 초기화
                    unit.setTarget(null);

                    // LOGGER.info("[MingTD] 아군 유닛 이동 명령: " + (success ? "성공" : "실패"));
                } else if (entity instanceof ZombieEntity) {
                    // 몬스터 이동 시도 시 로그 (선택 사항)
                    System.out.println("[Warning] 몬스터 이동 명령 거부됨: " + entity.getId());
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

        ServerPlayNetworking.registerGlobalReceiver(CameraMovePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                ServerWorld world = context.player().getEntityWorld();

                // 1. 새로운 좌표 계산
                double newX = player.getX() + payload.deltaX();
                double newZ = player.getZ() + payload.deltaZ();

                // 2. 높이 계산 및 Config 업데이트 (Alt/Ctrl 입력 반영)
                DefenseConfig.CAMERA_HEIGHT += payload.deltaY();
                double newY = DefenseConfig.GROUND_Y + DefenseConfig.CAMERA_HEIGHT;

                // 3. 텔레포트 (Pitch와 Yaw는 고정)
                player.teleport(
                        world,
                        newX, newY, newZ,
                        java.util.Collections.emptySet(), // Relative 이동 사용 안 함 (절대 좌표 지정)
                        player.getYaw(),
                        DefenseConfig.CAMERA_PITCH,
                        false // 스냅샷 여부
                );
            });
        });


        // 서버 틱 이벤트 등록 (서버가 살아있는 동안 계속 실행됨)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 1. 월드 및 데이터 상태 확인
            ServerWorld world = server.getOverworld();
            if (world == null) return;

            // 저장된 상태 데이터 가져오기
            DefenseState state = DefenseState.getServerState(world);
            // [핵심 수정] 진행 중(RUNNING)이 아니면 모든 계산(타이머, 스폰, 이동)을 중단함
            if (state.status != DefenseState.GameStatus.RUNNING) {
                // 일시정지 중에는 몬스터들이 멈춰있어야 하므로 이동 로직(tickMonsters)도 건너뜁니다.
                return;
            }

            // 게임 오버 상태라면 모든 로직 중단
            if (state.isGameOver) return;

            // 2. WaveManager 초기화 및 몬스터 스폰
            if (waveManager == null) {
                waveManager = new WaveManager(world, SPAWN_POS);
            }

            spawnTimer++;
            if (spawnTimer >= 20) {
                spawnTimer = 0;
                waveManager.spawnMonster();
            }

            // 3. 필드 내 활성 몬스터 수집 및 카운트
            List<ZombieEntity> activeZombies = new ArrayList<>();
            Vec3d centerPos = Vec3d.ofCenter(SPAWN_POS);

            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof ZombieEntity zombie && zombie.isAlive() && entity.getType() != Mingtd.MINGTD_UNIT_TYPE) {
                    // [수정] getPos() 대신 getX, getY, getZ를 사용하여 Vec3d를 직접 생성
                    Vec3d zombiePos = new Vec3d(zombie.getX(), zombie.getY(), zombie.getZ());

                    if (zombiePos.distanceTo(centerPos) < 50.0) {
                        activeZombies.add(zombie);
                    }
                }

                // 아군: 커스텀 타입인 경우
                if (entity.getType() == Mingtd.MINGTD_UNIT_TYPE) {
                    // RTS 선택 및 이동 패킷 대상
                }
            }



            // 전역 변수 및 상태 데이터 업데이트
            // 1. 업데이트 전의 값을 미리 보관
            int previousCount = state.monsterCount;

            // 2. 새로운 값을 계산하여 대입
            int currentCount = activeZombies.size();
            state.monsterCount = currentCount;

            // 3. 이전 값과 현재 값을 비교
            if (previousCount != currentCount) {
                state.markDirty(); // 값이 변했을 때만 저장 예약
                LOGGER.info("💾 [데이터 변경] 몬스터 수: {} -> {} (저장 예약 완료)", previousCount, currentCount);
            } else {
                // 디버깅이 끝나면 이 else 문은 지우셔도 됩니다.
                // LOGGER.info("ℹ️ 변화 없음: {}마리 유지 중", currentCount);
            }

            // 4. 스코어보드 업데이트 (사이드바)
            Scoreboard scoreboard = server.getScoreboard();
            ScoreboardObjective obj = scoreboard.getNullableObjective("monster_count");

            // 목적지가 없다면 생성 (최초 1회)
            if (obj == null) {
                obj = scoreboard.addObjective(
                        "monster_count",
                        ScoreboardCriterion.DUMMY,
                        Text.literal("👾 몬스터 수").formatted(Formatting.RED, Formatting.BOLD),
                        ScoreboardCriterion.RenderType.INTEGER,
                        true,
                        null
                );
                scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
            }

            // 접속 중인 모든 플레이어에게 현재 카운트 표시
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                scoreboard.getOrCreateScore(player, obj).setScore(state.monsterCount);
            }

            scoreboard.getOrCreateScore(ScoreHolder.fromName("§b⚡ 보유 위습"), obj).setScore(state.getWispCount());

            // 5. 게임 종료 및 경고 조건 체크
            // 100마리 초과 시 종료
            if (state.monsterCount > MAX_MONSTER_COUNT) {
                state.isGameOver = true;
                state.markDirty(); // [중요] 상태 저장 예약
                triggerGameOver(server);
                return;
            }

            // 90마리 이상 시 액션바 경고 (1초 주기)
            if (state.monsterCount >= 90 && server.getTicks() % 20 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.sendMessage(Text.literal("⚠️ 경고: 몬스터 한계치 도달! (" + state.monsterCount + "/100)")
                            .formatted(Formatting.YELLOW), true);
                }
            }

            // 6. 몬스터 AI 동작 (WaveManager)
            waveManager.tickMonsters(activeZombies);
        });
    }

    // 1. 패킷 데이터 정의
    public record OpenRtsScreenPayload() implements CustomPayload {
        public static final Id<OpenRtsScreenPayload> ID = new Id<>(Identifier.of("mingtd", "open_screen"));
        public static final PacketCodec<RegistryByteBuf, OpenRtsScreenPayload> CODEC = PacketCodec.unit(new OpenRtsScreenPayload());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    private void triggerGameOver(MinecraftServer server) {
        Text gameOverText = Text.literal("\n[ GAME OVER ]\n")
                .append(Text.literal("몬스터가 " + MAX_MONSTER_COUNT + "마리를 초과하여 패배했습니다!"))
                .formatted(Formatting.RED, Formatting.BOLD);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // 1. 메시지 전송
            player.sendMessage(gameOverText, false);
        }

        // 4. 필드 청소 (안전한 제거를 위해 리스트 복사 후 처리)
        ServerWorld world = server.getOverworld();
        if (world != null) {
            // iterateEntities() 도중 discard()를 하면 ConcurrentModificationException이 발생할 수 있으므로
            // 제거할 대상을 리스트에 먼저 모은 뒤 한꺼번에 지웁니다.
            List<Entity> toRemove = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof ZombieEntity) {
                    toRemove.add(entity);
                }
            }
            toRemove.forEach(Entity::discard);
        }
    }
}
