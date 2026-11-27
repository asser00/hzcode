# Skin asset manifest (HZCODE)

All entity skins are 64x64 PNG player skins under `assets/hzcode/textures/entity/skins/<type>/`
(general pool) and `.../<type>/male|female/` (gender variant pools).

## Owner-provided skins (added 2026-11-27)
Source folder: `C:\Users\NOXROV\Downloads\SKINS` (supplied by the project owner - approved for bundling).

| Target path in jar | Source file | Variant |
|---|---|---|
| skins/scavenger/male/0.png | scavenger-m1-raider.png | scavenger male |
| skins/scavenger/male/1.png | scavenger-m2-gasmask.png | scavenger male |
| skins/scavenger/female/0.png | scavenger-f1-mechanic.png | scavenger female |
| skins/scavenger/female/1.png | scavenger-f2-nomad.png | scavenger female |
| skins/soldier/female/0..4.png | Fsoldier/FS1..FS5.png | soldier female |
| skins/mercenary/male/0.png | solders...(mercenere)/soldier-m1-woodland.png | mercenary male |
| skins/mercenary/male/1.png | .../soldier-m2-desert.png | mercenary male |
| skins/mercenary/female/0.png | .../soldier-f1-ash-sniper.png | mercenary female |
| skins/mercenary/female/1.png | .../soldier-f2-breacher.png | mercenary female |
| skins/medic/female/0..2.png | female medic/female medic1..3.png | medic (always female) |

## Pre-existing pools (general fallback, kept)
- skins/zombie 0-3 + vanilla zombie textures mixed in at render time
- skins/soldier 0-5, skins/scavenger 0-7, skins/terrorist 0-7, skins/medic 0-3
- NOTE: some of these were originally extracted from other mods' jars (MCA, superbwarfare, quark,
  rottencreatures, ...) or downloaded player-skin sites. Those third-party assets are NOT cleared
  for redistribution - see open licensing task. Owner-provided skins above are fine to bundle.

## Adding new owner skins later
Drop 64x64 PNGs into `skins/<type>/` (general) or `skins/<type>/male|female/` (gendered), rebuild
with `build-offline.ps1`, copy `build/libs/hzcode-2.0.1.jar` into the instance mods folder as
`HZCODE.jar`. No code changes needed - pools are discovered via the ResourceManager at runtime.
