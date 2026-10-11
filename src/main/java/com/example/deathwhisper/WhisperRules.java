package com.example.deathwhisper;

import net.minecraft.world.level.GameRules;

public final class WhisperRules {
    private WhisperRules() {}
    public static final GameRules.Key<GameRules.BooleanValue> ENABLED = GameRules.register("deathWhisperEnabled", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> FAKE_CHARGES = GameRules.register("deathWhisperFakeCharges", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> REPOSITION = GameRules.register("deathWhisperReposition", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> UNDERGROUND = GameRules.register("deathWhisperUnderground", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> NO_DAMAGE = GameRules.register("deathWhisperNoDamage", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.IntegerValue> COOLDOWN = GameRules.register("deathWhisperCooldown", GameRules.Category.MOBS, GameRules.IntegerValue.create(900));
    public static final GameRules.Key<GameRules.IntegerValue> CHANCE = GameRules.register("deathWhisperChance", GameRules.Category.MOBS, GameRules.IntegerValue.create(30));
    public static final GameRules.Key<GameRules.IntegerValue> MIN_DISTANCE = GameRules.register("deathWhisperMinDistance", GameRules.Category.MOBS, GameRules.IntegerValue.create(20));
    public static final GameRules.Key<GameRules.IntegerValue> MAX_DISTANCE = GameRules.register("deathWhisperMaxDistance", GameRules.Category.MOBS, GameRules.IntegerValue.create(48));
    public static final GameRules.Key<GameRules.IntegerValue> DURATION = GameRules.register("deathWhisperDuration", GameRules.Category.MOBS, GameRules.IntegerValue.create(25));
    public static final GameRules.Key<GameRules.IntegerValue> CHARGE_CHANCE = GameRules.register("deathWhisperChargeChance", GameRules.Category.MOBS, GameRules.IntegerValue.create(15));
    public static final GameRules.Key<GameRules.IntegerValue> THREAT_GAIN = GameRules.register("deathWhisperThreatGain", GameRules.Category.MOBS, GameRules.IntegerValue.create(10));
}
