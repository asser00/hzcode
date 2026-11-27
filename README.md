# HZCODE - Horde Soldiers (Forge 1.20.1)

Minecraft 1.20.1 / Forge 47.x / Java 17. Primary integration: TACZ 1.2.0 (hard dependency).
Mod id: `hzcode` (installed jar file name: `HZCODE.jar`). This is a **new working copy with a different project name**; the
original `C:\c\Users\NOXROV\mcmod-dev\horde-soldiers` (and `E:\Games\horde_soldiers-1.0.0.jar`)
are left untouched.

## Core principle

**ONE BRAIN. MULTIPLE BODIES. SHARED STRATEGY. INDEPENDENT EXECUTION.**

Each horde has exactly one strategic controller (`HordeController`). The leader entity performs
the expensive decisions (target acquisition, destination, primary path, orders, formation).
Followers keep their own body (movement, collision, health, equipment, combat execution) and only
read published shared state + formation slots, with throttled local recovery pathfinding.

## Architecture

- `horde/HordeController` — the single strategic brain per horde. Scheduled intervals: strategic
  decisions, leader validity/election, formation, path. Holds no live entity references across
  saves (UUIDs only).
- `horde/HordeData` — serializable shared state (ids, leader, target, destination, order,
  formation, loot/breach targets). Persisted via `horde/HordeSavedData` (per-dimension SavedData);
  membership also persists on each entity's own NBT (`horde/HordeLink`).
- `horde/HordeRegistry` — per-`ServerLevel` registry. Guarantees one controller per horde id,
  entity→horde index, per-dimension breach counter and per-tick path budget. Never force-loads
  chunks; unloaded members are dormant and re-attach on `EntityJoinLevelEvent`.
- `horde/HordeMember` + `horde/FormationSlot` + `horde/FormationManager` + `horde/FormationMath` —
  stable, deterministic (UUID-sorted, index-based) formation slots: FOLLOW_TRAIL, PROTECT_LEADER,
  COMBAT_SPREAD, HOLD_POSITION. No random jitter.
- `horde/HordeNavigation` — hybrid navigation: leader owns the strategic path; followers do local
  slot correction every `followerCorrectionInterval` ticks; full path recalc only on stuck/
  separated, per-member cooldown + global per-tick budget (`maxPathRecalcsPerTick`).
- `horde/LeaderElection` — deterministic election (leader flag, health, age, UUID tiebreak).
  Exactly one leader; leader death → single successor, shared state survives (order persists).
- `horde/HordeSpawner` — spawns a horde as a real group (leader + followers registered into one
  controller, stable slots, faction init). Soldiers get real TACZ guns/ammo here; zombie hordes
  get a designated breacher. No orphans on partial failure.
- `horde/HordeManager` — timed wave event (`/horde start|stop|status|wave`).
- `faction/` — centralized relations (`FactionRelations` pure matrix + `FactionManager` MC-aware).
  ALLIED/NEUTRAL/HOSTILE, config-overridable. All targeting goes through it.
- `entities/` — ZombieEntity, SoldierEntity, ScavengerEntity, TerroristEntity
  (`IHordeEntity`, `AbstractHordeMob` with persisted 27-slot inventory capability).
- `entities/ai/goal/` — FactionTargetGoal (followers defer to shared target), HordeFollowerGoal,
  ZombieBreachGoal (mobGriefing + whitelist/blacklist + tool rules + per-dimension breach cap via
  `ZombieBreachRules`), SoldierTaczCombatGoal (real TACZ aim/shoot/reload), ScavengerDefenseGoal,
  LootContainerGoal + `LootTransfer` (simulate-first transfer; items are never lost, remainders
  are preserved and put back).
- `tacz/TacZAdapter` — real TACZ API bridge (`IGunOperator`/`IGun`/`IAmmo`). `equipSoldier` is
  called by the spawner and on entity load; TACZ is declared mandatory in `mods.toml`.
- `commands/ModCommands` — `/horde start|stop|status|list|wave|spawn <type> [count]|order <order> [radius]|dismiss [radius]`.
  Orders (FOLLOW, HOLD_POSITION, DEFEND_POSITION, PROTECT_PLAYER, ATTACK_TARGET, RETREAT,
  CEASE_FIRE, RECALL, MOVE_TO) are applied to controllers only. `/horde list` prints per-horde
  detail (type, member count, state, order, formation, leader position) in the current dimension.

## Fixed vs. the audited 1.0.0 jar

- TACZ provisioning wired into the spawn/load lifecycle; `tacz` declared as mandatory dependency.
- Textures exist for all four entities (`zombie/scavenger/terrorist.png` generated,
  `soldier_biker/medic.png` carried over).
- Breaching respects `mobGriefing`, whitelist/blacklist, tool requirement, per-dimension
  concurrent breach limit (was a cross-dimension static counter).
- Scavenger loot scan is leader-driven and throttled (was ~9,801 positions per scavenger);
  container transfer is simulate-first and lossless (was extract-then-maybe-drop). A real
  duplication bug in the transfer probe (the "simulated" insert into the destination was not
  actually simulated, so items were inserted twice) was found by the automated tests and fixed.
- Stale `ally`/`terrorist` entity ids replaced by `zombie/soldier/scavenger/terrorist` in lang,
  tags and registrations.
- Breacher role is assigned by the spawner/controller (`setBreacher` has real callers).

## Configuration

All tuning lives in the COMMON config (`config/horde_soldiers-common.toml`). Notable groups:

- `general` — `enableHorde`, `waveInterval`, `spawnCap`, `waveSizeGrowthPerWave`, `waveSizeGrowthCap`
  (wave size = `zombieHordeSize + min(waveSizeGrowthCap, waveNumber/2 * waveSizeGrowthPerWave)`).
- `horde` — horde sizes per type, engine intervals (`strategicInterval`, `targetRefreshInterval`,
  `formationInterval`, `followerCorrectionInterval`, `leaderCheckInterval`, `pathRecalcCooldown`,
  `maxPathRecalcsPerTick`, `recoveryDistance`, `formationSpacing`, `targetAcquisitionRange`).
- `zombieStats` — `zombieHealth`, `zombieDamage`, `zombiesBurnInSunlight` (default false: horde
  zombies do not burn in daylight; set true for vanilla behavior).
- `zombie` — breaching: `breachEnabled`, `maxConcurrentBreaches`, `respectMobGriefing`,
  `breachRequiresToolForStone`, `breachableBlocks`, `protectedBlocks`.
- `soldier` / `scavenger` / `terrorist` — per-type health/damage, TACZ gun+ammo provisioning
  (`soldierGun`, `soldierAmmo`, `soldierAmmoStacks`, `soldierLoadedAmmo`, `terroristsUseGuns`),
  scavenger loot scan (`scavengerLootRadius`, `scavengerLootInterval`, `scavengerRetreatThreshold`).
- `factions` — relation overrides (`relationPlayerScavenger`, `relationPlayerTerrorist`,
  `relationSoldierScavenger`, `relationSoldierTerrorist`, `relationScavengerTerrorist`),
  each ALLIED / NEUTRAL / HOSTILE, applied symmetrically.

## Build (offline-capable)

```
gradle --offline build        # compiles, jars, runs logic tests
gradle --offline logicTest    # logic unit tests (formation, factions, orders, election, NBT, loot transfer)
powershell -File build-offline.ps1   # fully offline javac build + jar + tests (no network needed)
```

Local build artifacts (`build\cp\*.jar`, `libs\tacz-1.20.1-1.2.0.jar`) are NOT tracked in git.
If they are missing (fresh clone / wiped build dir), restore them with:

```
powershell -File tools\restore-build-artifacts.ps1   # copies classpath jars from build_classpath.txt + the TACZ jar
```

Production jar (install THIS into the instance mods folder as `HZCODE.jar`):
`build/libs/hzcode-2.0.1.jar` (SRG-reobfuscated by ForgeGradle; BOM-free mods.toml).
Dev/test-only jar: `build/libs/horde_soldiers-2.0.1-dev.jar` (mojmap - do NOT install into the game).
Tests: `src/test/.../LogicTests.java`
(168 checks as of 2026-11-27; the MC-dependent loot-transfer tests bootstrap the vanilla registries headlessly
via reflection on `Bootstrap.isBootstrapped`, so they run without Forge/FML).

## Verification status

See `VERIFICATION.md` for the per-item status table (IMPLEMENTED / COMPILED / AUTOMATED TESTED /
IN-GAME VERIFIED / BENCHMARKED). In-game verification requires launching a Forge client/server;
this environment has no network, so runtime verification is limited to what can run offline.


## Status (2026-11-27) & docs
- Mod id `hzcode` (jar `HZCODE.jar`), package `com.nox.hzcode`. HEAD `2ee3741`, tests 168/168, build green.
- Features: horde engine, factions (incl. MERCENARY), medic seek-and-heal + self-defense melee, TACZ soldier/terrorist/mercenary
  loadouts (verified ids), scavenger melee + work system, barter trading, taming, weapon swap, male/female variants with
  owner skin pools (lazy-loaded, reload-safe), mercenary contract recruitment, war horn + support calls, horde_camp structure.
- NOT yet implemented: squad commands (FOLLOW/STAY/DEFEND UI) - Phase 2, see `PLAN.md`; voice/sound system; see `PLAN.md` backlog.
- Docs: `PROJECT.md` (identity/boundaries/deps/gotchas), `PLAN.md` (active plan + backlog), `RECAP.md` (session history),
  `.handoff.md` (resume guide), `docs/` (FEATURES-*, SKIN-ASSETS.md, TEST_ENV.md).

## GitHub
Remote: `https://github.com/asser00/hzcode.git` - branch `main` is in sync; plain `git push` works (upstream set).
Do not commit build artifacts or secrets (`.gitignore` covers `build/`, `libs/` jars, logs).
