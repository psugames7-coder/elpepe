package com.kephale.psuattributes;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public final class SwordAbility {

    private SwordAbility() {}

    static float beforeDamage(PlayerState s, ItemStack weapon, float amount) {
        if (!s.swordActive || !weapon.isIn(ItemTags.SWORDS)) return amount;
        return amount * (float) PsuAttributes.get().config.swordMultiplier;
    }

    static void afterHit(ServerPlayerEntity player, PlayerState s, LivingEntity target, ItemStack weapon, boolean hit, int now) {
        if (!hit || target == player || !weapon.isIn(ItemTags.SWORDS) || s.swordActive || now < s.swordCooldownUntil) return;
        s.swordHits++;
        ModConfig config = PsuAttributes.get().config;
        int need = Math.max(1, config.swordComboHits);
        if (s.swordHits >= need) {
            s.swordActive = true;
            Abilities.bar(player, "Combo buff: x" + Abilities.fmt(config.swordMultiplier) + " until you get hit", Formatting.GOLD);
        } else {
            Abilities.bar(player, "Combo " + s.swordHits + "/" + need, Formatting.YELLOW);
        }
    }

    static void onDamaged(ServerPlayerEntity victim, PlayerState s, Entity attacker, boolean hit, int now) {
        if (!hit || !(attacker instanceof LivingEntity) || attacker == victim) return;
        if (s.swordActive) {
            end(victim, s, now, "Got hit");
        } else if (s.swordHits > 0) {
            s.swordHits = 0;
            Abilities.bar(victim, "Combo broken", Formatting.GRAY);
        }
    }

    static void end(ServerPlayerEntity player, PlayerState s, int now, String reason) {
        int ticks = Math.max(0, PsuAttributes.get().config.swordCooldownSeconds) * 20;
        s.swordActive = false;
        s.swordHits = 0;
        s.swordCooldownUntil = now + ticks;
        s.swordCooling = ticks > 0;
        Abilities.bar(player, reason + (s.swordCooling ? ". Combo cooldown " + (ticks / 20) + "s" : ""), Formatting.RED);
    }

    static void tick(ServerPlayerEntity player, PlayerState s, int now) {
        if (!s.swordCooling) return;
        if (now >= s.swordCooldownUntil) {
            s.swordCooling = false;
            Abilities.bar(player, "Combo ready", Formatting.GREEN);
        } else if (now % 10 == 0 && player.getMainHandStack().isIn(ItemTags.SWORDS)) {
            Abilities.bar(player, "Combo cooldown " + Abilities.secondsLeft(s.swordCooldownUntil, now) + "s", Formatting.RED);
        }
    }
}
