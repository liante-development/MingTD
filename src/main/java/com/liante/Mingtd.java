package com.liante;

import com.liante.config.DefenseConfig;
import com.liante.config.DefenseState;
import com.liante.manager.CameraMovePayload;
import com.liante.manager.UpgradeManager;
import com.liante.manager.WaveManager;
import com.liante.map.MapGenerator;
import com.liante.network.*;
import com.liante.spawner.UnitSpawner;
import com.liante.unit.UnitDataLoader;
import com.liante.unit.UnitInfo;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.resource.ResourceType;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.rule.GameRules;

import java.lang.reflect.Field;
import java.util.*;

import static com.liante.ModEntities.DEFENSE_MONSTER_TYPE;
import static com.liante.ModEntities.MINGTD_UNIT_TYPE;
import static com.liante.unit.MingtdUnits.UNIT_REGISTRY;
import static com.mojang.text2speech.Narrator.LOGGER;

public class Mingtd implements ModInitializer {
    // 맵의 기준 좌표 (0, 100, 0 등 고정된 위치 권장)
    public static final BlockPos SPAWN_POS = new BlockPos(0, 100, 0);
    // 클래스 레벨에서 변수 선언 (나중에 초기화)
    private WaveManager waveManager;
    private int spawnTimer = 0;

    public static final int MAX_MONSTER_COUNT = 100;

    @Override
    public void onInitialize() {
        // 패킷 등록 (S2C)
        PayloadTypeRegistry.playS2C().register(OpenRtsScreenPayload.ID, OpenRtsScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MoveUnitPayload.ID, MoveUnitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectUnitPayload.ID, SelectUnitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CameraMovePayload.ID, CameraMovePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UnitStatPayload.ID, UnitStatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MultiUnitPayload.ID, MultiUnitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UnitInventoryPayload.ID, UnitInventoryPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpgradeRequestPayload.ID, UpgradeRequestPayload.CODEC);

        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(
                Identifier.of("mingtd", "units"),
                new UnitDataLoader() // 새로 만들 유닛 데이터 로더
        );

        ModAttributes.registerAttributes();
        ModEntities.registerEntities();

        // 몬스터 엔티티 타입에 해당 속성들을 주입 (이곳에 추가!)
        FabricDefaultAttributeRegistry.register(
                ModEntities.DEFENSE_MONSTER_TYPE,
                DefenseMonsterEntity.createMonsterAttributes() // 클래스 내부에 정의한 빌더 호출
        );

        expandHealthLimit();


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
                UnitInventoryPayload.sendSync(player);

                // [추가] 환영 메시지 및 현재 자원 안내
                player.sendMessage(Text.literal("§e MingTD에 오신 것을 환영합니다!"), false);
                player.sendMessage(Text.literal("§b 현재 보유 위습: §f" + state.getWispCount() + "개"), false);

            });
        });

        // [초기화 명령어] /mingtd 명령어 세트 등록
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("mt")
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
                                state.setWispCount(20); // 기본 위습 지급
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
                    .then(CommandManager.literal("s")
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                ServerWorld world = context.getSource().getWorld();

                                if (player != null) {
                                    // 위습 1개를 소모하여 유닛 소환 (UnitSpawner 연동)
                                    UnitSpawner.spawnRandomUnit(player, world);
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
                        ))
                    .then(CommandManager.literal("upgrade")
                            .executes(context -> {
//                                UpgradeManager.tryUpgrade(context.getSource().getPlayer());
                                return 1;
                        }))
                    .then(CommandManager.literal("mannequin")
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("unit_id", StringArgumentType.string())
                                        .executes(context -> {
                                            // 예시: /mp mannequin <x> <y> <z> <unit_id>

                                            ServerPlayerEntity player = context.getSource().getPlayer();
                                            ServerWorld world = context.getSource().getWorld();
                                            BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");

                                            // 2. 위에서 정의한 이름과 똑같이 "unit_id"로 가져옵니다.
                                            String unitId = StringArgumentType.getString(context, "unit_id");

                                            try {
                                                UnitInfo unit = UNIT_REGISTRY.get(unitId);
                                                UnitSpawner.spawnMannequin(player, world, pos, unit);
                                                return 1;
                                            } catch (Exception e) {
                                                context.getSource().sendError(Text.literal("§c존재하지 않는 유닛 ID입니다: " + unitId));
                                                return 0;
                                            }
                            }))))
                    .then(CommandManager.literal("dummy")
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        ServerWorld world = context.getSource().getWorld();
                                        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");

                                        UnitSpawner.spawnDummy(player, world, pos);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("debug_pos")
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                ServerWorld world = context.getSource().getWorld();

                                // [1.21.2 최신화] world.iterateEntities() 또는 iterateEntities()를 통해 순회
                                for (Entity entity : world.iterateEntities()) {

//                                    // 1. 살아있는 모든 적대적 몹(Monster/HostileEntity) 찾기
//                                    // Yarn 1.21.2에서는 HostileEntity가 적대적 몹의 표준 클래스입니다.
//                                    if (entity instanceof HostileEntity hostile && hostile.isAlive()) {
//                                        // [수정] getPos() 대신 직접 좌표 메서드 호출 (Yarn 컨벤션)
//                                        LOGGER.info("[MingtdDebug] 몬스터 포착: {} | 좌표: X={}, Y={}, Z={}",
//                                                entity.getName().getString(),
//                                                String.format("%.3f", entity.getX()),
//                                                String.format("%.3f", entity.getY()),
//                                                String.format("%.3f", entity.getZ()));
//
//                                        // 히트박스 정보 추가 (isPickable 상태 확인 포함)
//                                        Box box = entity.getBoundingBox();
//                                        LOGGER.info(" -> 히트박스 범위: [MinY:{}, MaxY:{}] ",
//                                                box.minY, box.maxY);
//                                    }

                                    // 2. 아군 유닛 찾기 (MingtdUnit 클래스 타입 체크)
                                    if (entity instanceof MingtdUnit unit) {
                                        // [수정] getEntityWorld() 및 좌표 메서드 사용
//                                        LOGGER.info("[MingtdDebug] 유닛 포착: {} | 좌표: X={}, Y={}, Z={}",
//                                                unit.getName().getString(),
//                                                String.format("%.3f", unit.getX()),
//                                                String.format("%.3f", unit.getY()),
//                                                String.format("%.3f", unit.getZ()));

                                        Box box = unit.getBoundingBox();
                                        // [MingtdDebug] 선택 오류의 주원인인 isPickable 값을 반드시 로그로 확인하세요.
//                                        LOGGER.info(" -> 히트박스 범위: [MinY:{}, MaxY:{}]",
//                                                box.minY, box.maxY);
                                    }
                                }
                                return 1;
                            }))
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
                    // 전용 메서드 호출로 상태 관리와 이동을 동시에 처리
                    unit.startManualMove(target.x, target.y + 1.0D, target.z, 1.3D);

//                    LOGGER.info("[MingTD] 수동 이동 모드 활성화: {}", target);
                } else if (entity instanceof DefenseMonsterEntity) {
                    // 몬스터 이동 시도 시 로그 (선택 사항)
//                    LOGGER.info("[Warning] 몬스터 이동 명령 거부됨: " + entity.getId());
                }
            });
        });

// --- 2. 유닛 선택 및 팀 색상 처리 (SelectUnitPayload) ---
        ServerPlayNetworking.registerGlobalReceiver(SelectUnitPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerWorld world = (ServerWorld) context.player().getEntityWorld();
                ServerPlayerEntity player = context.player();
                Scoreboard scoreboard = context.server().getScoreboard();
                Team team = scoreboard.getTeam("selected_units");
                if (team == null) return;

                // 1. 기존 팀 초기화 및 발광 해제
                List<String> toRemove = new ArrayList<>(team.getPlayerList());
                for (String name : toRemove) {
                    for (Entity e : world.iterateEntities()) {
                        if (e.getNameForScoreboard().equals(name)) {
                            e.setGlowing(false);
                            break;
                        }
                    }
                    scoreboard.removeScoreHolderFromTeam(name, team);
                }

                // 2. 다중 선택 정보 수집을 위한 리스트
                List<MultiUnitPayload.UnitEntry> entries = new ArrayList<>();
                List<Integer> ids = payload.entityIds();

                // 3. 새로운 유닛 추가 및 데이터 수집
                for (int id : ids) {
                    Entity entity = world.getEntityById(id);
                    if (entity != null) {
                        entity.setGlowing(true);
                        scoreboard.addScoreHolderToTeam(entity.getNameForScoreboard(), team);

                        // HUD용 요약 정보 생성
                        if (entity instanceof MingtdUnit unit) {
                            UnitInfo type = unit.getUnitType();
                            entries.add(new MultiUnitPayload.UnitEntry(
                                    unit.getEntity().getId(),
                                    type.id(),
                                    type.name(),
                                    type.rarity().ordinal() // Enum에 priority 필드 추가 필요
                            ));
                        } else if (entity instanceof DefenseMonsterEntity monster) {
                            entries.add(new MultiUnitPayload.UnitEntry(monster.getId(), "MONSTER", monster.getName().getString(), 0));
                        }
                    }
                }

                // 4. 우선순위 정렬 (높은 순서대로)
                entries.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

                // 5. 클라이언트에 다중 유닛 정보 전송 (HUD 갱신)
                ServerPlayNetworking.send(player, new MultiUnitPayload(entries));

                // 6. [중요] 대표 유닛 상세 정보 전송 (단일 선택창 호환용)
                if (!entries.isEmpty()) {
                    Entity firstEntity = world.getEntityById(entries.get(0).entityId());
                    if (firstEntity instanceof MingtdUnit unit) {
                        unit.syncUnitStatsToClient(player);
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

        ServerPlayNetworking.registerGlobalReceiver(UpgradeRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            LOGGER.info("[MingtdDebug] ServerPlayNetworking UpgradeRequestPayload ");
            // [수정] 클라이언트가 보낸 3가지 핵심 정보를 추출
            String recipeId = payload.recipeId();
            String resultId = payload.resultId();
            int mainUnitId = payload.mainUnitEntityId();
            List<Integer> ingredientIds = payload.ingredientIds(); // 새로 추가된 재료 목록

            context.server().execute(() -> {
                // [수정] UpgradeManager에 재료 목록까지 함께 넘겨줍니다.
                UpgradeManager.tryUpgrade(player, recipeId, resultId, mainUnitId, ingredientIds);

                // 결과와 상관없이 항상 최신 상태를 동기화하여 HUD를 갱신합니다.
                UnitInventoryPayload.sendSync(player);
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
            List<DefenseMonsterEntity> activeMonsters = new ArrayList<>();
            Vec3d centerPos = Vec3d.ofCenter(SPAWN_POS);

            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof DefenseMonsterEntity monster && monster.isAlive() && entity.getType() == DEFENSE_MONSTER_TYPE) {
                    // [수정] getPos() 대신 getX, getY, getZ를 사용하여 Vec3d를 직접 생성
                    Vec3d zombiePos = new Vec3d(monster.getX(), monster.getY(), monster.getZ());

                    // 더미인지 확인
                    boolean isDummy = monster.getCommandTags().contains("dummy");
                    if (zombiePos.distanceTo(centerPos) < 50.0) {
                        if (isDummy) {
                            // 더미는 체력바만 업데이트하고 이동 리스트(activeMonsters)에는 넣지 않음
                            waveManager.updateDummyHealthBar(monster);
                        } else {
                            activeMonsters.add(monster);
                        }
                    }
                }

                // 아군: 커스텀 타입인 경우
                if (entity.getType() == MINGTD_UNIT_TYPE && entity.isAlive()) {
                    // RTS 선택 및 이동 패킷 대상
                }
            }



            // 전역 변수 및 상태 데이터 업데이트
            // 1. 업데이트 전의 값을 미리 보관
            int previousCount = state.monsterCount;

            // 2. 새로운 값을 계산하여 대입
            int currentCount = activeMonsters.size();
            state.monsterCount = currentCount;

            // 3. 이전 값과 현재 값을 비교
            if (previousCount != currentCount) {
                state.markDirty(); // 값이 변했을 때만 저장 예약
//                LOGGER.info("💾 [데이터 변경] 몬스터 수: {} -> {} (저장 예약 완료)", previousCount, currentCount);
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
            waveManager.tickMonsters(activeMonsters);
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
                if (entity instanceof DefenseMonsterEntity) {
                    toRemove.add(entity);
                }
            }
            toRemove.forEach(Entity::discard);
        }
    }

    public static void expandHealthLimit() {
        try {
            // 1. MAX_HEALTH 어트리뷰트 객체를 가져옴 (RegistryEntry에서 실제 객체 추출)
            Object attribute = EntityAttributes.MAX_HEALTH.value();

            if (attribute instanceof ClampedEntityAttribute clamped) {
                // 2. ClampedEntityAttribute의 maxValue 필드에 접근 (자바 리플렉션)
                // 'maxValue'는 마인크래프트 내부 필드 이름입니다.
                Field maxValueField = ClampedEntityAttribute.class.getDeclaredField("maxValue");
                maxValueField.setAccessible(true); // 프라이빗 필드 접근 허용

                // 3. 제한 값을 1,000,000으로 수정
                maxValueField.setDouble(clamped, 1000000.0);

                System.out.println("[MingTD] 체력 제한이 1,000,000으로 성공적으로 해제되었습니다.");
            }
        } catch (Exception e) {
            System.err.println("[MingTD] 체력 제한 해제 중 오류 발생: " + e.getMessage());
        }
    }
}
