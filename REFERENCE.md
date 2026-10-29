# PsuAttributes reference

Server side Fabric mod for Minecraft 1.21.11. Vanilla clients join with nothing installed.
Updating from an older config version resets all settings to the current defaults once (grants and player data are kept).
Config and grants live in `config/psuattributes.json`.

## Commands (ops only)

`/psu` opens the menu: player list, per player attribute toggles, flight, cooldown reset and combat tag, plus a settings page.
In settings, left click goes up, right click goes down, shift moves 10 steps, toggles take one click.

| Command | What it does |
|---|---|
| `/psu give <player> <mace/spear/sword>` | Grants an attribute. A player can hold several. |
| `/psu take <player> <mace/spear/sword>` | Removes it. |
| `/psu reset <player>` | Clears cooldowns, combos and chains. |
| `/psu flight <player> [seconds]` | Shows or sets someone's flight time. |
| `/psu list` | Who has what. |
| `/psu settings` | Shows all tuning values. |
| `/psu config default` | Puts every setting back to default. Attribute grants and player data are kept. |
| `/psu set <setting> [value]` | macecooldown, macemultiplier, spearcooldown, swordhits, swordmultiplier, swordcooldown, macecooldownmode, macelandgrace, macekillreset, spearsneaksave, combatlog, combatseconds, logouthearts, flight, flightoutsidecombat, flightstart, flightmax, flightunlock, flightdamagehearts, flightperdamage, flightperkill |

## Mace

A hit counts toward the chain when the server registers a vanilla mace smash hit (the mace_smash damage), so attribute swap smashes count too.
Hit 1 bursts Wind Burst I, hit 2 has no Wind Burst, hit 3 bursts Wind Burst III with x1.5 damage.
From hit 3 on, every mace hit bursts Wind Burst III with x1.5, rising or falling, until you land.
The cooldown starts 1 second after you touch the ground (`macelandgrace`). An empowered hit during that second keeps the chain going.
`macecooldownmode`: `disabled` (default, mace hits do nothing during the cooldown, maces show the cooldown sweep),
`nowindburst` (normal mace damage, never any Wind Burst), `vanilla` (plain vanilla mace) or `nocooldown` (no cooldown, the chain restarts right away).
A player kill clears the mace cooldown (`macekillreset`).
Non smash hits never count toward the chain. Messages: First hit, Second hit, Chain hit.
The real item is never edited. The Wind Burst level is swapped on a copy that only the enchant effect sees.

## Spear

Applies to spears with Lunge. Ground lunge: one lunge, then 15s cooldown. Air lunge: unlimited until you land, then 15s cooldown.
During cooldown the jab still works, only the lunge is skipped.
`spearsneaksave` (default on): jabbing while sneaking never lunges, never costs hunger or saturation and never starts a cooldown.

## Sword

Counts landed sword hits on anyone (sword as weapon or in main hand). Sweep hits on bystanders do not count toward the combo.
Killing someone does not reset it, you keep counting on the next person.
Getting hit by anyone (player or mob, melee or projectile) resets the count.
After 5 hits, every sword hit does x1.2 until you get hit, then 30s cooldown. Dying also ends it.

## Combat log

Any PvP hit (melee or projectile, blocked or not) tags both players for 2 minutes. Every hit refreshes it.
Logging out while tagged takes 8 hearts straight off, as the disconnect happens. Armor, protection and resistance
do nothing against it. Absorption hearts go first, then real health. If that is lethal they die, and totems do not save them.
Server shutdown never triggers it. Dying clears the tag. Creative and spectator players are skipped.

## Flight time

Applies to everyone. New players start with 20s, cap 25s.
In combat, gliding drains 1s per second. Out of combat elytra flight is free
(set `flightoutsidecombat` on to make it drain everywhere).
Gains: +2.5s for every 10 hearts of real damage dealt to players (after armor, partial damage carries over),
+5s per player kill.
Hitting 0 locks flight until you are back to at least 20s. While locked you cannot start gliding in combat,
and if you run out mid flight your elytra closes.
Flight time is saved to `config/psuattributes_players.json`, so it survives restarts.

## HUD

`/hud` or `/settings` (any player) opens a menu where each player picks where each HUD shows:
combat timer, flight time, ability messages and cooldowns (separately). Options: above the hotbar, top of the screen (boss bar),
center of the screen (subtitle), chat (ability messages only) or hidden. Saved per player.
Defaults: ability messages in chat, everything else above the hotbar. In `disabled` mace mode the mace cooldown is not shown in the HUD, the item cooldown sweep already shows it.
Things sharing the hotbar or the subtitle spot are joined on one line; an ability message there takes priority for 2 seconds.

## Tech notes

Damage changes and hit counting: `LivingEntityDamageMixin` wraps `LivingEntity.damage`.
Wind Burst swap: `EnchantmentHelperMixin` wraps both `onTargetDamaged` overloads.
Lunge gate: `EnchantmentHelperMixin` cancels `EnchantmentHelper.onAttack`, which runs the
`post_piercing_attack` effects (Lunge is the only vanilla enchant using it).
Sweep filter: `PlayerSweepMixin` wraps `PlayerEntity.doSweepingAttack`.
Glide gate: `PlayerGlideMixin` blocks `PlayerEntity.checkGliding`, and the tick closes elytras mid flight.
Combat log damage runs on Fabric's DISCONNECT event, which fires before the player is removed and saved.
All mixins are non required. If one fails to apply, only that feature dies. Watch for `PsuAttributes:` log lines.
Enchant lookups go through registry keys built from ids (`wind_burst`, `lunge`) instead of constants.
Cooldowns are in memory and survive relogs but not restarts.
