package com.kephale.psuattributes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PsuAttributes implements ModInitializer {

    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("PsuAttributes");

    private static PsuAttributes instance;

    public ModConfig config;
    public PlayerDataStore data;
    public MinecraftServer server;
    private final Map<UUID, PlayerState> states = new HashMap<>();

    public static PsuAttributes get() {
        return instance;
    }

    @Override
    public void onInitialize() {
        instance = this;
        Path dir = FabricLoader.getInstance().getConfigDir();
        config = ModConfig.load(dir.resolve("psuattributes.json"));
        data = new PlayerDataStore(dir.resolve("psuattributes_players.json"));

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            CombatLog.stopping = false;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            CombatLog.stopping = true;
            data.save();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            states.clear();
            CombatLog.clearAll();
            Hud.clearAll();
            data.save();
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register(this::onDeath);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> data.get(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            try {
                CombatLog.onDisconnect(handler.player, now());
            } catch (Throwable t) {
                LOGGER.warn("PsuAttributes: combat log failed", t);
            }
            Hud.remove(handler.player.getUuid());
            data.saveIfDirty();
        });

        Commands.register(this);
        LOGGER.info("PsuAttributes loaded");
    }

    public int now() {
        return server == null ? 0 : server.getTicks();
    }

    public boolean has(ServerPlayerEntity player, AttributeType type) {
        return config.has(player.getUuid(), type);
    }

    public PlayerState state(ServerPlayerEntity player) {
        return states.computeIfAbsent(player.getUuid(), k -> new PlayerState());
    }

    public PlayerState peek(ServerPlayerEntity player) {
        return states.get(player.getUuid());
    }

    public void resetState(UUID id, AttributeType type) {
        PlayerState s = states.get(id);
        if (s == null) return;
        if (type == null || type == AttributeType.MACE) {
            s.resetMace();
            ServerPlayerEntity online = server == null ? null : server.getPlayerManager().getPlayer(id);
            if (online != null) MaceAbility.clearItemCooldown(online);
        }
        if (type == null || type == AttributeType.SPEAR) s.resetSpear();
        if (type == null || type == AttributeType.SWORD) s.resetSword();
    }

    private void tick(MinecraftServer s) {
        int now = s.getTicks();
        for (ServerPlayerEntity player : s.getPlayerManager().getPlayerList()) {
            try {
                Flight.tick(player, now);
                EnumSet<AttributeType> set = config.get(player.getUuid());
                if (!set.isEmpty()) {
                    PlayerState st = state(player);
                    if (set.contains(AttributeType.MACE)) MaceAbility.tick(player, st, now);
                    if (set.contains(AttributeType.SPEAR)) SpearAbility.tick(player, st, now);
                    if (set.contains(AttributeType.SWORD)) SwordAbility.tick(player, st, now);
                    st.macePendingLevel = -1;
                    st.maceAirborne = false;
                }
                if (now % 10 == 0) Hud.tick(player, now);
            } catch (Throwable t) {
                LOGGER.warn("PsuAttributes: tick failed for {}", player.getGameProfile().name(), t);
            }
        }
        if (now % 1200 == 0) data.saveIfDirty();
    }

    private void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        try {
            ServerPlayerEntity killer = null;
            if (source.getAttacker() instanceof ServerPlayerEntity p) killer = p;
            else if (player.getAttackingPlayer() instanceof ServerPlayerEntity p) killer = p;
            if (killer != null && killer != player) Flight.onKill(killer);

            CombatLog.clear(player.getUuid());

            PlayerState st = states.get(player.getUuid());
            if (st == null) return;
            int now = now();
            if (st.maceEmpowered) MaceAbility.startCooldown(player, st, now);
            st.maceHits = 0;
            if (st.spearAirChain) SpearAbility.startCooldown(player, st, now, "Died");
            if (st.swordActive) SwordAbility.end(player, st, now, "Died");
            else st.swordHits = 0;
        } catch (Throwable t) {
            LOGGER.warn("PsuAttributes: death handling failed", t);
        }
    }
}
