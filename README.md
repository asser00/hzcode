# Horde Soldiers v2 (`horde-soldiers-v2`)

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

Output jar: `build/libs/horde_soldiers-2.0.1.jar`. Tests: `src/test/.../LogicTests.java`
(111 checks; the MC-dependent loot-transfer tests bootstrap the vanilla registries headlessly
via reflection on `Bootstrap.isBootstrapped`, so they run without Forge/FML).

## Verification status

See `VERIFICATION.md` for the per-item status table (IMPLEMENTED / COMPILED / AUTOMATED TESTED /
IN-GAME VERIFIED / BENCHMARKED). In-game verification requires launching a Forge client/server;
this environment has no network, so runtime verification is limited to what can run offline.


## GitHub
This project is mirrored as a git repo. Remote (set it to your repo before pushing):
`https://github.com/NOXROV/hzcode.git` - to publish: create the repo on GitHub (or `gh repo create NOXROV/hzcode --public --source .`), then `git push -u origin master`.
