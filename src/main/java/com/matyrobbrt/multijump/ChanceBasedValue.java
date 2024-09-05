package com.matyrobbrt.multijump;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record ChanceBasedValue(LevelBasedValue chance, LevelBasedValue value, LevelBasedValue fallback) implements LevelBasedValue {
    public static final MapCodec<ChanceBasedValue> CODEC = RecordCodecBuilder.mapCodec(in -> in.group(
        LevelBasedValue.CODEC.fieldOf("chance").forGetter(ChanceBasedValue::chance),
        LevelBasedValue.CODEC.fieldOf("value").forGetter(ChanceBasedValue::value),
        LevelBasedValue.CODEC.fieldOf("fallback").forGetter(ChanceBasedValue::fallback)
    ).apply(in, ChanceBasedValue::new));

    @Override
    public float calculate(int level) {
        return Math.random() <= chance.calculate(level) ? value.calculate(level) : fallback.calculate(level);
    }

    @Override
    public MapCodec<? extends LevelBasedValue> codec() {
        return CODEC;
    }
}
