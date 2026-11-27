# HZCODE — Plan

## Phase 2 (ACTIVE, not started) — Squad Command & Unit Control System

Goal: real player-facing FOLLOW / STAY / DEFEND orders for owned (tamed) units.

### Spec
- `EntityOrder` enum: FOLLOW, STAY, DEFEND (+ parse; extensible for PATROL/GUARD/HOLD_FIRE/ENGAGE/RETREAT/REGROUP/ESCORT later).
- State lives on `ITameableHorde`/`AbstractHordeMob`: order + defend anchor, persisted in entity NBT, default FOLLOW (safe for old saves), synched to clients (EntityDataAccessor or deterministic+persisted).
- Network: `SimpleChannel` (`hzcode:main`), C2S `OrderPacket(entityId, order)`; server validates: entity exists, correct dimension/type, `isTamed()` and `getOwnerUUID().equals(sender)` — never trust client claims. Reject unauthorized silently/with message.
- UI: keybind (suggest G) → raycast owned unit within ~6 blocks → minimal `Screen` with 3 buttons, active order highlighted; button → packet; closing without selection changes nothing.
- Behavior:
  - FOLLOW: follow owner at configurable distance, no teleport spam, resume after combat, keep combat/faction rules.
  - STAY: stop following, hold position (block wander), still self-defend when attacked, persists across save/load.
  - DEFEND: anchor = position when ordered; engage hostiles (per `FactionManager`) within configurable radius around anchor/owner; return to anchor after combat; no infinite chase; never attack neutral/allied.
- Config: follow distance, defend radius, defend return distance; throttle path recalcs; combat must not permanently override the order.
- Compatibility: medic healing keeps working under FOLLOW/DEFEND; soldier TACZ combat intact; scavenger melee intact; mercenary recruit/ownership intact; faction matrix remains authoritative. Unsupported combos must fail explicitly, not silently.
- Tests: extend `LogicTests` — order transitions, default, NBT round-trip, invalid order parse, faction-aware defend targeting, ownership helpers. Run full suite; build production jar. No multiplayer claims without testing. Provide exact manual in-game test procedure.
- Done = the 12 definition-of-done items (working FOLLOW/STAY/DEFEND, real UI, server-authoritative, owner validation, persistence, client sync, AI intact, tests pass, build succeeds, documented).

### Suggested integration points (verified in source)
- `ITameableHorde` + `AbstractHordeMob` (owner NBT already there) — add order/anchor + NBT.
- Goals: modify `TamedFollowGoal.canUse` (skip when STAY/DEFEND), add `TamedStayGoal` (MOVE flag blocks stroll), add `TamedDefendGoal` (scan hostiles near anchor via `FactionManager.isHostileTo`, set target, else return to anchor).
- Register goals in Soldier/Scavenger/Medic entities.
- Client: `ClientModEvents` keybind registration + `RegisterKeyMappingsEvent`, `OrdersScreen`, tick handler via `MinecraftForge.EVENT_BUS`.

## Backlog (prioritized, from the 2026-11-27 post-overnight audit)
1. In-game verification of P0 fixes: join a world; confirm skins render (lazy pools), soldiers fire TACZ guns, medic navigates+heals, scavenger melee, mercenary recruit. (blocks everything else)
2. Voice/sound infrastructure: `ModSounds` registry + `sounds.json` scaffold, vanilla-sound fallback with gender pitch (`Variant`), `docs/VOICE-ASSETS.md` manifest (male/female soldier, male/female scavenger, female medic; ambient/hurt/death/combat/command).
3. Mercenary world presence: spawn egg or biome modifier + distinct escort/guard behavior.
4. Materials/progression: crafting chains for scrap_metal/weapon_part, scavenger-gathering integration, repair system.
5. Faction dynamics extras (patrol/defensive encounters), creative tab polish, Super Warfare branding note.

## Completed
- Overnight mission (see RECAP.md): repackage, P0 entity fixes, variants + owner skins, mercenary faction + recruitment, materials, docs. HEAD `2ee3741`, tests 168/168, builds green, boot-verified.
