# Test environment - Apocalypssia instance (E:\Games\Minecraft\instances)

Target: the MultiMC/Prism modpack instances `Apocalypssia-4.1.4`, `Apocalypssia-4.1.4-CP004`,
`Apocalypssia-4.94` (MC 1.20.1 / Forge 47.x, TACZ present: `tacz-forge-1.20.1-1.2.0.jar` in `minecraft\mods`).

> This agent sandbox can only write under `C:\Users\NOXROV`, so the jar is staged here:
> `dist/apocalypssia/mods/horde_soldiers-2.0.0.jar` + `dist/apocalypssia/install.ps1`.

## 1. Install the mod into an instance

From a normal shell (not the sandbox):

```powershell
powershell -File C:\Users\NOXROV\source\repos\horde-soldiers-v2\dist\apocalypssia\install.ps1 -Instance Apocalypssia-4.94
```

Updated 2026-11-20: the jar to install is the PRODUCTION build `C:\Users\NOXROV\source\repos\horde-soldiers-v2\build\libs\horde_soldiers-2.0.1.jar` (SRG-reobfuscated by `gradle --offline build` via `build-offline.ps1`; raw javac/mojmap jars crash the game). Copy it to `E:\Games\Minecraft\instances\Apocalypssia-4.94\minecraft\mods\horde_soldiers-2.0.1.jar` (move any old jar to `DELETED\` instead of deleting) and verify TACZ is installed (hard dependency). `dist\apocalypssia\mods\` holds the same production jar.

## 2. Launch

Open the launcher at `E:\Games\Minecraft`, pick the instance, launch. The mod id is `horde_soldiers`;
check `logs\latest.log` for `Found valid mod file horde_soldiers-2.0.1.jar`, zero `FATAL` lines, no new files in `crash-reports\`, and `logs\modlist.txt` containing `horde_soldiers`; the game must reach the title screen. Verified working 2026-11-20 (see `docs\CRASH-FIX-2026-11-20.md`). Dependency errors (tacz mandatory) would abort boot.

## 3. In-game test script (op, permission level 2)

```
/horde status                                  -> event state, wave, horde/member counts
/horde spawn zombie 10                         -> spawns a 10-member zombie horde at your position
/horde list                                    -> per-horde detail (members, state, order, formation, leader pos)
/horde spawn soldier 5
/horde spawn scavenger 5
/horde spawn terrorist 5
/horde order hold_position 64                  -> hordes in radius hold
/horde order follow 64
/horde order move_to 64                        -> hordes move to your position
/horde order retreat 64  /  cease_fire 64  /  recall 64
/horde wave 3                                  -> 3 zombie waves near you
/horde start / stop                            -> timed event on/off
/horde dismiss 64                              -> removes hordes in radius
```

Expected: soldiers/terrorists hold real TACZ guns and shoot hostiles; zombies path to targets and
breach wooden doors/planks (mobGriefing on, per-dimension breach cap); scavengers loot nearby
containers losslessly (check the container afterwards - nothing should be voided); leader death
triggers a single deterministic successor (`/horde list` leader changes); relog and confirm horde
state persists (SavedData + entity NBT).

## 4. Benchmark (closes the BENCHMARKED gap in VERIFICATION.md)

1. Install Spark (or use `/forge tick`) in the instance.
2. `/horde spawn zombie 20`, `soldier 30`, `scavenger 30`; `/horde start`.
3. Record average/peak MSPT while hordes fight/move; watch `HordeRegistry` counters (path budget,
   active breaches, loot scans) via `/horde status` + logs.
4. Repeat with the old `horde_soldiers-1.0.0.jar` on the same world/seed for comparison.

## 5. Headless server option

If a display is unavailable, run a dedicated server from the instance dir with the same mods:
accept `eula.txt`, then `java -jar <forge-server-jar> nogui`, and drive the same `/horde` commands
from the server console. Logs land in `logs/latest.log`.

## 2026-11-19 - virtual test env + deploy package (what was actually run here)

The agent sandbox can only write under the repo (not `E:`) and has no display, so the real
game cannot be launched from here. What WAS executed and verified in-sandbox:

- Virtual instance `.testenv/instances/Apocalypssia-4.94/minecraft/mods` with the real
  `tacz-forge-1.20.1-1.2.0.jar` (copied from the E: instance) + `dist/apocalypssia/install.ps1`
  run against it with `-Base .testenv\instances`: TACZ detected, `horde_soldiers-2.0.0.jar`
  installed, config preset deployed to `minecraft\config\horde_soldiers-common.toml`. PASS.
- Jar smoke test on `build/libs/horde_soldiers-2.0.0.jar`: 94 entries, 51 classes, 30 resources,
  `mods.toml` fully expanded (no `${...}` left), `modId="tacz" mandatory=true [1.2.0,)` present. PASS.
- Offline logic tests: 111/111 PASS (`build-offline.ps1`).
- Confirmed on `E:\Games\Minecraft`: instances `Apocalypssia-4.1.4`, `-CP004`, `-4.94` all contain
  `tacz-forge-1.20.1-1.2.0.jar` in `minecraft\mods` (hard dependency satisfied). No vanilla
  *server* jar exists in the launcher libraries, so a headless server boot is not possible
  offline here; in-game verification must be done via the MultiMC client.

Deploy package (`dist/apocalypssia/`): `mods/horde_soldiers-2.0.0.jar`, `install.ps1`
(installs jar + config preset into a chosen instance, checks TACZ, warns about old 1.0.0 jar),
`config/horde_soldiers-common.toml` (test preset), `test-commands.txt` (paste-ready `/horde`
script), `check-log.ps1` (greps `logs/latest.log` for `horde_soldiers` after a run).

To run for real: `powershell -File dist\apocalypssia\install.ps1 -Instance Apocalypssia-4.94`
then launch in MultiMC, run `test-commands.txt`, then `check-log.ps1`.

## 2026-11-19 (later) - workspace copy + spawn eggs + flake fix

- Local workspace `.testenv/workspace/` (~4.8 GB, pure local copy, no downloads): full
  `instances/Apocalypssia-4.94` + `libraries/` + `meta/` from `E:\Games\Minecraft`. Point a
  MultiMC data dir here (or copy back to E:) to run fully offline. `install.ps1` was run
  against it: TACZ detected, `horde_soldiers-2.0.0.jar` installed, config preset deployed,
  and the pre-existing `horde_soldiers-1.01.jar` (present in the real 4.94 mods) renamed to
  `.disabled` so Forge won't hit a duplicate modId. install.ps1 does the same on the real instance.
- Spawn eggs: already fully wired - 4 `SpawnEggItem` registrations (ModEntities), 4 model JSONs
  in the jar, lang entries for all 4; lang also gained entries for the new config keys and the
  MOVE_TO order string. Rebuilt jar re-verified: 51 classes, expanded mods.toml.
- Flaky-test root cause found and fixed: `FormationManager.assignSlots` gave the sorted-first
  member slot index 0 even when the leader wasn't first -> two members could share slot 0.
  Now the leader always takes slot 0 and others get unique 1..n-1. LogicTests 111/111, 5/5 repeat runs.
