# Verification Report — horde-soldiers-v2 (Horde Soldiers 2.0.0)

Target: Minecraft 1.20.1, Forge 47.4.28, Java 17, TACZ 1.2.0 (hard dependency).
Project: `C:\Users\NOXROV\source\repos\horde-soldiers-v2` (new name; original
`C:\c\...\horde-soldiers` and `E:\Games\horde_soldiers-1.0.0.jar` untouched).

## Status table

| Item | Status |
|---|---|
| Horde engine (controller, data, member, registry, saved data, spawner, manager) | IMPLEMENTED |
| Hybrid navigation (leader path + follower slot correction + budgeted recovery) | IMPLEMENTED |
| Formation system (4 types, deterministic slot math) | IMPLEMENTED + AUTOMATED TESTED |
| Leader election (deterministic, single successor) | IMPLEMENTED + AUTOMATED TESTED |
| Faction matrix (ALLIED/NEUTRAL/HOSTILE, config overrides) | IMPLEMENTED + AUTOMATED TESTED |
| Orders (9) applied to controllers via `/horde order` | IMPLEMENTED |
| TACZ integration (real `IGunOperator`/`IGun`/`IAmmo`; `equipSoldier` called at spawn + on load; `tacz` mandatory in mods.toml) | IMPLEMENTED (compile-verified against tacz-1.20.1-1.2.0.jar) |
| Zombie breaching (mobGriefing, whitelist/blacklist, tool rule, per-dimension breach cap, breacher designation) | IMPLEMENTED |
| Scavenger leader-driven throttled loot discovery + lossless transfer (`LootTransfer` simulate-first) | IMPLEMENTED |
| Persistence (per-dimension SavedData + per-entity NBT, dormant members, no chunk force-loading) | IMPLEMENTED |
| Textures for all 4 entities, lang, tags, spawn eggs, mods.toml, pack.mcmeta | IMPLEMENTED (present in jar) |
| COMPILED | YES — 51 classes, 0 errors (`build-offline.ps1`, JDK 17, `--release 17`) |
| Release JAR | `build/libs/horde_soldiers-2.0.1.jar` |
| AUTOMATED TESTED | 111/111 logic tests pass (`LogicTests`: formation math + slot assignment, faction matrix incl. full symmetry, order/type parsing, leader election, NBT round-trips of `HordeData`/`HordeMember`/`FormationSlot`/`HordeLink`, loot transfer). The loot-transfer runtime tests RUN offline: the harness bootstraps the vanilla registries headlessly by flipping `Bootstrap.isBootstrapped` via reflection (full `Bootstrap.bootStrap()` needs Forge FML and fails outside it). These tests caught and now guard a real item-duplication bug in `LootTransfer` (the destination probe insert was not simulated) — fixed, with conservation asserted |
| `/horde list` per-horde detail command | IMPLEMENTED |
| Config: wave growth (`waveSizeGrowthPerWave`/`waveSizeGrowthCap`), zombie stats (`zombieHealth`/`zombieDamage`), `zombiesBurnInSunlight` | IMPLEMENTED |
| IN-GAME VERIFIED (boot) | YES - 2026-11-24 21:02 with HZCODE.jar: mod instance created, config loaded, 0 FATAL, no crash reports/registry errors, title screen reached. World-join structure crash fixed (piece mirror/rotation NPE + spawn_overrides). In-game feature testing by user in progress |
| BENCHMARKED | NO — requires a running server; benchmark procedure documented below |

## Notes on environment blockers

- Build: network IS available and the Gradle 8.8 + ForgeGradle 6.0.54 caches are complete, so `gradle --offline build` works and produces the correct SRG-reobfuscated production jar (raw `javac` output is mojmap-only and crashes in-game with `NoSuchMethodError` — see `docs\CRASH-FIX-2026-11-20.md`). `build-offline.ps1` automates dev-jar + tests + gradle production jar + verification; `tools\run-tests.ps1` smoke-tests the production jar (BOM, SRG `m_20704_` marker, no `FMLModType` manifest attr) and runs LogicTests on the dev jar. The old note below about no-network javac builds is superseded:
  against the cached `forge-1.20.1-47.4.28_mapped_official_1.20.1.jar` + cached libraries +
  `libs/tacz-1.20.1-1.2.0.jar` (see `build-offline.ps1`). `gradle --offline build` remains
  available for environments with network.
- The sandbox blocks some zipfs/directory operations, which is why the build script uses jars
  on the classpath; a cosmetic "Cannot close compiler resources" message appears after classes
  are written (exit code may be non-zero despite 0 compile errors — check for `error:` lines).
- Headless test bootstrap: `LogicTests` cannot call `Bootstrap.bootStrap()` (Forge `NetworkHooks`
  init fails outside FML), so it sets `Bootstrap.isBootstrapped` via reflection after
  `SharedConstants.tryDetectVersion()` — enough for the vanilla registries and `ItemStack` to work.

## Benchmark procedure (to run in-game)

1. Install `build/libs/horde_soldiers-2.0.0.jar` + TACZ into a Forge 1.20.1 instance.
2. `/horde spawn soldier|scavenger|zombie N` for small (5/5/6), medium (15/15/20), large (30/30/40).
3. Measure with Spark or `/forge tick` + MSPT: average/peak tick time, plus counter reads from
   `HordeRegistry` (controllers, path budget use, active breaches, loot scans).
4. Compare against the old `horde_soldiers-1.0.0.jar` under the same world/seed/settings.
5. Expected (not yet measured): one strategic scan + one path per horde per interval instead of
   per-entity scans; follower path recalcs capped by `maxPathRecalcsPerTick`.
