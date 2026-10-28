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
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        player.damage(world, player.getDamageSources().generic(), (float) config.logoutDamage);
        PsuAttributes.LOGGER.info("PsuAttributes: {} logged out in combat, dealt {} damage",
                player.getGameProfile().name(), config.logoutDamage);
    }
}
