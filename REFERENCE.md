# PsuAttributes reference

Server side Fabric mod for Minecraft 1.21.11. Vanilla clients join with nothing installed.
Config and grants live in `config/psuattributes.json`.

## Commands (ops only)

| Command | What it does |
|---|---|
| `/attributes give <player> <mace/spear/sword>` | Grants an attribute. A player can hold several. |
| `/attributes take <player> <mace/spear/sword>` | Removes it. |
| `/attributes reset <player>` | Clears cooldowns, combos and chains. |
| `/attributes flight <player> [seconds]` | Shows or sets someone's flight time. |
| `/attributes list` | Who has what. |
| `/attributes settings` | Shows all tuning values. |
| `/attributes set <setting> [value]` | macecooldown, macemultiplier, spearcooldown, swordhits, swordmultiplier, swordcooldown, combatlog, combatseconds, logoutdamage, flight, flightoutsidecombat, flightstart, flightmax, flightunlock, flightdamagehearts, flightperdamage, flightperkill |

## Mace

Only smash hits count (the hits where vanilla mace smash applies: falling more than 1.5 blocks, not gliding).
Hit 1 rolls Wind Burst I, hit 2 rolls no Wind Burst, hit 3 rolls Wind Burst III and x1.5 damage.
From hit 3 on, every hit keeps Wind Burst III and x1.5 until you touch the ground, then 20s cooldown.
During cooldown the mace is a normal vanilla mace with whatever enchants it really has.
The hit count does not reset if you land after hit 1 or 2; only the empowered state ends on landing.
The real item is never edited. The Wind Burst level is swapped on a copy that only the enchant effect sees.
The multiplier applies to the final damage, smash bonus included.

## Spear

Applies to spears with Lunge. The attribute gates the vanilla Lunge effect.
Starting a lunge on the ground: that one lunge goes through, then 15s cooldown.
Starting a lunge in the air: unlimited lunges until you land, then 15s cooldown.
During cooldown the jab still works, only the lunge (impulse, hunger, durability, sound) is skipped.
Vanilla rules still apply on top: no lunge in water, while gliding or while riding.

## Sword

Counts landed sword hits on anyone. Sweep hits on bystanders do not count toward the combo.
Killing someone does not reset it, you keep counting on the next person.
Getting hit by anyone (player or mob, melee or projectile) resets the count.
After 5 hits, every sword hit does x1.2 until you get hit, then 30s cooldown. Dying also ends it.

## Combat log

Any PvP hit (melee or projectile, blocked or not) tags both players for 2 minutes. Every hit refreshes it.
Logging out while tagged deals 6 hearts of generic damage as the disconnect happens, same as the OG plugin:
armor reduces it, totems pop, and it can kill. Server shutdown never triggers it. Dying clears the tag.
Creative and spectator players are skipped.

## Flight time

Applies to everyone. New players start with 30s.
In combat, gliding drains 1s per second. Out of combat elytra flight is free
(set `flightoutsidecombat` on to make it drain everywhere).
Gains: +5s for every 5 hearts of real damage dealt to players (after armor, partial damage carries over),
+10s per player kill. Cap 35s.
Hitting 0 locks flight until you are back to at least 20s. While locked you cannot start gliding in combat,
and if you run out mid flight your elytra closes.
Flight time is saved to `config/psuattributes_players.json`, so it survives restarts.

## HUD

While tagged, the action bar shows the combat timer and flight time. Ability messages take priority for 2 seconds.

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
