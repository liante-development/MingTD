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
        WARRIOR("§f[전사]", Items.IRON_SWORD, 8.0f, 4.0f),  // 대미지 8, 사거리 4
        ARCHER("§e[궁수]", Items.BOW, 5.0f, 16.0f),       // 대미지 5, 사거리 16
        MAGE("§b[법사]", Items.STICK, 12.0f, 12.0f),      // 대미지 12, 사거리 12
        ROGUE("§6[도적]", Items.IRON_AXE, 6.0f, 6.0f);     // 대미지 6, 사거리 6

        private final String displayName;
        private final Item defaultItem;
        private final float damage;  // 추가
        private final float range;   // 추가

        DefenseUnit(String displayName, Item defaultItem, float damage, float range) {
            this.displayName = displayName;
            this.defaultItem = defaultItem;
            this.damage = damage;
            this.range = range;
        }

        public Item getMainItem() { return this.defaultItem; }
        public String getDisplayName() { return this.displayName; }
        public float getDamage() { return this.damage; } // Getter 추가
        public float getRange() { return this.range; }   // Getter 추가
    }

    public static void spawnRandomUnit(ServerPlayerEntity player, ServerWorld world) {
        DefenseState state = DefenseState.getServerState(world);
        if (!state.consumeWisp(world, 1)) {
            player.sendMessage(Text.literal("§c위습이 부족합니다!"), true);
            return;
        }

        DefenseUnit selectedUnit = DefenseUnit.values()[world.random.nextInt(DefenseUnit.values().length)];
        MingtdUnit unitEntity = Mingtd.MINGTD_UNIT_TYPE.create(world, SpawnReason.MOB_SUMMONED);

        if (unitEntity != null) {
            unitEntity.refreshPositionAndAngles(
                    Mingtd.SPAWN_POS.getX() + 0.5,
                    Mingtd.SPAWN_POS.getY() + 1.0,
                    Mingtd.SPAWN_POS.getZ() + 0.5,
                    0, 0
            );

            // 1. 직업 데이터 주입 (내부에서 아이템 장착 + AI 갱신 수행)
            unitEntity.setUnitType(selectedUnit);

            // 2. 이름 설정
            unitEntity.setCustomName(Text.literal(selectedUnit.getDisplayName()));
            unitEntity.setCustomNameVisible(true);

            // 3. 월드에 소환
            world.spawnEntity(unitEntity);

            player.sendMessage(Text.literal(selectedUnit.getDisplayName() + "§a 유닛이 소환되었습니다!"), true);
        }
    }
}
