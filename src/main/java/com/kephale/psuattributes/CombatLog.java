package com.kephale.psuattributes;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatLog {

    private CombatLog() {}

    private static final Map<UUID, Integer> until = new HashMap<>();
    public static boolean stopping;

    static void onPvpHit(ServerPlayerEntity victim, ServerPlayerEntity attacker, int now) {
        int ticks = Math.max(0, PsuAttributes.get().config.combatSeconds) * 20;
        if (ticks <= 0) return;
        until.put(victim.getUuid(), now + ticks);
        until.put(attacker.getUuid(), now + ticks);
    }

    public static boolean inCombat(ServerPlayerEntity player, int now) {
        Integer u = until.get(player.getUuid());
        return u != null && u > now;
    }

    static int ticksLeft(ServerPlayerEntity player, int now) {
        Integer u = until.get(player.getUuid());
        return u == null ? 0 : Math.max(0, u - now);
    }

    static void clear(UUID id) {
        until.remove(id);
    }

    static void clearAll() {
        until.clear();
    }

    static void onDisconnect(ServerPlayerEntity player, int now) {
        Integer u = until.remove(player.getUuid());
        ModConfig config = PsuAttributes.get().config;
        if (stopping || !config.combatLogEnabled || u == null || u <= now) return;
        if (player.isDead() || player.isCreative() || player.isSpectator()) return;

        float damage = (float) Math.max(0.0, config.logoutHearts * 2.0);
        float absorption = player.getAbsorptionAmount();
        float fromAbsorption = Math.min(absorption, damage);
        if (fromAbsorption > 0.0F) player.setAbsorptionAmount(absorption - fromAbsorption);
        float rest = damage - fromAbsorption;
        float health = player.getHealth();
        if (rest > 0.0F) {
            if (rest < health) {
                player.setHealth(health - rest);
            } else {
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                player.damage(world, player.getDamageSources().genericKill(), Float.MAX_VALUE);
            }
        }
        PsuAttributes.LOGGER.info("PsuAttributes: {} logged out in combat, took {} hearts",
                player.getGameProfile().name(), Abilities.fmt(config.logoutHearts));
    }
}
