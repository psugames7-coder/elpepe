package com.kephale.psuattributes;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public final class Abilities {

    private Abilities() {}

    public static int sweepDepth;

    private static ItemStack weapon(DamageSource source) {
        ItemStack weapon = source.getWeaponStack();
        return weapon == null ? ItemStack.EMPTY : weapon;
    }

    public static float beforeDamage(LivingEntity target, DamageSource source, float amount) {
        try {
            if (sweepDepth > 0) return amount;
            if (!(source.getAttacker() instanceof ServerPlayerEntity player) || source.getSource() != player) return amount;
            ItemStack weapon = weapon(source);
            PsuAttributes mod = PsuAttributes.get();
            int now = mod.now();
            float out = amount;
            if (mod.has(player, AttributeType.MACE)) out = MaceAbility.beforeDamage(player, mod.state(player), target, source, weapon, out, now);
            if (Float.isNaN(out)) return out;
            if (mod.has(player, AttributeType.SWORD)) out = SwordAbility.beforeDamage(player, mod.state(player), weapon, out);
            return out;
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: beforeDamage failed", t);
            return amount;
        }
    }

    public static void afterDamage(LivingEntity target, DamageSource source, boolean hit, float dealt) {
        try {
            PsuAttributes mod = PsuAttributes.get();
            int now = mod.now();
            Entity attackerEntity = source.getAttacker();

            if (target instanceof ServerPlayerEntity victim) {
                if (attackerEntity instanceof ServerPlayerEntity attacker && attacker != victim) {
                    CombatLog.onPvpHit(victim, attacker, now);
                    if (hit) Flight.onDamageDealt(attacker, dealt);
                }
                if (mod.has(victim, AttributeType.SWORD)) {
                    SwordAbility.onDamaged(victim, mod.state(victim), attackerEntity, hit, now);
                }
            }

            if (sweepDepth > 0) return;
            if (!(attackerEntity instanceof ServerPlayerEntity player) || source.getSource() != player) return;
            ItemStack weapon = weapon(source);
            if (mod.has(player, AttributeType.MACE)) MaceAbility.afterDamage(player, mod.state(player), hit, now);
            if (mod.has(player, AttributeType.SWORD)) SwordAbility.afterHit(player, mod.state(player), target, weapon, hit, now);
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: afterDamage failed", t);
        }
    }

    public static RegistryEntry<Enchantment> enchant(Entity entity, RegistryKey<Enchantment> key) {
        return entity.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key).orElse(null);
    }

    public static void bar(ServerPlayerEntity player, String message, Formatting color) {
        Hud.ability(player, message, color, false);
    }

    public static void ready(ServerPlayerEntity player, String message) {
        Hud.ability(player, message, Formatting.GREEN, false);
        try {
            Registries.SOUND_EVENT.getEntry(Identifier.ofVanilla("block.anvil.land")).ifPresent(sound ->
                    player.networkHandler.sendPacket(new PlaySoundS2CPacket(sound, SoundCategory.PLAYERS,
                            player.getX(), player.getY(), player.getZ(), 0.8F, 0.5F, player.getRandom().nextLong())));
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: ready sound failed", t);
        }
    }

    public static void barRepeat(ServerPlayerEntity player, String message, Formatting color) {
        Hud.ability(player, message, color, true);
    }

    public static final int SHOW_TICKS = 40;

    public static int secondsLeft(int until, int now) {
        return Math.max(0, (until - now + 19) / 20);
    }

    public static String fmt(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }
}
