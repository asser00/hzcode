# RECAP — Horde Soldiers v2 (what was done)

## Starting point
- Original project found at `C:\c\Users\NOXROV\mcmod-dev\horde-soldiers\mdk` (only `libs\tacz-1.20.1-1.2.0.jar` + 2 placeholder PNGs; no sources/gradle) and the built `E:\Games\horde_soldiers-1.0.0.jar` (audited via javap).
- Per your instruction, work happened in a NEW project with a different name: `horde-soldiers-v2`. The original project and the 1.0.0 jar were never modified.

## What was built (43 Java sources, Forge 1.20.1 / 47.4.28, Java 17, TACZ 1.2.0 hard dep)
- **Horde engine** (`horde/`): `HordeController` (one strategic brain per horde: throttled target acquisition, destination, primary path, orders, formation), `HordeData`/`HordeMember`/`HordeLink` (UUID-only shared state), `HordeRegistry` (per-dimension, one controller per horde, breach counter, path budget), `HordeSavedData` (world persistence), `HordeSpawner` (group spawn: leader+followers, no orphans), `HordeManager` (wave event), `HordeNavigation` (hybrid nav: leader path + follower slot correction + budgeted/cooldown recovery), `FormationManager`/`FormationMath` (4 deterministic formations), `LeaderElection` (deterministic single successor), `LootTransfer` (simulate-first, lossless).
- **Factions** (`faction/`): centralized ALLIED/NEUTRAL/HOSTILE matrix with config overrides; all targeting goes through it. Zombies, soldiers, scavengers, terrorists.
- **Entities** (`entities/`): Zombie/Soldier/Scavenger/Terrorist + spawn eggs; soldiers & terrorists provisioned with real TACZ guns/ammo via `tacz/TacZAdapter` (`IGunOperator`/`IGun`/`IAmmo`), called at spawn and on load; `tacz` declared mandatory in `mods.toml`.
- **Goals** (`entities/ai/goal/`): faction targeting (followers defer to shared target), follower formation movement, zombie breach (mobGriefing + whitelist/blacklist + pickaxe-for-stone + per-dimension breach cap + assigned breachers), TACZ combat (aim/shoot/reload from own inventory), scavenger defense + leader-assigned looting.
- **Commands**: `/horde start|stop|status|wave|spawn <type> [count]|order <order> [radius]|dismiss [radius]` — 9 orders applied to controllers.
- **Resources**: textures for all 4 entities (generated 64×64 PNGs + carried-over soldier art), lang, entity tags, spawn-egg models, expanded `mods.toml`, `pack.mcmeta`.

## Audit defects fixed vs the 1.0.0 jar
TACZ provisioning disconnected → wired into spawn/load + mandatory dep; missing zombie/scavenger textures → generated; breach ignored mobGriefing → enforced per tick (+ tool rule, per-dimension cap instead of global static); scavenger loot scanned ~9,801 blocks per scavenger → leader-driven throttled scan; item-loss on full inventory → simulate-first lossless transfer; stale `ally`/`terrorist` ids → `zombie/soldier/scavenger/terrorist`; uncalled `setBreacher()` → assigned by spawner/controller.

## Verification (details in VERIFICATION.md)
- COMPILED: 51 classes, 0 errors, offline (`build-offline.ps1`, JDK 17 `--release 17`, cached Forge 47.4.28 mapped jar + TACZ jar).
- Release JAR: `build/libs/horde_soldiers-2.0.0.jar` (116 KB).
- AUTOMATED TESTED: 28/28 logic tests pass (formation, factions, orders, leader election). Loot-transfer runtime test skips (Forge can't bootstrap headless in this sandbox).
- IN-GAME VERIFIED: NO (no network to launch a Forge instance here). BENCHMARKED: NO (procedure in VERIFICATION.md).

## How to rebuild/test
`powershell -File build-offline.ps1` (offline). With network: `gradle --offline build` / `gradle build`.

## Move note
This session's sandbox only permits writes under `C:\Users\NOXROV` (+ temp), so `E:\minecraftproject` could not be created from here. From a normal shell, move it with:
`robocopy "C:\Users\NOXROV\source\repos\horde-soldiers-v2" "E:\minecraftproject" /E`

## Continuation session — 2026-11-17 (v2 hardening + docs)

### Bug found and fixed by the newly-enabled tests
- **`LootTransfer` item duplication (root cause):** the "simulate-first" destination probe called
  `insertAll(dest, simulated.copy())` with `insertItem(..., false)` — i.e. a REAL insert — before
  the real extract+insert, so every moved stack was inserted into the destination twice. Fixed by
  threading a `simulate` flag through `insertAll` and using `true` for the probe. Conservation is
  now asserted by tests (`loot.allItemsConserved`, `loot.maxOpsConserved`, `loot.mixedAllMoved`,
  `loot.wholeStackMovesWithRoom`).

### Test harness breakthrough
- Full `Bootstrap.bootStrap()` cannot run outside FML (Forge `NetworkHooks` event-bus init fails),
  which is why the loot tests used to SKIP. The harness now sets `Bootstrap.isBootstrapped` via
  reflection after `SharedConstants.tryDetectVersion()` — enough for vanilla registries and
  `ItemStack`/`ItemStackHandler` to work headless. The loot-transfer runtime tests now RUN.
- `LogicTests` grew from 28 to **111 checks**: formation slot assignment (`FormationManager`),
  full faction-matrix symmetry, `HordeType`/`HordeOrder`/`FactionRelation` parsing edge cases,
  leader-election null handling, NBT round-trips (`HordeData`, `HordeMember`, `FormationSlot`,
  `HordeLink`, incl. corrupt-enum fallbacks), and 7 loot-transfer cases. Result: **111 passed, 0 failed**.

### Features / config added
- `/horde list` — per-horde detail in the current dimension (type, member count, state, order,
  formation, leader position, short id).
- Wave sizing is now config-driven: `waveSizeGrowthPerWave` (default 1) + `waveSizeGrowthCap`
  (default 6) replace the hardcoded `min(6, waveNumber/2)` (identical defaults).
- `zombieHealth` / `zombieDamage` config (zombie stats were hardcoded before).
- `zombiesBurnInSunlight` (default **false**): horde zombies no longer burn in daylight unless
  enabled — `ZombieEntity.isSunSensitive()` override.

### Cleanups
- `HordeFollowerGoal.canUse()` simplified (`resolve() && !isDone() || resolve()` was just `resolve()`).
- `HordeController.registerMember` leader assignment simplified; dead `prev` variable removed from
  `findBlockingBlockOnPath`; dead code removed from `HordeSavedData.loadInto`; dead local removed
  from `FormationMath.FOLLOW_TRAIL`; `/horde order` error message now lists `MOVE_TO`.

### Build/verification state
- Offline build green: 51 main classes, 0 compile errors; jar `build/libs/horde_soldiers-2.0.0.jar`.
- `LogicTests`: 111/111 pass. Still NOT done (need a networked Forge instance): in-game
  verification and benchmarking — procedure in `VERIFICATION.md`.

## 2026-11-19 (v2.0.1 changes)

- Spawn eggs added to the vanilla Spawn Eggs creative tab (`ModEntities.onBuildCreativeModeTab`).
- `ZombieEntity.setCanBreakDoors(false)` in finalizeSpawn: vanilla door breaking no longer bypasses `ZombieBreachRules`; all breaching goes through `ZombieBreachGoal` (mobGriefing/whitelist/tool/cap enforced).
- New config: `horde.leaderPathInterval` (default 40, was hardcoded) and `horde.saveInterval` (default 100 ticks; `HordeRegistry` now batches dirty saves instead of writing NBT every dirty tick; still saves on server stop).
- Version bumped to **2.0.1** (gradle.properties, build-offline.ps1, install.ps1, dist jar renamed to `horde_soldiers-2.0.1.jar`).
- Doc generator committed as `tools/gen_docs.ps1` (regenerates docs/manifest.json + FILE_MAP.md, fails on undocumented files).
- FormationManager slot-collision fix from earlier today included. Build: 51 classes, 0 errors; LogicTests 111/111 (3 repeat runs). Deployed to `.testenv/workspace` instance (2.0.1 active, 2.0.0/1.01 disabled).

## 2026-11-19 (hardening pass, still v2.0.1)

- Faction relation overrides now re-apply on COMMON config load/reload (`ModEvents.onConfigEvent`
  on `ModConfigEvent`), not just once per server start.
- Minimal logging under logger `horde_soldiers`: wave spawns (count, position, player) and leader
  elections - visible in `logs/latest.log`, greppable by `dist/apocalypssia/check-log.ps1`.
- Regression tests added: `fm.noSlotCollision25Trials` (leader slot 0 + unique world slots across
  25 random-UUID trials) and `loot.zeroMaxOps*`; suite now **114 checks, 114/114** (repeated runs).

## Session 2026-11-20 - fixed the instant launch crash (4 root causes)

The mod crashed immediately in `Apocalypssia-4.94`. Full write-up: `docs\CRASH-FIX-2026-11-20.md`.
Broken jars were MOVED (not deleted) into `DELETED\` folders (project + instance) with READMEs.

1. **BOM in mods.toml** - old `build-offline.ps1` used `Set-Content -Encoding utf8` (PS 5.1 writes
   `EF BB BF`); Forge doesn't strip it -> `ModFileLoadingException`. Now written BOM-free with a
   byte-level self-check.
2. **No mojmap->SRG reobfuscation** - raw javac jar kept mojmap names; the game runs SRG
   (`EntityType$Builder.of` -> `NoSuchMethodError`, SRG name `m_20704_`) + cascading
   `Registry Object not present: horde_soldiers:zombie`. Production jar is now built by cached
   `gradle --offline build` (ForgeGradle reobfs; `gradle jar` alone does NOT).
3. **Spawn eggs** used `SpawnEggItem(ZOMBIE.get(), ...)` in the register lambda -> now
   `ForgeSpawnEggItem(ZOMBIE, ...)` (Supplier-based, lookup deferred).
4. **`FMLModType: GAME` manifest attribute** - forgespi 7.0.1 has no `Type.GAME` ->
   `IllegalArgumentException` during mod scanning. Attribute removed (v1.0.0 had none).
   Also fixed `processResources`: mods.toml uses `${loaderVersion}`, now mapped.

Result: game boots to the title screen with `horde_soldiers 2.0.1` loaded (0 FATAL, no crash
reports, verified live twice: 02:11 and 02:31 on 2026-11-20). `build-offline.ps1` now produces a
verified production jar (`build\libs\horde_soldiers-2.0.1.jar`, SRG/BOM-free/clean manifest) plus
a dev-only mojmap jar for tests; `tools\run-tests.ps1` checks all of it (114/114 logic tests).

## Session 2026-11-24 - user-reported issues fixed + new content (HZCODE.jar)

Reported: world-join crash, invisible/wrong skins (blue zombie), some soldiers unarmed,
taming unclear, no trading. Plus requests: better render/perf, use the skins in
`E:\Games\Minecraft\instances\*.png` per creature, new tab products (timing item), support items
spawning 1-5 soldiers, and rename the jar to HZCODE.jar.

Fixed: structure piece NPE (mirror/rotation null -> setOrientation(NORTH)) + structure JSON required
`spawn_overrides`; skin pools via client ResourceManager + curated per creature incl. the user's PNGs
(scanv=zombie, solder/medic=soldier, scaven=scavenger, trerror=terrorist); exact TACZ rifle list for
soldiers, random any-gun for terrorists, random melee for scavengers, melee fallback for unarmed;
wolf-like taming with the new Taming Treat item + hint; barter trading with soldiers/scavengers;
creative tab "Horde Soldiers (HZCODE)" with eggs + Taming Treat + War Horn (start/stop timed event)
+ Support Call x1..x5; natural spawning for all types (biome modifiers + placements, config-gated).
Mod installed as `mods\HZCODE.jar`. Boot verified 21:02 (0 FATAL, no crash reports, title screen).
Tests 130/130. Docs: docs/FEATURES-2026-11-24.md.

## Session 2026-11-25 - mod id hzcode (HZCODE.jar) + medic + scavenger work + weapon swap

Mod id renamed horde_soldiers -> hzcode (jar file stays HZCODE.jar). New: barter trading (tamed or
wild), soldier weapon swap via right-click (gun/melee swap, ammo into inventory), Medic healer entity
(hzcode:medic, medic skin pool, heals allies), tamed scavengers go to work for 5 min and return with
loot (persisted SavedData), zombies hostile to all factions + vanilla zombie textures in the pool.
Boot verified 23:59 (0 FATAL, title screen). Tests 142/142. Docs: docs/FEATURES-2026-11-25.md.

## Session 2026-11-27 (overnight mission + audit + handoff docs) - HEAD 2ee3741

Commits: cc700f8 (.gitignore), 6a63daf (build preflight guard + tools/restore-build-artifacts.ps1),
4a9a775 (repackage com.nox.horde_soldiers -> com.nox.hzcode; fixes JPMS ResolutionException when HZHER coexists),
a55b18a (male/female variants via UUID-deterministic Variant + slim model; 12 owner skins from Downloads\SKINS;
mercenary type; verified mcx_spear_lt rifle id), a3e0163 (P0 fixes: TACZ 1.2.0 NBT weapon ids via base item +
setGunId/setAmmoId with verified 70-gun/25-ammo tables; terrorist+mercenary class-based loadouts; lazy skin pools +
reload invalidation; medic faction mapping bug fixed + seek-and-heal HealAlliesGoal + medic self-defense melee;
loot table invalid tacz ids removed), 2ee3741 (Faction.MERCENARY + contract/diamond recruitment + scrap_metal/
weapon_part/mercenary_contract items + recipe + tab).

Verification: LogicTests 168/168 (run 2026-11-27). build-offline.ps1 green (142 classes, BUILD SUCCESSFUL,
jar self-checks OK). Boot-verified in Apocalypssia-4.94 repeatedly: hzcode 2.0.1 loads, title screen, 0
hzcode-attributable FATAL/crashes. The 11:06 crash was parcool (external, no com.nox frames).

Owner feedback received: medic healing WORKS in-game (verified). Skins and weapons were broken -> root-caused
and fixed in a3e0163 (lazy pools; TACZ NBT ids) - in-game re-confirmation pending.

Audit (2026-11-27, read-only): completion matrix produced; NOT implemented: orders UI (Phase 2), voice/sound
system + manifest, mercenary world presence/distinct AI, materials progression, branding note, overnight report.
Multiplayer never tested. See PLAN.md for the Phase 2 spec and prioritized backlog; .handoff.md for resume steps.

Security: GitHub PAT exposed in Desktop txt file - revoke it.
