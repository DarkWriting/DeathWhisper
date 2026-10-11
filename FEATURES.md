# Death Whisper — 50-feature tracker

**Status legend:** implemented = a specific code path exists, not necessarily in-game proven; partial = limited implementation; not yet implemented / superseded = intentional remaining scope. **No item is considered gameplay-verified until tested in Minecraft.**

1. **Predictive stalking** — partial: movement-vector intercept
2. **Shadow following** — partial: route behind the player's view
3. **Dynamic hiding spots** — implemented: occluded reachable cover
4. **Adaptive stalking distance** — implemented: based on chase history
5. **Alternative route pursuit** — implemented: pathfinding; terrain-dependent
6. **Head-only peeking** — partial: head and shoulder animation
7. **Multiple peek styles** — implemented: three head-lean magnitudes
8. **Reactive hiding** — implemented: stare/witness triggers cover
9. **Tree-to-tree movement** — partial: general cover; not trunk-aware path graph
10. **Elevated observation** — partial: elevated candidate placement
11. **False sightings** — implemented: shortened silhouette encounter
12. **Peripheral appearances** — implemented: view-relative placement
13. **Delayed reaction** — implemented: stare timing
14. **Fake departures** — partial: longer encounters and cover retreats
15. **Unpredictable silence** — implemented: silent encounters
16. **Directional whistling** — implemented: positional server sound
17. **Different whistle patterns** — implemented: three synthesized cues
18. **Footstep sounds** — implemented: positional footstep audio
19. **Environmental silence** — not yet implemented: custom ambient sounds only
20. **Distant laughter** — implemented: original synthesized cue
21. **Long-term player memory** — implemented: NBT-backed stats
22. **Behaviour adaptation** — implemented: chase-based distance/charge changes
23. **Death tracking** — implemented: threat gain on death
24. **Personalised stalking** — partial: target-specific memory and cooldown
25. **Curiosity tracking** — implemented: count prolonged stares
26. **Private encounters** — partial: per-client visual suppression
27. **Group avoidance** — implemented: weighted spawn chance
28. **Shared sightings** — implemented: group encounter kind
29. **Player isolation** — implemented: isolation chance bonus
30. **Witness reactions** — implemented: non-target sightings trigger hiding
31. **Biome-specific behaviour** — partial: forest/cave script selection
32. **Weather adaptation** — partial: thunder encounter kind
33. **Light avoidance** — partial: shadow scenes prefer lower brightness
34. **Structure stalking** — implemented: doors/beds/campfires as anchors
35. **Cave encounters** — implemented: roof/low-height scripts
36. **Campfire observation** — implemented: anchor seeking
37. **Doorway encounters** — implemented: anchor seeking
38. **Mining encounters** — implemented: low-height script
39. **Following home** — partial: detected beds, not long-form trailing
40. **Long-distance stare** — implemented: hill encounter scripts
41. **Custom character model** — replaced at user's request: inverted target-player model
42. **Head tracking** — implemented: vanilla look control
43. **Custom movement animations** — partial: sprint plus head-and-shoulder peeking
44. **Glowing eyes** — implemented: emissive texture; gamerule not yet hooked
45. **Cloak movement** — superseded: player skin and outfit instead
46. **Encounter director** — implemented: server-wide capped scheduler
47. **Advanced gamerules** — partial: many wired, some placeholders
48. **Debug commands** — implemented: debug/status/memory/scenario
49. **Performance optimisation** — implemented: bounded route search and encounter caps
50. **Encounter recording** — implemented: profile counters, no export

## Testing sequence

1. Verify player skin colour inversion and transparency in multiplayer.
2. Test cover/peek poses by a real tree and wall.
3. Test private scenario with two clients (audio may remain shared).
4. Test each scripted scenario and long-duration movement.
5. Observe CPU/tick time with 20+ connected players before enabling frequent encounters.
6. Check persistent memory after restart and player death.
