package com.kephale.psuattributes;

import com.kephale.psuattributes.mixin.LivingEntityInvoker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public final class Flight {

    private Flight() {}

    private static boolean drains(ServerPlayerEntity player, int now) {
        return PsuAttributes.get().config.flightDrainOutsideCombat || CombatLog.inCombat(player, now);
    }

    public static boolean canStartGliding(ServerPlayerEntity player) {
        try {
            PsuAttributes mod = PsuAttributes.get();
            if (mod == null || !mod.config.flightEnabled) return true;
            if (player.isCreative() || player.isSpectator()) return true;
            int now = mod.now();
            if (!drains(player, now)) return true;
            PlayerDataStore.Entry d = mod.data.get(player);
            if (d.flightLocked || d.flightTicks <= 0) {
                Abilities.barRepeat(player, "✈ locked " + (d.flightTicks / 20) + "/" + mod.config.flightUnlockSeconds + "s", Formatting.RED);
                return false;
            }
            return true;
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: glide check failed", t);
            return true;
        }
    }

    static void tick(ServerPlayerEntity player, int now) {
        PsuAttributes mod = PsuAttributes.get();
        if (!mod.config.flightEnabled || !player.isGliding() || !drains(player, now)) return;
        if (player.isCreative() || player.isSpectator()) return;
        PlayerDataStore.Entry d = mod.data.get(player);
        if (d.flightLocked || d.flightTicks <= 0) {
            stopGliding(player);
            return;
        }
        d.flightTicks--;
        mod.data.markDirty();
        if (d.flightTicks <= 0) {
            d.flightTicks = 0;
            d.flightLocked = true;
            stopGliding(player);
            Abilities.bar(player, "Out of flight", Formatting.RED);
        }
    }

    private static void stopGliding(ServerPlayerEntity player) {
        try {
            ((LivingEntityInvoker) player).psuattributes$stopGliding();
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: could not stop gliding", t);
        }
    }

    static void add(ServerPlayerEntity player, int ticks) {
        PsuAttributes mod = PsuAttributes.get();
        if (!mod.config.flightEnabled || ticks <= 0) return;
        PlayerDataStore.Entry d = mod.data.get(player);
        int max = Math.max(0, mod.config.flightMaxSeconds) * 20;
        d.flightTicks = Math.min(max, d.flightTicks + ticks);
        if (d.flightLocked && d.flightTicks >= mod.config.flightUnlockSeconds * 20) {
            d.flightLocked = false;
            Abilities.bar(player, "Flight unlocked", Formatting.GREEN);
        }
        mod.data.markDirty();
    }

    static void set(ServerPlayerEntity player, int seconds) {
        PsuAttributes mod = PsuAttributes.get();
        PlayerDataStore.Entry d = mod.data.get(player);
        d.flightTicks = Math.max(0, seconds) * 20;
        d.flightLocked = d.flightTicks <= 0;
        d.damageBank = 0;
        mod.data.markDirty();
    }

    static void onDamageDealt(ServerPlayerEntity attacker, float dealt) {
        PsuAttributes mod = PsuAttributes.get();
        if (!mod.config.flightEnabled || dealt <= 0) return;
        double step = Math.max(0.5, mod.config.flightDamageHearts * 2.0);
        PlayerDataStore.Entry d = mod.data.get(attacker);
        d.damageBank += dealt;
        int rewards = 0;
        while (d.damageBank >= step) {
            d.damageBank -= step;
            rewards++;
        }
        mod.data.markDirty();
        if (rewards > 0) add(attacker, (int) Math.round(rewards * Math.max(0.0, mod.config.flightPerDamageSeconds) * 20.0));
    }

    static void onKill(ServerPlayerEntity killer) {
        add(killer, Math.max(0, PsuAttributes.get().config.flightPerKillSeconds) * 20);
    }

    static float fraction(ServerPlayerEntity player) {
        PsuAttributes mod = PsuAttributes.get();
        int max = Math.max(1, mod.config.flightMaxSeconds * 20);
        return mod.data.get(player).flightTicks / (float) max;
    }

    static boolean locked(ServerPlayerEntity player) {
        return PsuAttributes.get().data.get(player).flightLocked;
    }

    static String hud(ServerPlayerEntity player) {
        PsuAttributes mod = PsuAttributes.get();
        PlayerDataStore.Entry d = mod.data.get(player);
        int secs = (d.flightTicks + 19) / 20;
        if (d.flightLocked) return "§8✈ " + secs + "/" + mod.config.flightUnlockSeconds + "s";
        return "§b✈ " + secs + "s";
    }
}
