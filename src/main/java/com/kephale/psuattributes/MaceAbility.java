package com.kephale.psuattributes;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.damage.DamageSource;
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
    private static final double FORCED_FALL = 2.0;
    private static boolean loggedOverride;

    public record Prepared(ItemStack weapon, ServerPlayerEntity player, double savedFall) {
        public void restore() {
            if (player != null && !Double.isNaN(savedFall)) player.fallDistance = savedFall;
        }
    }

    private static String mode() {
        return PsuAttributes.get().config.maceCooldownMode;
    }

    static float beforeDamage(ServerPlayerEntity player, PlayerState s, ItemStack weapon, float amount, int now) {
        s.maceAirborne = false;
        if (!weapon.isOf(Items.MACE)) return amount;
        if (now < s.maceCooldownUntil) {
            if (ModConfig.MACE_DISABLED.equals(mode())) {
                Abilities.barRepeat(player, "Mace disabled " + Abilities.secondsLeft(s.maceCooldownUntil, now) + "s", Formatting.RED);
                return Float.NaN;
            }
            return amount;
        }
        s.maceAirborne = !player.isOnGround();
        if (s.maceAirborne && (s.maceEmpowered || s.maceHits >= 2)) {
            return amount * (float) PsuAttributes.get().config.maceMultiplier;
        }
        return amount;
    }

    static void afterDamage(ServerPlayerEntity player, PlayerState s, ItemStack weapon, boolean hit, int now) {
        boolean airborne = s.maceAirborne;
        s.maceAirborne = false;
        if (!hit || !weapon.isOf(Items.MACE)) return;
        if (now < s.maceCooldownUntil) {
            if (ModConfig.MACE_NO_WIND_BURST.equals(mode())) s.macePendingLevel = 0;
            return;
        }
        if (!airborne) {
            s.macePendingLevel = 0;
            return;
        }
        s.maceHits++;
        if (s.maceHits == 1) {
            s.macePendingLevel = 1;
            Abilities.bar(player, "Mace hit 1: Wind Burst I", Formatting.AQUA);
        } else if (s.maceHits == 2) {
            s.macePendingLevel = 0;
            Abilities.bar(player, "Mace hit 2: no Wind Burst. Next hit empowers", Formatting.GRAY);
        } else {
            s.macePendingLevel = 3;
            if (!s.maceEmpowered) {
                s.maceEmpowered = true;
                Abilities.bar(player, "Mace empowered: Wind Burst III and x"
                        + Abilities.fmt(PsuAttributes.get().config.maceMultiplier) + " until you land", Formatting.GOLD);
            }
        }
    }

    public static Prepared prepare(DamageSource source, ItemStack weapon) {
        try {
            if (weapon == null || weapon.isEmpty() || !weapon.isOf(Items.MACE)) return new Prepared(weapon, null, Double.NaN);
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
                player.setIgnoreFallDamageFromCurrentExplosion(true);
                player.currentExplosionImpactPos = new Vec3d(player.getX(), player.getY(), player.getZ());
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
        int ticks = Math.max(0, PsuAttributes.get().config.maceCooldownSeconds) * 20;
        s.maceEmpowered = false;
        s.maceHits = 0;
        s.macePendingLevel = -1;
        s.maceCooldownUntil = now + ticks;
        s.maceCooling = ticks > 0;
        if (!s.maceCooling) return;
        String mode = mode();
        if (ModConfig.MACE_DISABLED.equals(mode)) {
            player.getItemCooldownManager().set(new ItemStack(Items.MACE), ticks);
            Abilities.bar(player, "Mace disabled for " + (ticks / 20) + "s", Formatting.RED);
        } else if (ModConfig.MACE_VANILLA.equals(mode)) {
            Abilities.bar(player, "Mace cooldown " + (ticks / 20) + "s: normal mace", Formatting.RED);
        } else {
            Abilities.bar(player, "Mace cooldown " + (ticks / 20) + "s: no Wind Burst", Formatting.RED);
        }
    }

    static void clearItemCooldown(ServerPlayerEntity player) {
        ItemStack mace = new ItemStack(Items.MACE);
        player.getItemCooldownManager().remove(player.getItemCooldownManager().getGroup(mace));
    }

    static void tick(ServerPlayerEntity player, PlayerState s, int now) {
        if (s.maceEmpowered && player.isOnGround()) {
            startCooldown(player, s, now);
            return;
        }
        if (!s.maceCooling) return;
        if (now >= s.maceCooldownUntil) {
            s.maceCooling = false;
            Abilities.bar(player, "Mace ready", Formatting.GREEN);
        } else if (now % 10 == 0 && player.getMainHandStack().isOf(Items.MACE)) {
            Abilities.barRepeat(player, "Mace cooldown " + Abilities.secondsLeft(s.maceCooldownUntil, now) + "s", Formatting.RED);
        }
    }
}
