package com.liante.unit;

import net.minecraft.util.Formatting;

public enum Rarity {
    NORMAL(Formatting.WHITE, 0.6),
    MAGIC(Formatting.BLUE, 0.3),
    RARE(Formatting.YELLOW, 0.08),
    UNIQUE(Formatting.GOLD, 0.02);

    public final Formatting color;
    public final double spawnChance;
    Rarity(Formatting color, double spawnChance) {
        this.color = color;
        this.spawnChance = spawnChance;
    }
}
