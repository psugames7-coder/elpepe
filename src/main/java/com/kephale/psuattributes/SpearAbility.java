package com.kephale.psuattributes;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public final class SpearAbility {

    private SpearAbility() {}

    private static final RegistryKey<Enchantment> LUNGE =
            RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("lunge"));

    public static boolean allowLunge(Entity attacker) {
        try {
            if (!(attacker instanceof ServerPlayerEntity player)) return true;
            PsuAttributes mod = PsuAttributes.get();
            if (mod == null || !mod.has(player, AttributeType.SPEAR)) return true;
            if (!holdsLunge(player)) return true;
            if (player.hasVehicle() || player.isGliding() || player.isTouchingWater()) return true;
            PlayerState s = mod.state(player);
            int now = mod.now();
            if (now < s.spearCooldownUntil) {
                Abilities.bar(player, "Lunge cooldown " + Abilities.secondsLeft(s.spearCooldownUntil, now) + "s", Formatting.RED);
                return false;
            }
            if (s.spearAirChain) return true;
            if (player.isOnGround()) {
                startCooldown(player, s, now, "Lunge used");
                return true;
            }
            s.spearAirChain = true;
            Abilities.bar(player, "Air lunge: unlimited until you land", Formatting.AQUA);
            return true;
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: lunge check failed", t);
            return true;
        }
    }

    private static boolean holdsLunge(ServerPlayerEntity player) {
        RegistryEntry<Enchantment> lunge = Abilities.enchant(player, LUNGE);
        if (lunge == null) return false;
        return EnchantmentHelper.getLevel(lunge, player.getMainHandStack()) > 0
                || EnchantmentHelper.getLevel(lunge, player.getOffHandStack()) > 0;
    }

    static void startCooldown(ServerPlayerEntity player, PlayerState s, int now, String reason) {
        int ticks = Math.max(0, PsuAttributes.get().config.spearCooldownSeconds) * 20;
        s.spearAirChain = false;
        s.spearCooldownUntil = now + ticks;
        s.spearCooling = ticks > 0;
        if (s.spearCooling) Abilities.bar(player, reason + ". Lunge cooldown " + (ticks / 20) + "s", Formatting.RED);
    }

    static void tick(ServerPlayerEntity player, PlayerState s, int now) {
        if (s.spearAirChain && player.isOnGround()) {
            startCooldown(player, s, now, "Landed");
            return;
        }
        if (!s.spearCooling) return;
        if (now >= s.spearCooldownUntil) {
            s.spearCooling = false;
            Abilities.bar(player, "Lunge ready", Formatting.GREEN);
        } else if (now % 10 == 0 && holdsLunge(player)) {
            Abilities.bar(player, "Lunge cooldown " + Abilities.secondsLeft(s.spearCooldownUntil, now) + "s", Formatting.RED);
        }
    }
}
