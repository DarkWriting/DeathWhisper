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

## Supernatural powers framework — experimental v0.5.0

All active abilities implement `SupernaturalAbility` and are dispatched via `PowerFramework`.
The common framework handles server-only activation, cooldowns, PvP-safe targeting,
periodic ticks and a pre-damage hook. New powers register an ability ID with the framework,
then use a `PowerItem` which delegates its right-click action to that ability.
The existing Puzzle Box and Blight Serum now use the same framework.

**Creative-mode test items or OP /give commands:**

```mcfunction
/give @s deathwhisper:chain_puzzle_box
/give @s deathwhisper:blight_serum
/give @s deathwhisper:shrouded_mask
/give @s deathwhisper:ink_feather
/deathwhisper powers
```

- **Shrouded Mask (Ghost Face-inspired):** Right-click to enter temporary invisibility.
  While holding the mask, stare directly at one eligible mob for long enough to mark it.
  A mark highlights it and grants **only the mask user** bonus damage when attacking
  that marked target before the mark expires. Armor and held items may remain visible.
- **Ink-Stained Feather (Artist-inspired):** Right-click to launch three supernatural
  ink wisps. Their smoke-wing silhouettes move forward, bend toward nearby living
  enemies, and reveal any targets struck with Glowing. They are visual particles and
  server-side flight records, not persistent new mobs.
- **Puzzle Box:** Right-click toward a nearby living target to restrain it with chains.
- **Blight Serum:** Right-click to rush; use again within the charging window for
  follow-up bursts; wall collisions cause a rebound interruption.

**Gamerules:**

```mcfunction
/gamerule deathWhisperPowersEnabled true
/gamerule deathWhisperPowersPvp false
/gamerule deathWhisperGhostCooldown 30
/gamerule deathWhisperGhostShroudSeconds 14
/gamerule deathWhisperGhostMarkTicks 44
/gamerule deathWhisperGhostMarkSeconds 12
/gamerule deathWhisperGhostRange 22
/gamerule deathWhisperGhostDamageBonus 35
/gamerule deathWhisperCrowCooldown 16
/gamerule deathWhisperCrowCount 3
/gamerule deathWhisperCrowRange 28
/gamerule deathWhisperCrowRevealSeconds 9
/gamerule deathWhisperCrowSpeed 105
```

PvP targeting for supernatural abilities is **disabled by default**. The new items
are intentionally not given survival crafting recipes yet: balance/progression will be
set after the abilities have been tested. No third-party model or sound assets are used.

## Experimental v0.6: Living Harpoon, Slasher's Relic, Security Door Controller

### Test items
/give @s deathwhisper:living_harpoon
/give @s deathwhisper:slasher_relic
/give @s deathwhisper:security_door_controller

### Living Harpoon
Right-click while aiming directly at another player. The tether pulls them toward you for a limited time, with no direct damage. **It never targets mobs or Pokemon.** Enable the opt-in gamerule first: /gamerule deathWhisperHarpoonPvp true. This is independent of the general PvP setting. Other gamerules: HarpoonRange 20, HarpoonCooldown 16 seconds, HarpoonSeconds 3, HarpoonPull 24. All begin with the deathWhisper prefix.

### Slasher's Relic
Right-click for temporary invisibility and speed. Tracks nearby living mobs as private spectral footprints; tracking other players is OFF unless /gamerule deathWhisperSlasherTrackPlayers true. Right-click again after 0.6 seconds to lunge, breaking invisibility; this second activation shares the first cooldown. Other gamerules: SlasherCooldown 26 seconds, SlasherSeconds 12, SlasherTrackRange 28, SlasherLungeSpeed 140. No contact damage.

### Security Door Controller
Sneak + right-click TWO existing iron doors to register them for your player. Right-click air to open a clickable chat-based access panel with SCAN and TRAVEL links. Scans show a status snapshot (open/closed, players and monsters) of a loaded iron door, *not a live video feed*. Travel requires the source within five blocks of one of your registered doors and a safe loaded destination in the SAME dimension. No chunks or doors are created by this item. Commands while holding the item: /deathwhispersecurity list, /deathwhispersecurity scan 1, /deathwhispersecurity travel 2. Gamerules: deathWhisperSecurityEnabled true, deathWhisperSecurityTravel false (opt in to teleportation after testing claim protections), deathWhisperSecurityMaxDoors 8, deathWhisperSecurityScanRadius 8.

**Caution:** The security system does NOT currently integrate Open Parties and Claims protection checks; movement between registered doors may bypass claim access restrictions. Test with trusted players, and do not enable on a public production server until claim integration and multiplayer tests have been completed.

These features have GitHub compilation tests, not automated in-world integration tests.

## Paranormal investigation v0.7 (experimental; NeoForge 1.21.1)

**This expansion is separate from combat powers.** It features original ghost profiles inspired by cooperative ghost-hunting games without copying their copyrighted names, models or audio. Everything operates on loaded chunks and is designed not to destroy blocks, paintings or bases.

### Get the five tools

```mcfunction
/give @s deathwhisper:seance_chalk
/give @s deathwhisper:field_journal
/give @s deathwhisper:emf_reader
/give @s deathwhisper:spirit_radio
/give @s deathwhisper:ghost_thermometer
/give @s deathwhisper:uv_lamp
```

**Cooperative séance:** Two players each hold ritual chalk and sneak/right-click the SAME floor block (or within four blocks). Remain within eight blocks for the channeling time. The circle emits supernatural particles and, if successful, both participants receive a clue in their own persistent journals. No chalk blocks are placed. Rituals expire if participants leave or disconnect.

**Haunted paintings:** Interact with any existing vanilla painting to potentially awaken a haunting. Sneak-right-click or hold the journal while right-clicking the painting for reliable activation. The painting produces ominous ink, whispers and spectral particles; it is never broken or replaced. This version does not yet redraw or switch the original artwork.

**Spirit evidence:** Ghosts near painting or ritual sites have one consistent pair of evidence types: EMF readings, spirit voices, freezing readings, or spectral ink. Use the four instruments (EMF, radio, thermometer and UV lamp) within the investigation range of an active haunting. Your journal retains up to 24 cases across deaths, including discoveries made with other players. The active haunting itself is temporary; it can be recreated by revisiting the same painting/ritual anchor. World-seed-based ghost profiles stay stable at the same coordinates.

**Unexplained footprints:** Rare, private spectral trails appear behind players (chance and interval below), emit unsettling footsteps, and vanish after 10 seconds. They do not place blocks or permanent entities. If a trail occurs beside a ghost site associated with spectral ink, it adds that clue to the player's journal.

**Guessing the ghost:**

```mcfunction
/deathwhisperinvestigate journal
/deathwhisperinvestigate types
/deathwhisperinvestigate guess wailer
```

Each of the six original ghost profiles has a unique two-evidence combination. A correct identification, after collecting both clues, records the case as solved and grants a modest amount of XP.

**World gamerules:**

```mcfunction
/gamerule deathWhisperInvestigationsEnabled true
/gamerule deathWhisperSeancesEnabled true
/gamerule deathWhisperHauntedPaintings true
/gamerule deathWhisperUnexplainedFootprints true
/gamerule deathWhisperSeanceSeconds 14
/gamerule deathWhisperInvestigationRange 24
/gamerule deathWhisperFootprintInterval 240
/gamerule deathWhisperFootprintChance 30
```

For accelerated testing, set FootprintInterval 30 and FootprintChance 100, then stand in survival mode.

**Important limitations:** No full ghost NPC/3D apparition, live voice recognition, UV scanner, client evidence GUI, or actual painting-art transformations in this first version. Haunted paintings show effects rather than changing pictures. Investigation events do not intentionally modify blocks or teleport anyone, making them safer to try on servers using claims mods. Compile success is not proof of correct in-game behavior.
