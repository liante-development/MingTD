package com.liante;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.datafixer.DataFixTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefenseState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger("MingTD-Debug");
    // 게임 상태 정의
    public enum GameStatus { READY, RUNNING, PAUSED, GAMEOVER }

    public GameStatus status = GameStatus.READY; // 기본값은 대기

    public int waveStep = 1;
    public boolean isGameOver = false;
    public int monsterCount = 0;

    private int wispCount = 0; // 위습 자원

    // Codec도 업데이트해야 함 (상태값 추가)
    public static final Codec<DefenseState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("status").forGetter(s -> s.status.name()),
                    Codec.INT.fieldOf("waveStep").forGetter(s -> s.waveStep),
                    Codec.INT.fieldOf("monsterCount").forGetter(s -> s.monsterCount),
                    Codec.INT.fieldOf("wispCount").forGetter(s -> s.wispCount)
            ).apply(instance, (statusName, wave, count, wisp) -> { // wisp 변수명 수정
                DefenseState state = new DefenseState();
                state.status = GameStatus.valueOf(statusName);
                state.waveStep = wave;
                state.monsterCount = count;
                state.wispCount = wisp; // count 대신 wisp 대입 (버그 수정)
                return state;
            })
    );

    public DefenseState(int waveStep, boolean isGameOver, int monsterCount, int wispCount) {
        this.waveStep = waveStep;
        this.isGameOver = isGameOver;
        this.monsterCount = monsterCount;
        this.wispCount = wispCount;
    }

    public DefenseState() {
        // [로그] 저장된 데이터가 없어 새로 생성될 때 호출됨
        LOGGER.info("🆕 [신규 생성] 저장된 데이터가 없어 기본 상태로 시작합니다.");
    }

    // [중요] TYPE을 반드시 static final 상수로 고정해야 함
    public static final PersistentStateType<DefenseState> TYPE = new PersistentStateType<>(
            "mingtd_state",
            DefenseState::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    public static DefenseState getServerState(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public int getWispCount() { return wispCount; }

    public void addWisp(ServerWorld world, int amount) {
        this.wispCount += amount;
        this.markDirty();
        updateScoreboard(world);
    }

    public void setWispCount(int amount) {
        this.wispCount = amount;

        // [중요] 데이터가 변경되었음을 서버에 알려 저장되도록 함
        this.markDirty();

        // UI 업데이트를 위해 LOGGER에 기록하거나,
        // Mingtd 클래스의 틱 이벤트에서 스코어보드가 자동으로 갱신되므로
        // 여기서는 데이터 무결성만 보장합니다.
        LOGGER.info("💰 [자원 설정] 위습 개수가 {}개로 설정되었습니다.", amount);
    }

    public boolean consumeWisp(ServerWorld world, int amount) {
        if (this.wispCount >= amount) {
            this.wispCount -= amount;
            this.markDirty();
            updateScoreboard(world);
            return true;
        }
        return false;
    }

    public void updateScoreboard(ServerWorld world) {
        // 모든 플레이어의 사이드바에 위습 수치를 갱신하는 로직
        // 실제 구현 시에는 ScoreboardManager 같은 별도 클래스를 호출하는 것이 깔끔합니다.
        world.getPlayers().forEach(player -> {
            // 여기서 간단하게 메시지로 먼저 테스트하거나, 실제 점수판 API를 호출하세요.
            player.sendMessage(net.minecraft.text.Text.literal("§b현재 위습: §e" + this.wispCount), true);
        });
    }


}