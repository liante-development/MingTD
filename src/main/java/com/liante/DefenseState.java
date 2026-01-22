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

    // Codec도 업데이트해야 함 (상태값 추가)
    public static final Codec<DefenseState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("status").forGetter(s -> s.status.name()), // Enum을 문자열로 저장
                    Codec.INT.fieldOf("waveStep").forGetter(s -> s.waveStep),
                    Codec.INT.fieldOf("monsterCount").forGetter(s -> s.monsterCount)
            ).apply(instance, (statusName, wave, count) -> {
                DefenseState state = new DefenseState();
                state.status = GameStatus.valueOf(statusName);
                state.waveStep = wave;
                state.monsterCount = count;
                return state;
            })
    );

    public DefenseState(int waveStep, boolean isGameOver, int monsterCount) {
        this.waveStep = waveStep;
        this.isGameOver = isGameOver;
        this.monsterCount = monsterCount;
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
}