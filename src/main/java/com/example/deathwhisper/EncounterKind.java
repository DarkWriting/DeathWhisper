package com.example.deathwhisper;

/** Distinct encounter scripts; more than one may be combined by the director. */
public enum EncounterKind {
    WHISTLE, SILENT, SHADOW, WATCH, PERIPHERAL, HILL, CAVE, DOORWAY, MINING,
    CAMPFIRE, HOME, GROUP, FALSE_SIGHTING, FAKE_DEPARTURE, FOOTSTEPS, LAUGHTER,
    RAIN, FOREST, STARING, CHASE;
    public static EncounterKind parse(String value) {
        try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
