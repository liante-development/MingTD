package com.liante.spawner;

import com.liante.DefenseState;
import com.liante.Mingtd;
import com.liante.MingtdUnit;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class UnitSpawner {
    public enum DefenseUnit {
        WARRIOR("§f[전사]", Items.IRON_SWORD),
        ARCHER("§e[궁수]", Items.BOW),
        MAGE("§b[법사]", Items.STICK),
        ROGUE("§6[도적]", Items.IRON_AXE);

        final String displayName;
        final Item defaultItem;

        DefenseUnit(String name, Item item) {
            this.displayName = name;
            this.defaultItem = item;
        }
    }

    public static void spawnRandomUnit(ServerPlayerEntity player, ServerWorld world) {
        DefenseState state = DefenseState.getServerState(world);
        if (!state.consumeWisp(world, 1)) {
            player.sendMessage(Text.literal("§c위습이 부족합니다!"), true);
            return;
        }

        // 1. [핵심 추가] 랜덤 유닛 결정 로직
        DefenseUnit[] units = DefenseUnit.values();
        DefenseUnit selectedUnit = units[world.random.nextInt(units.length)];

        // 2. 커스텀 엔티티 생성
        MingtdUnit unitEntity = Mingtd.MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);

        if (unitEntity != null) {
            // 위치 설정
            unitEntity.refreshPositionAndAngles(
                    Mingtd.SPAWN_POS.getX() + 0.5,
                    Mingtd.SPAWN_POS.getY() + 1.0,
                    Mingtd.SPAWN_POS.getZ() + 0.5,
                    0, 0
            );

            // 3. [수정] 선택된 유닛에 맞는 이름과 아이템 적용
            unitEntity.setCustomName(Text.literal(selectedUnit.displayName));
            unitEntity.setCustomNameVisible(true);

            // 주손에 아이템 장착 (직업 구분용)
            unitEntity.equipStack(EquipmentSlot.MAINHAND, new ItemStack(selectedUnit.defaultItem));
//            unitEntity.equipStack(EquipmentSlot.MAINHAND, new ItemStack(selectedUnit.defaultItem));

            world.spawnEntity(unitEntity);

            // 플레이어에게 어떤 직업이 나왔는지 알림
            player.sendMessage(Text.literal(selectedUnit.displayName + "§a 유닛이 소환되었습니다!"), true);
        }
    }
}
