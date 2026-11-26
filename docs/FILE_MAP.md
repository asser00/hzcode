# FILE MAP - horde-soldiers-v2

Machine-readable version: `docs/manifest.json` (regenerate with `tools/gen_docs.ps1`).

| Path | Role | What it does |
|---|---|---|
| `src/main/java/com/nox/horde_soldiers/HordeSoldiersMod.java` | entrypoint | Forge @Mod entry point: registers config, entities, capabilities, creative-tab listener, event listeners (commands, entity join, death, server tick/stop, wave manager). |
| `src/main/java/com/nox/horde_soldiers/client/ClientModEvents.java` | client | Entity renderer registration; per-type skin pools (textures/entity/skins/<type>/), skin picked deterministically from the entity UUID (SkinVariant). |
| `src/main/java/com/nox/horde_soldiers/commands/ModCommands.java` | command | /horde start\|stop\|status\|list\|wave\|spawn <type> [count]\|order <order> [radius]\|dismiss [radius]. Orders apply to HordeControllers only. |
| `src/main/java/com/nox/horde_soldiers/config/ModConfig.java` | config | ForgeConfigSpec COMMON spec: general/wave, horde engine intervals, zombie breach rules, per-type stats + naturalSpawn + randomWeapons flags, faction overrides, taming (enabled/item/chance). |
| `src/main/java/com/nox/horde_soldiers/entities/AbstractHordeMob.java` | entity-base | PathfinderMob base for humanoid members: HordeLink + persisted 27-slot ItemStackHandler capability + ITameableHorde owner (NBT-persisted); never despawns while in a horde or tamed. |
| `src/main/java/com/nox/horde_soldiers/entities/IHordeEntity.java` | interface | Horde membership contract: hordeId, leader flag, HordeType. |
| `src/main/java/com/nox/horde_soldiers/entities/ITameableHorde.java` | interface | Taming contract: isTamed/getOwnerUUID/setTamed. |
| `src/main/java/com/nox/horde_soldiers/entities/MedicEntity.java` | entity | Medic healer: never fights, heals friendly entities in an aura (HealAlliesGoal), medic skin pool, tameable. |
| `src/main/java/com/nox/horde_soldiers/entities/ModEntities.java` | registry | DeferredRegister: 4 entity types + 4 spawn eggs (also added to the vanilla Spawn Eggs creative tab) + attributes. |
| `src/main/java/com/nox/horde_soldiers/entities/ScavengerEntity.java` | entity | Looter: spawns with a random melee weapon, retreat defense + leader-assigned loot via LootContainerGoal, tamable. |
| `src/main/java/com/nox/horde_soldiers/entities/SkinVariant.java` | pure-logic | Deterministic per-UUID skin index + skin texture path; unit-tested. |
| `src/main/java/com/nox/horde_soldiers/entities/SoldierEntity.java` | entity | Armed soldier: random rifle-class TACZ gun + matched ammo (or fixed config gun), SoldierTaczCombatGoal, tamable (follow/defend owner). |
| `src/main/java/com/nox/horde_soldiers/entities/TerroristEntity.java` | entity | Hostile raider: random TACZ gun (any class) + matched ammo when terroristsUseGuns, melee fallback. |
| `src/main/java/com/nox/horde_soldiers/entities/ZombieEntity.java` | entity | Horde zombie: breach goal, faction targeting, config health/damage, isSunSensitive() override, vanilla door-breaking disabled so all breaching goes through ZombieBreachRules. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/FactionTargetGoal.java` | ai-goal | Target selector restricted to FactionManager-hostile entities; followers defer to the shared controller target. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/HealAlliesGoal.java` | ai-goal | Medic heal aura: heals + regenerates allies/owner in radius on an interval. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/HordeFollowerGoal.java` | ai-goal | Follower movement toward the published formation slot; budgeted recovery path only when stuck/separated. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/LootContainerGoal.java` | ai-goal | Paths to leader-assigned loot container, runs LootTransfer (simulate-first, lossless). |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/ScavengerDefenseGoal.java` | ai-goal | Flees nearest hostile when health below scavengerRetreatThreshold %. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/ScavengerWorkGoal.java` | ai-goal | Tamed scavengers go to work (disappear 5 min, return with loot) when owner is near and no hostiles. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/SoldierTaczCombatGoal.java` | ai-goal | TACZ combat: aim/shoot with computed yaw-pitch, reload from own inventory, strafing; respects CEASE_FIRE/RETREAT. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/TamedFollowGoal.java` | ai-goal | Tamed mobs follow their owner (move/look flags); no-op when wild. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/TamedOwnerDefenseGoal.java` | ai-goal | Tamed mobs target whoever last hurt their owner (or whom the owner last hurt). |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/ZombieBreachGoal.java` | ai-goal | Breacher-only mining of the shared breach target; holds a per-dimension breach slot; re-validates rules every tick. |
| `src/main/java/com/nox/horde_soldiers/entities/ai/goal/ZombieBreachRules.java` | rules | Central breach rules: breachEnabled, mobGriefing, whitelist/blacklist, pickaxe-for-stone. |
| `src/main/java/com/nox/horde_soldiers/equipment/WeaponStocker.java` | equipment | Random weapon provisioning: melee pool (swords/axes/tridents/melee-named mod items with attack damage), random TACZ guns (rifle-only or any), ammo matched via IAmmo.isAmmoOfGun. Pure matchers unit-tested. |
| `src/main/java/com/nox/horde_soldiers/event/ModEvents.java` | events | Forge event wiring: attributes, capabilities, commands, entity join (re-attach), death (detach), server tick, server stopping, taming interact (PlayerInteractEvent.EntityInteract). |
| `src/main/java/com/nox/horde_soldiers/faction/Faction.java` | enum | PLAYER, SOLDIER, ZOMBIE, SCAVENGER, TERRORIST. |
| `src/main/java/com/nox/horde_soldiers/faction/FactionManager.java` | faction | MC-aware facade: entity->faction mapping, config overrides, tamed-owner ALLIED overrides, all hostility checks. |
| `src/main/java/com/nox/horde_soldiers/faction/FactionRelation.java` | enum | ALLIED/NEUTRAL/HOSTILE + lenient parse(). |
| `src/main/java/com/nox/horde_soldiers/faction/FactionRelations.java` | pure-logic | Pure symmetric relation matrix with runtime overrides; unit-tested. |
| `src/main/java/com/nox/horde_soldiers/horde/FormationManager.java` | pure-logic | Stable slot assignment: UUID-sorted, leader slot 0, others unique 1..n-1 (collision fixed). |
| `src/main/java/com/nox/horde_soldiers/horde/FormationMath.java` | pure-logic | Pure formation offset math for 4 types; unit-tested. |
| `src/main/java/com/nox/horde_soldiers/horde/FormationSlot.java` | pure-logic | Slot = index + offset + world pos; NBT-persisted. |
| `src/main/java/com/nox/horde_soldiers/horde/FormationType.java` | enum | FOLLOW_TRAIL/PROTECT_LEADER/COMBAT_SPREAD/HOLD_POSITION. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeController.java` | core | ONE strategic brain per horde: throttled targeting, orders, formation, primary path (leaderPathInterval), loot scan, breach designation; UUID-only state. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeData.java` | core | Serializable shared horde state + NBT round-trip. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeLink.java` | core | Per-entity membership holder persisted in entity NBT. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeManager.java` | core | Timed wave event: zombie hordes near random overworld players; size = zombieHordeSize + min(cap, wave/2*growth); spawnCap per dimension. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeMember.java` | core | Per-member record: role, slot, stuck/separated detection, path cooldown, dormancy; NBT-persisted. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeNavigation.java` | core | Hybrid navigation helpers: slot correction + budgeted/cooldown recovery pathing. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeOrder.java` | enum | 9 orders + parse(). |
| `src/main/java/com/nox/horde_soldiers/horde/HordeRegistry.java` | core | Per-dimension registry: one controller per horde, entity index, breach counter, path budget, batched saves (saveInterval). Never force-loads chunks. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeSavedData.java` | persistence | SavedData storing horde states + entity index. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeSpawner.java` | core | Group spawn: leader+followers in one controller, stable slots, faction init, TACZ provisioning, breacher designation; no orphans. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeState.java` | enum | IDLE/ACTIVE/COMBAT/RETREATING/DISBANDING. |
| `src/main/java/com/nox/horde_soldiers/horde/HordeType.java` | enum | 4 horde types mapped to factions + parse(). |
| `src/main/java/com/nox/horde_soldiers/horde/LeaderElection.java` | pure-logic | Deterministic leader election (leader flag > health > age, UUID tiebreak); unit-tested. |
| `src/main/java/com/nox/horde_soldiers/horde/LootTransfer.java` | pure-logic | Lossless container->inventory transfer: simulated probe insert (duplication bug fixed), real extract only for what fits, remainder put back/dropped. |
| `src/main/java/com/nox/horde_soldiers/horde/MemberState.java` | enum | ACTIVE/STUCK/SEPARATED/COMBAT/RETREATING/IDLE. |
| `src/main/java/com/nox/horde_soldiers/horde/ScavengerWorkManager.java` | core | Work-trip scheduler: pending workers, 5-min duration, loot generation, SavedData persistence, return-to-owner. |
| `src/main/java/com/nox/horde_soldiers/items/ModItems.java` | registry | Mod items + creative tab: taming_treat, war_horn, support_call_1..5, tab Horde Soldiers (HZCODE). |
| `src/main/java/com/nox/horde_soldiers/items/SupportCallItem.java` | item | Support Call xN: right-click spawns a soldier horde of size N at the player. |
| `src/main/java/com/nox/horde_soldiers/items/WarHornItem.java` | item | Horde War Horn: right-click starts/stops the timed horde event. |
| `src/main/java/com/nox/horde_soldiers/structure/HordeCampPiece.java` | structure | Places the camp blocks: 9x9 plank/cobble platform, corner fences + torch, campfire, loot chest/barrel (chests/horde_camp loot table). |
| `src/main/java/com/nox/horde_soldiers/structure/HordeCampStructure.java` | structure | Horde camp structure: picks a surface point per chunk, returns a GenerationStub with one HordeCampPiece. |
| `src/main/java/com/nox/horde_soldiers/structure/ModStructures.java` | registry | DeferredRegister: structure type hzcode:horde_camp + its piece type. |
| `src/main/java/com/nox/horde_soldiers/tacz/TacZAdapter.java` | integration | TACZ API bridge (IGunOperator/IGun/IAmmo): equipSoldier, aim/shoot/reload, ammo matching; safe no-ops without TACZ. |
| `src/main/java/com/nox/horde_soldiers/worldgen/ModSpawnPlacements.java` | worldgen | Registers ON_GROUND spawn placements for all 4 types in common setup; config-gated (naturalSpawn), vanilla monster rules for zombies. |
| `src/main/resources/pack.mcmeta` | resource | Resource pack descriptor. |
| `src/main/resources/assets/hzcode/lang/en_us.json` | resource | Lang: entities, 4 spawn eggs, commands, all config keys. |
| `src/main/resources/assets/hzcode/models/item/medic_spawn_egg.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/scavenger_spawn_egg.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/soldier_spawn_egg.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/support_call_1.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/support_call_2.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/support_call_3.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/support_call_4.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/support_call_5.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/taming_treat.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/terrorist_spawn_egg.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/war_horn.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/models/item/zombie_spawn_egg.json` | resource | Spawn-egg item model JSON. |
| `src/main/resources/assets/hzcode/textures/entity/scavenger.png` | resource | 64x64 entity texture PNG. |
| `src/main/resources/assets/hzcode/textures/entity/soldier_biker.png` | resource | 64x64 entity texture PNG. |
| `src/main/resources/assets/hzcode/textures/entity/soldier_medic.png` | resource | 64x64 entity texture PNG. |
| `src/main/resources/assets/hzcode/textures/entity/terrorist.png` | resource | 64x64 entity texture PNG. |
| `src/main/resources/assets/hzcode/textures/entity/zombie.png` | resource | 64x64 entity texture PNG. |
| `src/main/resources/assets/hzcode/textures/entity/skins/medic/0.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/medic/1.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/medic/2.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/medic/3.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/scavenger/0.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/scavenger/1.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/scavenger/2.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/scavenger/3.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/scavenger/4.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/scavenger/5.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/scavenger/6.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/scavenger/7.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/soldier/0.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/soldier/1.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/soldier/2.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/soldier/3.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/soldier/4.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/soldier/5.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/terrorist/0.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/terrorist/1.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/terrorist/2.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/terrorist/3.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/terrorist/4.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/terrorist/5.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/terrorist/6.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/terrorist/7.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/zombie/0.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/zombie/1.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/zombie/2.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/zombie/3.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/entity/skins/zombie/4.png` | resource | 64x64 skin PNG pool per entity type (internet player skins + textures reused from modpack jars); picked per-entity by UUID. |
| `src/main/resources/assets/hzcode/textures/item/support_call_1.png` | resource | 16x16 item icon PNG (generated). |
| `src/main/resources/assets/hzcode/textures/item/support_call_2.png` | resource | 16x16 item icon PNG (generated). |
| `src/main/resources/assets/hzcode/textures/item/support_call_3.png` | resource | 16x16 item icon PNG (generated). |
| `src/main/resources/assets/hzcode/textures/item/support_call_4.png` | resource | 16x16 item icon PNG (generated). |
| `src/main/resources/assets/hzcode/textures/item/support_call_5.png` | resource | 16x16 item icon PNG (generated). |
| `src/main/resources/assets/hzcode/textures/item/taming_treat.png` | resource | 16x16 item icon PNG (generated). |
| `src/main/resources/assets/hzcode/textures/item/war_horn.png` | resource | 16x16 item icon PNG (generated). |
| `src/main/resources/data/hzcode/loot_tables/chests/horde_camp.json` | resource | Loot table for the horde camp chest/barrel. |
| `src/main/resources/data/hzcode/tags/entity_types/horde_mobs.json` | resource | Entity-type tag grouping all horde mobs. |
| `src/main/resources/data/hzcode/tags/entity_types/medic_units.json` | resource | Entity-type tag listing this mod's units. |
| `src/main/resources/data/hzcode/tags/entity_types/scavenger_units.json` | resource | Entity-type tag listing this mod's units. |
| `src/main/resources/data/hzcode/tags/entity_types/soldier_units.json` | resource | Entity-type tag listing this mod's units. |
| `src/main/resources/data/hzcode/tags/entity_types/terrorist_units.json` | resource | Entity-type tag listing this mod's units. |
| `src/main/resources/data/hzcode/tags/entity_types/zombie_units.json` | resource | Entity-type tag listing this mod's units. |
| `src/main/resources/data/hzcode/worldgen/biome_modifier/add_medic_spawns.json` | resource | forge:add_spawns biome modifier adding this mob type to overworld natural spawning. |
| `src/main/resources/data/hzcode/worldgen/biome_modifier/add_scavenger_spawns.json` | resource | forge:add_spawns biome modifier adding this mob type to overworld natural spawning. |
| `src/main/resources/data/hzcode/worldgen/biome_modifier/add_soldier_spawns.json` | resource | forge:add_spawns biome modifier adding this mob type to overworld natural spawning. |
| `src/main/resources/data/hzcode/worldgen/biome_modifier/add_terrorist_spawns.json` | resource | forge:add_spawns biome modifier adding this mob type to overworld natural spawning. |
| `src/main/resources/data/hzcode/worldgen/biome_modifier/add_zombie_spawns.json` | resource | forge:add_spawns biome modifier adding this mob type to overworld natural spawning. |
| `src/main/resources/data/hzcode/worldgen/structure/horde_camp.json` | resource | worldgen/structure JSON: biome filter + decoration step for the code-registered structure type. |
| `src/main/resources/data/hzcode/worldgen/structure_set/horde_camp.json` | resource | worldgen/structure_set JSON: random_spread placement (spacing/separation/salt) for the structure. |
| `src/main/resources/META-INF/mods.toml` | resource | Mod metadata; forge + minecraft + mandatory tacz [1.2.0,) AFTER. |
| `src/test/java/com/nox/horde_soldiers/test/LogicTests.java` | test | 130-check offline suite (gradle logicTest / build-offline.ps1); headless registry bootstrap via reflection on Bootstrap.isBootstrapped. |
| `docs/CRASH-FIX-2026-11-20.md` | doc | Full root-cause write-up of the 2026-11-20 instant-crash fixes (BOM, reobf, FMLModType, spawn eggs) + verification evidence. |
| `docs/FEATURES-2026-11-23.md` | doc | Skins, random weapons, taming, natural spawning, horde_camp structure: what was added, config keys, in-game test script. |
| `docs/FEATURES-2026-11-24.md` | doc | 2026-11-24 build (HZCODE.jar): fixes + skins/taming/trading/tab items/support calls/natural spawning/structure. |
| `docs/FEATURES-2026-11-25.md` | doc | 2026-11-25 build: mod id hzcode (HZCODE.jar), trading, weapon swap, medic, scavenger work, zombie hostility. |
| `docs/FILE_MAP.md` | docs | Human-readable file map generated from docs/manifest.json. |
| `docs/manifest.json` | docs | Machine-readable project manifest generated by tools/gen_docs.ps1 (this file list + metadata + commands + config + data flow). |
| `docs/TEST_ENV.md` | docs | Install/launch/test/benchmark guide for the Apocalypssia instances + in-sandbox verification results. |
| `dist/apocalypssia/check-log.ps1` | deploy | Greps an instance logs/latest.log for horde_soldiers load lines and errors. |
| `dist/apocalypssia/install.ps1` | deploy | Installs horde_soldiers-2.0.1.jar into a chosen Apocalypssia instance, disables older horde_soldiers jars, verifies TACZ, deploys config preset. |
| `dist/apocalypssia/test-commands.txt` | deploy | Paste-ready in-game /horde test script. |
| `dist/apocalypssia/config/horde_soldiers-common.toml` | deploy | Test config preset (defaults, waveInterval=120) deployed to <instance>\minecraft\config. |
| `dist/apocalypssia/mods/horde_soldiers-2.0.1.jar` | deploy | Staged release jar for deployment into instance mods dirs. |
| `dist/apocalypssia/mods/hzcode-2.0.1.jar` | deploy | Production mod jar under its mod-id name (installed as HZCODE.jar). |
| `dist/apocalypssia/mods/HZCODE.jar` | deploy | Production mod jar under the user-requested name HZCODE.jar. |
| `tools/gen_docs.ps1` | tools | Regenerates docs/manifest.json + docs/FILE_MAP.md; fails if any tracked file is undocumented. |
| `tools/run-tests.ps1` | tools | One-command offline debug/test: jar smoke test (classes, mods.toml, tacz dep, spawn-egg models) + LogicTests 111 checks. |
| `build.gradle` | build | ForgeGradle 6.0.54, Java 17, deps on forge + libs/tacz jar; logicTest task; jar manifest; processResources expansion. |
| `settings.gradle` | build | Root project name + plugin repositories. |
| `gradle.properties` | build | Mod metadata tokens (mod_id, mod_version=2.0.1, ranges) consumed by build.gradle. |
| `build-offline.ps1` | build | Offline build: javac --release 17 vs cached jars, jars build/libs/horde_soldiers-2.0.1.jar, runs LogicTests. |
| `build_classpath.txt` | build | Recorded classpath for offline compilation. |
| `README.md` | docs | Architecture overview, config reference, build instructions. |
| `RECAP.md` | docs | Session recap: what was built, defects fixed vs 1.0.0, continuation logs. |
| `VERIFICATION.md` | docs | Per-item verification status, environment blockers, benchmark procedure. |
| `libs/tacz-1.20.1-1.2.0.jar` | dependency | TACZ 1.2.0 mod jar (hard dependency), compiled against directly. |
| `.testenv/workspace/` | test-env | ~4.8GB local offline copy of the E: launcher data (Apocalypssia-4.94 instance + libraries + meta, no downloads) plus a 66MB minimal Apocalypssia-small debug instance (tacz + horde_soldiers-2.0.1 only). Mod installed; old jars disabled. Excluded from per-file listing. |

## Processing notes

- Compile: all of `src/main/java` together against `build/cp` + `build/jarsbak` + `libs/tacz-1.20.1-1.2.0.jar` (`build-offline.ps1`, `javac --release 17`).
- Tests: `com.nox.hzcode.test.LogicTests` (111 checks) with main+test classes and same jars on classpath.
- Deploy/test: `dist/apocalypssia/install.ps1` -> Apocalypssia instance (real one on E:, or the `.testenv/workspace` copy); see `docs/TEST_ENV.md`.
- `mods.toml`/`pack.mcmeta` contain `${...}` placeholders expanded by Gradle `processResources`; `build-offline.ps1` expands them with fixed values when jarring.