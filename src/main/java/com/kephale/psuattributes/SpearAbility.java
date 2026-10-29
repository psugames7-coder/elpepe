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
            if (mod.config.spearSneakSave && player.isSneaking()) return false;
            PlayerState s = mod.state(player);
            int now = mod.now();
            if (s.spearCooling && now < s.spearCooldownUntil) return false;
            if (s.spearAirChain) return true;
            if (player.isOnGround()) {
                startCooldown(s, now);
                return true;
            }
            s.spearAirChain = true;
            Abilities.bar(player, "Air lunges", Formatting.AQUA);
            return true;
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: lunge check failed", t);
            return true;
        }
    }

    public static boolean savesHunger(ServerPlayerEntity player) {
        PsuAttributes mod = PsuAttributes.get();
        return mod != null && mod.config.spearSneakSave && player.isSneaking()
                && mod.has(player, AttributeType.SPEAR) && holdsLunge(player);
    }

    static boolean holdsLunge(ServerPlayerEntity player) {
        RegistryEntry<Enchantment> lunge = Abilities.enchant(player, LUNGE);
        if (lunge == null) return false;
        return EnchantmentHelper.getLevel(lunge, player.getMainHandStack()) > 0
                || EnchantmentHelper.getLevel(lunge, player.getOffHandStack()) > 0;
    }

    static void startCooldown(PlayerState s, int now) {
        int ticks = Math.max(0, PsuAttributes.get().config.spearCooldownSeconds) * 20;
        s.spearAirChain = false;
        s.spearCooldownUntil = now + ticks;
        s.spearCooling = ticks > 0;
    }

    static void tick(ServerPlayerEntity player, PlayerState s, int now) {
        if (s.spearAirChain && player.isOnGround()) {
            startCooldown(s, now);
            return;
        }
        if (s.spearCooling && now >= s.spearCooldownUntil) {
            s.spearCooling = false;
            Abilities.bar(player, "Lunge available", Formatting.GREEN);
        }
    }

    static String cooldownText(PlayerState s, int now) {
        if (!s.spearCooling || now >= s.spearCooldownUntil) return null;
        return "Lunge " + Abilities.secondsLeft(s.spearCooldownUntil, now) + "s";
    }
}
