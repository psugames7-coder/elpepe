package com.kephale.psuattributes;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public final class MaceAbility {

    private MaceAbility() {}

    private static final RegistryKey<Enchantment> WIND_BURST =
            RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("wind_burst"));

    static float beforeDamage(ServerPlayerEntity player, PlayerState s, ItemStack weapon, float amount, int now) {
        s.maceSmash = false;
        if (!weapon.isOf(Items.MACE) || now < s.maceCooldownUntil) return amount;
        s.maceSmash = MaceItem.shouldDealAdditionalDamage(player);
        if (s.maceEmpowered || (s.maceHits >= 2 && s.maceSmash)) {
            return amount * (float) PsuAttributes.get().config.maceMultiplier;
        }
        return amount;
    }

    static void afterDamage(ServerPlayerEntity player, PlayerState s, ItemStack weapon, boolean hit, int now) {
        boolean smash = s.maceSmash;
        s.maceSmash = false;
        if (!hit || !smash || !weapon.isOf(Items.MACE) || now < s.maceCooldownUntil) return;
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

    public static ItemStack overrideWeapon(DamageSource source, ItemStack weapon) {
        try {
            if (weapon == null || weapon.isEmpty() || !weapon.isOf(Items.MACE)) return weapon;
            if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return weapon;
            PlayerState s = PsuAttributes.get().peek(player);
            if (s == null || s.macePendingLevel < 0) return weapon;
            int level = s.macePendingLevel;
            s.macePendingLevel = -1;
            RegistryEntry<Enchantment> windBurst = Abilities.enchant(player, WIND_BURST);
            if (windBurst == null) return weapon;
            ItemStack copy = weapon.copy();
            ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(
                    copy.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
            builder.remove(entry -> entry.matchesKey(WIND_BURST));
            if (level > 0) builder.set(windBurst, level);
            copy.set(DataComponentTypes.ENCHANTMENTS, builder.build());
            return copy;
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: mace wind burst override failed", t);
            return weapon;
        }
    }

    static void startCooldown(ServerPlayerEntity player, PlayerState s, int now) {
        int ticks = Math.max(0, PsuAttributes.get().config.maceCooldownSeconds) * 20;
        s.maceEmpowered = false;
        s.maceHits = 0;
        s.macePendingLevel = -1;
        s.maceCooldownUntil = now + ticks;
        s.maceCooling = ticks > 0;
        if (s.maceCooling) Abilities.bar(player, "Mace cooldown " + (ticks / 20) + "s", Formatting.RED);
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
            Abilities.bar(player, "Mace cooldown " + Abilities.secondsLeft(s.maceCooldownUntil, now) + "s", Formatting.RED);
        }
    }
}
