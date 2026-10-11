# Death Whisper — Minecraft 1.21.1 NeoForge

**Experimental v0.3.0.** A multiplayer psychological-horror stalker that avoids combat. Instead of a wolf, it appears as an **inverted-colour copy of the player it is hunting**, with glowing red eyes, an original whistle, head/shoulder peeking and AI that runs to physical hiding spots.

## Installation
Download the `death-whisper-jar` artifact from the newest successful build in [GitHub Actions](https://github.com/DarkWriting/DeathWhisper/actions). Extract the ZIP; install the JAR on **both** NeoForge 1.21.1 server and clients. Back up your world and test separately before production.

Only one Death Whisper JAR should be present in each `mods` folder. Restart the game/server after replacing it.

## Quick testing
Use an OP account in Survival on reasonably open terrain:

```mcfunction
/gamerule deathWhisperDuration 90
/gamerule deathWhisperReposition true
/gamerule deathWhisperFakeCharges false
/deathwhisper force
/deathwhisper status
/deathwhisper debug
/deathwhisper memory
/deathwhisper scenario peripheral
/deathwhisper scenario forest
/deathwhisper scenario doorway
/deathwhisper scenario silent
```

`scenario` succeeds only when a valid loaded spawn location is available and the active encounter cap allows it. `scenarioPlayer <player> <kind>` targets an individual. Other commands: `/deathwhisper clear [player]`, `/deathwhisper threat <player> <0..100>`.

## Inverted skins
The server synchronizes the target player's UUID with the stalker. Each client's renderer uses the public skin URL already supplied by Minecraft's player list, downloads only from `https://textures.minecraft.net/texture/...` with strict timeouts and size limits, inverts RGB while preserving alpha, and caches the resulting texture. Supports normal and slim player models. A generated fallback skin appears until the client has obtained the target's public skin. Offline, custom, or unavailable skins may not resolve. Real player names or UUIDs do **not** leave the game for any custom mod service.

## Gamerules
All server settings use `/gamerule deathWhisper...` and are saved with the world. Examples:

```mcfunction
/gamerule deathWhisperEnabled true
/gamerule deathWhisperCooldown 900
/gamerule deathWhisperChance 30
/gamerule deathWhisperMinDistance 20
/gamerule deathWhisperMaxDistance 48
/gamerule deathWhisperDuration 45
/gamerule deathWhisperMaxActive 2
/gamerule deathWhisperPathBudget 10
/gamerule deathWhisperPrivateFocus true
/gamerule deathWhisperSilentEncounters true
/gamerule deathWhisperPeeking true
/gamerule deathWhisperReposition true
/gamerule deathWhisperFakeCharges true
/gamerule deathWhisperChargeChance 10
```

Other gamerules include `Underground`, `NoDamage`, `Stealth`, `Prediction`, `Memory`, `BehaviorAdapt`, `Isolation`, `GroupAvoidance`, `Weather`, `BiomeBehavior`, `StructureBehavior`, `ElevatedWatch`, `FalseSightings`, `FakeDepartures`, `DirectionalAudio`, `Footsteps`, `Laughter`, `Ambience`, `EyeGlow`, `Debug`, `Relocations`, `PeekSeconds`, `StareSeconds`, `SilentChance`, `SightingChance`, `FootstepChance`, `WhistleVolume`, `ThreatGain`, `ThreatDecay`, `IsolationBonus`, `GroupRadius`, `MinPathDistance`, `MaxPathDistance`.

**Important:** Some settings expose future/partial behaviour and are not yet fully wired, including exact ambient sound attenuation and client eye glow. The mod is permanently non-damaging even if `deathWhisperNoDamage` is set false.

## Multiplayer and performance
The encounter director uses bounded pathfinding, no chunk loading, per-player cooldowns, a default of two active stalkers and a server-wide scheduler. Threat and basic encounter history are stored in player persistent data; the encounter cooldown scheduler resets after server restarts. Private focus currently hides the stalker **visually** from non-target clients, but server-spawned audio may still be audible nearby.

## Known limitations
- This code **builds**, but is **not yet in-game verified** for the new inversion, peeking animations, server restart persistence or all encounter profiles.
- Normal/slim skin shape and skin URL fetching depend on each client's usual Minecraft skin access.
- The 50-idea overhaul is tracked in [FEATURES.md](FEATURES.md); some complex elements remain partial or planned rather than being falsely marked complete.
- Atmosphere is original synthesized sounds, not the copyrighted film whistle.
- It does not modify terrain or attack players.

## Developer build
Java 21, NeoForge 21.1.250, Gradle ModDevGradle. GitHub Actions generates original audio/PNG assets, builds, and publishes a JAR artifact. Source is under `src/main/java/com/example/deathwhisper`.
