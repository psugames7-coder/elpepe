package com.kephale.psuattributes;

import com.kephale.psuattributes.mixin.PlayerEntityAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class MaceAbility {

    private MaceAbility() {}

    private static final RegistryKey<Enchantment> WIND_BURST =
            RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("wind_burst"));
    private static final RegistryKey<DamageType> MACE_SMASH =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.ofVanilla("mace_smash"));
    private static final double FORCED_FALL = 2.0;
    private static final int RELAND_DELAY = 5;
    private static boolean loggedOverride;

    public record Prepared(ItemStack weapon, ServerPlayerEntity player, double savedFall) {
        public void restore() {
            if (player != null && !Double.isNaN(savedFall)) player.fallDistance = savedFall;
        }
    }

    private static ModConfig cfg() {
        return PsuAttributes.get().config;
    }

    static boolean isMace(ServerPlayerEntity player, ItemStack weapon) {
        return (weapon != null && weapon.isOf(Items.MACE)) || player.getMainHandStack().isOf(Items.MACE);
    }

    private static boolean cooling(PlayerState s, int now) {
        return s.maceCooling && now < s.maceCooldownUntil;
    }

    static float beforeDamage(ServerPlayerEntity player, PlayerState s, DamageSource source, ItemStack weapon,
                              float amount, int now) {
        boolean smash = source.isOf(MACE_SMASH);
        boolean mace = smash || isMace(player, weapon);
        s.maceHit = mace;
        s.maceSmash = smash;
        if (!mace) return amount;
        if (cooling(s, now)) {
            return ModConfig.MACE_DISABLED.equals(cfg().maceCooldownMode) ? Float.NaN : amount;
        }
        if (s.maceEmpowered || (smash && s.maceHits >= 2)) {
            return amount * (float) cfg().maceMultiplier;
        }
        return amount;
    }

    static void afterDamage(ServerPlayerEntity player, PlayerState s, boolean hit, int now) {
        if (!s.maceHit) return;
        boolean smash = s.maceSmash;
        s.maceHit = false;
        s.maceSmash = false;
        if (!hit) return;
        if (cooling(s, now)) {
            if (ModConfig.MACE_NO_WIND_BURST.equals(cfg().maceCooldownMode)) s.macePendingLevel = 0;
            return;
        }
        if (s.maceEmpowered) {
            s.macePendingLevel = 3;
            s.maceLandTick = -1;
            s.maceLastEmpoweredHit = now;
            Abilities.bar(player, "Chain hit", Formatting.GOLD);
            return;
        }
        if (!smash) {
            s.macePendingLevel = 0;
            return;
        }
        s.maceHits++;
        if (s.maceHits == 1) {
            s.macePendingLevel = 1;
            Abilities.bar(player, "First hit", Formatting.AQUA);
        } else if (s.maceHits == 2) {
            s.macePendingLevel = 0;
            Abilities.bar(player, "Second hit", Formatting.GRAY);
        } else {
            s.macePendingLevel = 3;
            s.maceEmpowered = true;
            s.maceLandTick = -1;
            s.maceLastEmpoweredHit = now;
            Abilities.bar(player, "Chain hit", Formatting.GOLD);
        }
    }

    public static Prepared prepare(DamageSource source, ItemStack weapon) {
        try {
            if (weapon == null || weapon.isEmpty()) return new Prepared(weapon, null, Double.NaN);
            if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return new Prepared(weapon, null, Double.NaN);
            PlayerState s = PsuAttributes.get().peek(player);
            if (s == null || s.macePendingLevel < 0) return new Prepared(weapon, null, Double.NaN);
            int level = s.macePendingLevel;
            s.macePendingLevel = -1;
            RegistryEntry<Enchantment> windBurst = Abilities.enchant(player, WIND_BURST);
            if (windBurst == null) return new Prepared(weapon, null, Double.NaN);

            ItemStack copy = weapon.copy();
            ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(
                    copy.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
            builder.remove(entry -> entry.matchesKey(WIND_BURST));
            if (level > 0) builder.set(windBurst, level);
            copy.set(DataComponentTypes.ENCHANTMENTS, builder.build());

            double saved = Double.NaN;
            if (level > 0) {
                if (player.fallDistance < FORCED_FALL) {
                    saved = player.fallDistance;
                    player.fallDistance = FORCED_FALL;
                }
                try {
                    PlayerEntityAccessor acc = (PlayerEntityAccessor) player;
                    acc.psuattributes$setIgnoreFallDamageFromCurrentExplosion(true);
                    acc.psuattributes$setCurrentExplosionImpactPos(new Vec3d(player.getX(), player.getY(), player.getZ()));
                } catch (Throwable t) {
                    PsuAttributes.LOGGER.warn("PsuAttributes: fall damage protection unavailable", t);
                }
            }
            if (!loggedOverride) {
                loggedOverride = true;
                PsuAttributes.LOGGER.info("PsuAttributes: mace wind burst override is active");
            }
            return new Prepared(copy, player, saved);
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: mace wind burst override failed", t);
            return new Prepared(weapon, null, Double.NaN);
        }
    }

    static void startCooldown(ServerPlayerEntity player, PlayerState s, int now) {
        s.maceEmpowered = false;
        s.maceHits = 0;
        s.macePendingLevel = -1;
        s.maceLandTick = -1;
        String mode = cfg().maceCooldownMode;
        int ticks = Math.max(0, cfg().maceCooldownSeconds) * 20;
        if (ModConfig.MACE_NO_COOLDOWN.equals(mode) || ticks <= 0) {
            s.maceCooldownUntil = 0;
            s.maceCooling = false;
            return;
        }
        s.maceCooldownUntil = now + ticks;
        s.maceCooling = true;
        if (ModConfig.MACE_DISABLED.equals(mode)) {
            player.getItemCooldownManager().set(new ItemStack(Items.MACE), ticks);
        }
    }

    static void onKill(ServerPlayerEntity player, PlayerState s) {
        if (!cfg().maceKillReset || !s.maceCooling) return;
        s.maceCooling = false;
        s.maceCooldownUntil = 0;
        clearItemCooldown(player);
        Abilities.bar(player, "Mace available", Formatting.GREEN);
    }

    static void clearItemCooldown(ServerPlayerEntity player) {
        ItemStack mace = new ItemStack(Items.MACE);
        player.getItemCooldownManager().remove(player.getItemCooldownManager().getGroup(mace));
    }

    static void tick(ServerPlayerEntity player, PlayerState s, int now) {
        if (s.maceEmpowered) {
            if (s.maceLandTick < 0 && player.isOnGround() && now - s.maceLastEmpoweredHit > RELAND_DELAY) {
                s.maceLandTick = now;
            }
            int grace = (int) Math.round(Math.max(0.0, cfg().maceLandGraceSeconds) * 20.0);
            if (s.maceLandTick >= 0 && now - s.maceLandTick >= grace) {
                startCooldown(player, s, now);
            }
            return;
        }
        if (s.maceCooling && now >= s.maceCooldownUntil) {
            s.maceCooling = false;
            Abilities.bar(player, "Mace available", Formatting.GREEN);
        }
    }

    static String cooldownText(PlayerState s, int now) {
        if (!s.maceCooling || now >= s.maceCooldownUntil) return null;
        return "Mace " + Abilities.secondsLeft(s.maceCooldownUntil, now) + "s";
    }
}
