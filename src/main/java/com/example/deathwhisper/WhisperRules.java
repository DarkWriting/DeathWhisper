package com.example.deathwhisper;
import net.minecraft.world.level.GameRules;

/** World-save gamerules: /gamerule deathWhisper... */
public final class WhisperRules {
    private WhisperRules(){}
    private static GameRules.Key<GameRules.BooleanValue> bool(String n, boolean d) {
        return GameRules.register("deathWhisper"+n, GameRules.Category.MOBS, GameRules.BooleanValue.create(d));
    }
    private static GameRules.Key<GameRules.IntegerValue> integer(String n, int d) {
        return GameRules.register("deathWhisper"+n, GameRules.Category.MOBS, GameRules.IntegerValue.create(d));
    }
    public static final GameRules.Key<GameRules.BooleanValue> ENABLED = bool("Enabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> REPOSITION = bool("Reposition", true);
    public static final GameRules.Key<GameRules.BooleanValue> FAKE_CHARGES = bool("FakeCharges", true);
    public static final GameRules.Key<GameRules.BooleanValue> UNDERGROUND = bool("Underground", true);
    public static final GameRules.Key<GameRules.BooleanValue> NO_DAMAGE = bool("NoDamage", true);
    public static final GameRules.Key<GameRules.BooleanValue> STEALTH = bool("Stealth", true);
    public static final GameRules.Key<GameRules.BooleanValue> PEEKING = bool("Peeking", true);
    public static final GameRules.Key<GameRules.BooleanValue> PREDICTION = bool("Prediction", true);
    public static final GameRules.Key<GameRules.BooleanValue> MEMORY = bool("Memory", true);
    public static final GameRules.Key<GameRules.BooleanValue> BEHAVIOR_ADAPT = bool("BehaviorAdapt", true);
    public static final GameRules.Key<GameRules.BooleanValue> ISOLATION = bool("Isolation", true);
    public static final GameRules.Key<GameRules.BooleanValue> GROUP_AVOIDANCE = bool("GroupAvoidance", true);
    public static final GameRules.Key<GameRules.BooleanValue> WEATHER = bool("Weather", true);
    public static final GameRules.Key<GameRules.BooleanValue> BIOME_BEHAVIOR = bool("BiomeBehavior", true);
    public static final GameRules.Key<GameRules.BooleanValue> STRUCTURE_BEHAVIOR = bool("StructureBehavior", true);
    public static final GameRules.Key<GameRules.BooleanValue> ELEVATED_WATCH = bool("ElevatedWatch", true);
    public static final GameRules.Key<GameRules.BooleanValue> SILENT_ENCOUNTERS = bool("SilentEncounters", true);
    public static final GameRules.Key<GameRules.BooleanValue> FALSE_SIGHTINGS = bool("FalseSightings", true);
    public static final GameRules.Key<GameRules.BooleanValue> FAKE_DEPARTURES = bool("FakeDepartures", true);
    public static final GameRules.Key<GameRules.BooleanValue> DIRECTIONAL_AUDIO = bool("DirectionalAudio", true);
    public static final GameRules.Key<GameRules.BooleanValue> FOOTSTEPS = bool("Footsteps", true);
    public static final GameRules.Key<GameRules.BooleanValue> LAUGHTER = bool("Laughter", true);
    public static final GameRules.Key<GameRules.BooleanValue> AMBIENCE = bool("Ambience", true);
    public static final GameRules.Key<GameRules.BooleanValue> EYE_GLOW = bool("EyeGlow", true);
    public static final GameRules.Key<GameRules.BooleanValue> PRIVATE_FOCUS = bool("PrivateFocus", true);
    public static final GameRules.Key<GameRules.BooleanValue> DEBUG = bool("Debug", false);
    public static final GameRules.Key<GameRules.IntegerValue> COOLDOWN = integer("Cooldown", 900);
    public static final GameRules.Key<GameRules.IntegerValue> CHANCE = integer("Chance", 30);
    public static final GameRules.Key<GameRules.IntegerValue> MIN_DISTANCE = integer("MinDistance", 20);
    public static final GameRules.Key<GameRules.IntegerValue> MAX_DISTANCE = integer("MaxDistance", 48);
    public static final GameRules.Key<GameRules.IntegerValue> DURATION = integer("Duration", 45);
    public static final GameRules.Key<GameRules.IntegerValue> CHARGE_CHANCE = integer("ChargeChance", 10);
    public static final GameRules.Key<GameRules.IntegerValue> THREAT_GAIN = integer("ThreatGain", 10);
    public static final GameRules.Key<GameRules.IntegerValue> MAX_ACTIVE = integer("MaxActive", 2);
    public static final GameRules.Key<GameRules.IntegerValue> PATH_BUDGET = integer("PathBudget", 10);
    public static final GameRules.Key<GameRules.IntegerValue> RELOCATIONS = integer("Relocations", 4);
    public static final GameRules.Key<GameRules.IntegerValue> PEEK_SECONDS = integer("PeekSeconds", 3);
    public static final GameRules.Key<GameRules.IntegerValue> STARE_SECONDS = integer("StareSeconds", 5);
    public static final GameRules.Key<GameRules.IntegerValue> SILENT_CHANCE = integer("SilentChance", 25);
    public static final GameRules.Key<GameRules.IntegerValue> SIGHTING_CHANCE = integer("SightingChance", 15);
    public static final GameRules.Key<GameRules.IntegerValue> FOOTSTEP_CHANCE = integer("FootstepChance", 25);
    public static final GameRules.Key<GameRules.IntegerValue> WHISTLE_VOLUME = integer("WhistleVolume", 100);
    public static final GameRules.Key<GameRules.IntegerValue> THREAT_DECAY = integer("ThreatDecay", 1);
    public static final GameRules.Key<GameRules.IntegerValue> ISOLATION_BONUS = integer("IsolationBonus", 15);
    public static final GameRules.Key<GameRules.IntegerValue> GROUP_RADIUS = integer("GroupRadius", 30);
    public static final GameRules.Key<GameRules.IntegerValue> MIN_PATH_DISTANCE = integer("MinPathDistance", 6);
    public static final GameRules.Key<GameRules.IntegerValue> MAX_PATH_DISTANCE = integer("MaxPathDistance", 22);

    public static final GameRules.Key<GameRules.BooleanValue> POWERS_ENABLED = bool("PowersEnabled", true);
    public static final GameRules.Key<GameRules.BooleanValue> POWERS_PVP = bool("PowersPvp", false);
    public static final GameRules.Key<GameRules.IntegerValue> CHAINS_RANGE = integer("ChainsRange", 22);
    public static final GameRules.Key<GameRules.IntegerValue> CHAINS_SECONDS = integer("ChainsSeconds", 4);
    public static final GameRules.Key<GameRules.IntegerValue> CHAINS_COOLDOWN = integer("ChainsCooldown", 18);
    public static final GameRules.Key<GameRules.IntegerValue> CHAINS_TARGETS = integer("ChainsTargets", 3);
    public static final GameRules.Key<GameRules.IntegerValue> RUSH_SPEED = integer("RushSpeed", 115);
    public static final GameRules.Key<GameRules.IntegerValue> RUSH_CHARGES = integer("RushCharges", 5);
    public static final GameRules.Key<GameRules.IntegerValue> RUSH_TICKS = integer("RushTicks", 12);
    public static final GameRules.Key<GameRules.IntegerValue> RUSH_WINDOW = integer("RushWindow", 25);


    // Ghost Face-inspired, original stealth-and-mark power.
    public static final GameRules.Key<GameRules.IntegerValue> GHOST_COOLDOWN = integer("GhostCooldown",30);
    public static final GameRules.Key<GameRules.IntegerValue> GHOST_SHROUD_SECONDS = integer("GhostShroudSeconds",14);
    public static final GameRules.Key<GameRules.IntegerValue> GHOST_MARK_TICKS = integer("GhostMarkTicks",44);
    public static final GameRules.Key<GameRules.IntegerValue> GHOST_MARK_SECONDS = integer("GhostMarkSeconds",12);
    public static final GameRules.Key<GameRules.IntegerValue> GHOST_RANGE = integer("GhostRange",22);
    public static final GameRules.Key<GameRules.IntegerValue> GHOST_DAMAGE_BONUS = integer("GhostDamageBonus",35);
    // Artist-inspired homing ink-crows.
    public static final GameRules.Key<GameRules.IntegerValue> CROW_COOLDOWN = integer("CrowCooldown",16);
    public static final GameRules.Key<GameRules.IntegerValue> CROW_COUNT = integer("CrowCount",3);
    public static final GameRules.Key<GameRules.IntegerValue> CROW_RANGE = integer("CrowRange",28);
    public static final GameRules.Key<GameRules.IntegerValue> CROW_REVEAL_SECONDS = integer("CrowRevealSeconds",9);
    public static final GameRules.Key<GameRules.IntegerValue> CROW_SPEED = integer("CrowSpeed",105);

    // Living Harpoon is strictly player-only, independently opt-in.
    public static final GameRules.Key<GameRules.BooleanValue> HARPOON_PVP=bool("HarpoonPvp",false);
    public static final GameRules.Key<GameRules.IntegerValue> HARPOON_RANGE=integer("HarpoonRange",20);
    public static final GameRules.Key<GameRules.IntegerValue> HARPOON_COOLDOWN=integer("HarpoonCooldown",16);
    public static final GameRules.Key<GameRules.IntegerValue> HARPOON_SECONDS=integer("HarpoonSeconds",3);
    public static final GameRules.Key<GameRules.IntegerValue> HARPOON_PULL=integer("HarpoonPull",24);
    // Slasher: invisibility, private footprint tracking and optional second-use lunge.
    public static final GameRules.Key<GameRules.IntegerValue> SLASHER_COOLDOWN=integer("SlasherCooldown",26);
    public static final GameRules.Key<GameRules.IntegerValue> SLASHER_SECONDS=integer("SlasherSeconds",12);
    public static final GameRules.Key<GameRules.IntegerValue> SLASHER_TRACK_RANGE=integer("SlasherTrackRange",28);
    public static final GameRules.Key<GameRules.IntegerValue> SLASHER_LUNGE_SPEED=integer("SlasherLungeSpeed",140);
    public static final GameRules.Key<GameRules.BooleanValue> SLASHER_TRACK_PLAYERS=bool("SlasherTrackPlayers",false);
    // Player-owned existing iron-door network: only loaded same-dimension destinations.
    public static final GameRules.Key<GameRules.BooleanValue> SECURITY_ENABLED=bool("SecurityEnabled",true);
    public static final GameRules.Key<GameRules.BooleanValue> SECURITY_TRAVEL=bool("SecurityTravel",false);
    public static final GameRules.Key<GameRules.IntegerValue> SECURITY_MAX_DOORS=integer("SecurityMaxDoors",8);
    public static final GameRules.Key<GameRules.IntegerValue> SECURITY_SCAN_RADIUS=integer("SecurityScanRadius",8);
    // Non-destructive co-op investigations; world-saved gamerules.
    public static final GameRules.Key<GameRules.BooleanValue> INVESTIGATIONS_ENABLED=bool("InvestigationsEnabled",true);
    public static final GameRules.Key<GameRules.BooleanValue> SEANCES_ENABLED=bool("SeancesEnabled",true);
    public static final GameRules.Key<GameRules.BooleanValue> HAUNTED_PAINTINGS=bool("HauntedPaintings",true);
    public static final GameRules.Key<GameRules.BooleanValue> FOOTPRINTS_ENABLED=bool("UnexplainedFootprints",true);
    public static final GameRules.Key<GameRules.IntegerValue> SEANCE_SECONDS=integer("SeanceSeconds",14);
    public static final GameRules.Key<GameRules.IntegerValue> SEANCE_MIN_PLAYERS=integer("SeanceMinPlayers",2);
    public static final GameRules.Key<GameRules.IntegerValue> INVESTIGATION_RANGE=integer("InvestigationRange",24);
    public static final GameRules.Key<GameRules.IntegerValue> FOOTPRINT_INTERVAL=integer("FootprintInterval",240);
    public static final GameRules.Key<GameRules.IntegerValue> FOOTPRINT_CHANCE=integer("FootprintChance",30);

    // Equipment-driven supernatural encounters, no automatic damage or terrain damage.
    public static final GameRules.Key<GameRules.BooleanValue> MANIFESTATIONS_ENABLED=bool("ManifestationsEnabled",true);
    public static final GameRules.Key<GameRules.IntegerValue> MANIFEST_SECONDS=integer("ManifestSeconds",14);
    public static final GameRules.Key<GameRules.IntegerValue> PAINTING_MANIFEST_CHANCE=integer("PaintingManifestChance",35);
    public static final GameRules.Key<GameRules.BooleanValue> MOTION_SENSORS_ENABLED=bool("MotionSensorsEnabled",true);
    public static final GameRules.Key<GameRules.BooleanValue> MOTION_SENSOR_PLAYERS=bool("MotionSensorPlayers",false);
    public static final GameRules.Key<GameRules.IntegerValue> MOTION_SENSOR_RANGE=integer("MotionSensorRange",6);
    public static final GameRules.Key<GameRules.BooleanValue> CURSED_RECORDINGS=bool("CursedRecordings",true);
    public static final GameRules.Key<GameRules.BooleanValue> GHOST_HUNTS_ENABLED=bool("GhostHuntsEnabled",true);
    public static final GameRules.Key<GameRules.IntegerValue> HUNT_MIN_PLAYERS=integer("HuntMinPlayers",2);
    public static final GameRules.Key<GameRules.IntegerValue> HUNT_SECONDS=integer("HuntSeconds",70);
    public static final GameRules.Key<GameRules.IntegerValue> HUNT_ACTIVITY_TICKS=integer("HuntActivityTicks",100);
    public static final GameRules.Key<GameRules.IntegerValue> HUNT_MANIFEST_CHANCE=integer("HuntManifestChance",55);
}
