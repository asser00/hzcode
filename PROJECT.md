# HZCODE — Project Document

## Identity
- Minecraft **1.20.1 / Forge 47.4.28 / Java 17** mod. Mod id: `hzcode`. Installed jar file name: `HZCODE.jar`.
- Theme: military survival & faction warfare — horde engine ("one brain, multiple bodies") plus human factions
  (soldiers, scavengers, terrorists, medics, mercenaries) vs zombie hordes.
- **Branding**: "Super Warfare" is an alternative/backup name concept only. Do NOT rename the mod id,
  packages, repo, or jar. HZCODE stays the technical identity.

## Hard boundaries
- **HZHER is a separate project and is READ-ONLY.** Never modify it (code, repo, or its jar).
- **ZOWAR is a separate, unrelated project.** Do not transfer requirements or architecture from/to it.
- Work only in this repository: `C:\Users\NOXROV\source\repos\HZCODE`.

## Repository
- Local: `C:\Users\NOXROV\source\repos\HZCODE` (branch `main`, clean, in sync).
- Remote: `https://github.com/asser00/hzcode.git` (`main`). Push with plain `git push` (upstream set).
- Do not commit build artifacts, `build/`, `libs/` jars, logs, or secrets (see `.gitignore`).

## Dependencies
- Forge 47.4.28 (cached in `~/.gradle`, offline-capable), **TACZ 1.2.0 hard dependency** (`tacz-forge-1.20.1-1.2.0.jar`),
  Java 17 (from `~/.gradle\jdks`).
- TACZ 1.2.0 API facts (verified against the installed jar): one base gun item + one base ammo item; the concrete
  weapon lives in NBT via `IGun.setGunId(stack, tacz:<gunId>)` / `IAmmo.setAmmoId(stack, tacz:<ammoId>)`.
  Verified id tables live in `WeaponStocker.GUNS_BY_CLASS` (70 guns) and `AMMO_IDS` (25). Do not invent ids.

## Build / test / run
```
powershell -File build-offline.ps1            # dev compile + LogicTests + gradle production jar + self-checks
powershell -File tools\run-tests.ps1          # full suite: rebuild + smoke test on production jar + LogicTests
powershell -File tools\restore-build-artifacts.ps1   # if build\cp / libs\tacz jar were wiped (untracked)
```
- Production jar: `build\libs\hzcode-2.0.1.jar` → copy into the instance as `mods\HZCODE.jar`.
- Dev jar `build\libs\horde_soldiers-2.0.1-dev.jar` is mojmap — tests only, never install.
- Game instance: `E:\Games\Minecraft\instances\Apocalypssia-4.94\minecraft`. Launch:
  `E:\Games\Minecraft\polymc.exe --launch Apocalypssia-4.94`.
  Logs: `...\logs\latest.log`, `...\logs\debug.log`, `...\crash-reports\`.
- Known external flake: parcool crash `No model for layer minecraft:player#parcool.equipment_in` — not HZCODE.

## Architecture map (package `com.nox.hzcode`)
- `horde/` — HordeController/Registry/Spawner/Manager, formations, navigation, SavedData, ScavengerWorkManager.
- `faction/` — `FactionRelations` (pure matrix, unit-tested) + `FactionManager` (MC-aware, config overrides). All targeting goes through it.
- `entities/` — AbstractHordeMob (horde link + owner + 27-slot inventory capability), Zombie/Soldier/Scavenger/Terrorist/Medic,
  SkinVariant/Variant (deterministic per-UUID skin & gender), `ai/goal/*`.
- `equipment/WeaponStocker` — verified TACZ gun/ammo tables, class-based selection, melee pool.
- `tacz/TacZAdapter` — TACZ bridge (base items + NBT ids, equip, combat API wrappers).
- `items/`, `event/ModEvents` (interactions: trade/tame/recruit/weapon-swap/release), `commands/ModCommands` (`/horde ...`),
  `client/` (renderers, lazy skin pools, PoolInvalidator), `config/ModConfig`, `worldgen/`, `structure/`.

## Environment gotchas (Windows/PowerShell)
- PowerShell: no bash heredocs or `for (...)` loops; `Start-Sleep -Seconds 5` (never `-5`); `2>/dev/null` breaks — avoid.
  For non-trivial file edits, write a temp `.py` via a single-quoted here-string and run `python` on it.
- `apply_patch` tool is unsupported in this environment — edit via scripts.
- Empty `build\cp` makes javac fail with cryptic `invalid flag` — the preflight guard in `build-offline.ps1` now catches it.
- `mods.toml` must be BOM-free; production jar must be SRG-reobfuscated (ForgeGradle does it).

## Security
- A GitHub personal access token was pasted in plaintext into `C:\Users\NOXROV\Desktop\New Text Document (3).txt`
  (around line 25591). **Revoke it** at GitHub → Settings → Developer settings → Personal access tokens, and scrub
  that file. Never put tokens in chat/files/commits.

## Docs map
- `PLAN.md` — active mission (Phase 2: squad commands) + prioritized backlog.
- `RECAP.md` — session-by-session history.
- `.handoff.md` — how to resume work in a fresh session.
- `docs/` — `FEATURES-2026-11-*.md`, `SKIN-ASSETS.md`, `TEST_ENV.md`, `CRASH-FIX-2026-11-20.md`, `FILE_MAP.md`, `manifest.json`.
