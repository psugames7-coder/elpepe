package com.kephale.psuattributes;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Hud {

    private Hud() {}

    public enum Element {
        COMBAT("Combat timer"),
        FLIGHT("Flight time"),
        ABILITY("Ability messages"),
        COOLDOWN("Cooldowns");

        public final String label;

        Element(String label) {
            this.label = label;
        }
    }

    public enum Spot {
        ACTION_BAR("Above the hotbar"),
        BOSS_BAR("Top of the screen"),
        SUBTITLE("Center of the screen"),
        CHAT("Chat"),
        OFF("Hidden");

        public final String label;

        Spot(String label) {
            this.label = label;
        }
    }

    private static final int HOLD = 40;

    private static final class View {
        final EnumMap<Element, ServerBossBar> bars = new EnumMap<>(Element.class);
        int abilityUntil;
        int lastAction = -1000;
        int lastSubtitle = -1000;
    }

    private static final Map<UUID, View> views = new HashMap<>();

    private static View view(ServerPlayerEntity player) {
        return views.computeIfAbsent(player.getUuid(), k -> new View());
    }

    public static boolean allowed(Element element, Spot spot) {
        return spot != Spot.CHAT || element == Element.ABILITY;
    }

    public static Spot spot(ServerPlayerEntity player, Element element) {
        PlayerDataStore.Entry d = PsuAttributes.get().data.get(player);
        String raw = switch (element) {
            case COMBAT -> d.hudCombat;
            case FLIGHT -> d.hudFlight;
            case ABILITY -> d.hudAbility;
            case COOLDOWN -> d.hudCooldown;
        };
        try {
            Spot spot = Spot.valueOf(raw);
            return allowed(element, spot) ? spot : Spot.ACTION_BAR;
        } catch (Exception e) {
            return Spot.ACTION_BAR;
        }
    }

    public static void setSpot(ServerPlayerEntity player, Element element, Spot spot) {
        if (!allowed(element, spot)) return;
        PlayerDataStore.Entry d = PsuAttributes.get().data.get(player);
        switch (element) {
            case COMBAT -> d.hudCombat = spot.name();
            case FLIGHT -> d.hudFlight = spot.name();
            case ABILITY -> d.hudAbility = spot.name();
            case COOLDOWN -> d.hudCooldown = spot.name();
        }
        PsuAttributes.get().data.markDirty();
    }

    public static void ability(ServerPlayerEntity player, String message, Formatting color, boolean repeating) {
        int now = PsuAttributes.get().now();
        Text text = Text.literal(message).formatted(color);
        View v = view(player);
        switch (spot(player, Element.ABILITY)) {
            case ACTION_BAR -> {
                v.lastAction = now;
                player.sendMessage(text, true);
            }
            case SUBTITLE -> {
                v.lastSubtitle = now;
                subtitle(player, text, HOLD);
            }
            case BOSS_BAR -> {
                ServerBossBar bar = bar(v, Element.ABILITY);
                bar.setName(text);
                bar.setPercent(1.0F);
                bar.setColor(BossBar.Color.YELLOW);
                v.abilityUntil = now + HOLD;
                show(bar, player);
            }
            case CHAT -> {
                if (!repeating) player.sendMessage(text, false);
            }
            case OFF -> {
            }
        }
    }

    static void tick(ServerPlayerEntity player, int now) {
        PsuAttributes mod = PsuAttributes.get();
        View v = view(player);

        boolean combat = CombatLog.inCombat(player, now);
        boolean flight = mod.config.flightEnabled
                && (combat || (mod.config.flightDrainOutsideCombat && player.isGliding()));
        Spot combatSpot = spot(player, Element.COMBAT);
        Spot flightSpot = spot(player, Element.FLIGHT);
        Spot cooldownSpot = spot(player, Element.COOLDOWN);

        String combatText = null;
        if (combat) {
            int secs = (CombatLog.ticksLeft(player, now) + 19) / 20;
            combatText = "§c⚔ " + secs / 60 + ":" + String.format("%02d", secs % 60);
        }
        String flightText = flight ? Flight.hud(player) : null;
        String cooldownText = cooldowns(player, now);

        List<String> action = new ArrayList<>();
        List<String> sub = new ArrayList<>();
        if (combatText != null) {
            if (combatSpot == Spot.ACTION_BAR) action.add(combatText);
            else if (combatSpot == Spot.SUBTITLE) sub.add(combatText);
        }
        if (flightText != null) {
            if (flightSpot == Spot.ACTION_BAR) action.add(flightText);
            else if (flightSpot == Spot.SUBTITLE) sub.add(flightText);
        }
        if (cooldownText != null) {
            if (cooldownSpot == Spot.ACTION_BAR) action.add(cooldownText);
            else if (cooldownSpot == Spot.SUBTITLE) sub.add(cooldownText);
        }
        if (!action.isEmpty() && now - v.lastAction >= HOLD) {
            player.sendMessage(Text.literal(String.join("   ", action)), true);
        }
        if (!sub.isEmpty() && now - v.lastSubtitle >= HOLD) {
            subtitle(player, Text.literal(String.join("   ", sub)), 15);
        }

        float combatPct = 0.0F;
        if (combat) {
            combatPct = CombatLog.ticksLeft(player, now) / (float) Math.max(1, mod.config.combatSeconds * 20);
        }
        updateBar(v, Element.COMBAT, player, combat && combatSpot == Spot.BOSS_BAR,
                combatText, combatPct, BossBar.Color.RED);
        updateBar(v, Element.FLIGHT, player, flight && flightSpot == Spot.BOSS_BAR,
                flightText, flight ? Flight.fraction(player) : 0.0F,
                flight && Flight.locked(player) ? BossBar.Color.WHITE : BossBar.Color.BLUE);

        updateBar(v, Element.COOLDOWN, player, cooldownText != null && cooldownSpot == Spot.BOSS_BAR,
                cooldownText, cooldownFraction(player, now), BossBar.Color.PURPLE);

        ServerBossBar abilityBar = v.bars.get(Element.ABILITY);
        if (abilityBar != null && (now >= v.abilityUntil || spot(player, Element.ABILITY) != Spot.BOSS_BAR)) {
            abilityBar.clearPlayers();
        }
    }

    private static String cooldowns(ServerPlayerEntity player, int now) {
        PsuAttributes mod = PsuAttributes.get();
        PlayerState s = mod.peek(player);
        if (s == null) return null;
        List<String> parts = new ArrayList<>();
        if (showMace(mod, player)) add(parts, MaceAbility.cooldownText(s, now));
        if (showSpear(mod, player)) add(parts, SpearAbility.cooldownText(s, now));
        if (showSword(mod, player)) add(parts, SwordAbility.cooldownText(s, now));
        return parts.isEmpty() ? null : "§c" + String.join("  ", parts);
    }

    private static boolean showMace(PsuAttributes mod, ServerPlayerEntity player) {
        return mod.has(player, AttributeType.MACE) && player.getMainHandStack().isOf(Items.MACE)
                && !ModConfig.MACE_DISABLED.equals(mod.config.maceCooldownMode);
    }

    private static boolean showSpear(PsuAttributes mod, ServerPlayerEntity player) {
        return mod.has(player, AttributeType.SPEAR) && SpearAbility.holdsLunge(player);
    }

    private static boolean showSword(PsuAttributes mod, ServerPlayerEntity player) {
        return mod.has(player, AttributeType.SWORD) && player.getMainHandStack().isIn(ItemTags.SWORDS);
    }

    private static void add(List<String> parts, String text) {
        if (text != null) parts.add(text);
    }

    private static float cooldownFraction(ServerPlayerEntity player, int now) {
        PsuAttributes mod = PsuAttributes.get();
        PlayerState s = mod.peek(player);
        if (s == null) return 0.0F;
        float best = 0.0F;
        if (s.maceCooling && showMace(mod, player)) best = Math.max(best, frac(s.maceCooldownUntil, now, mod.config.maceCooldownSeconds));
        if (s.spearCooling && showSpear(mod, player)) best = Math.max(best, frac(s.spearCooldownUntil, now, mod.config.spearCooldownSeconds));
        if (s.swordCooling && showSword(mod, player)) best = Math.max(best, frac(s.swordCooldownUntil, now, mod.config.swordCooldownSeconds));
        return best;
    }

    private static float frac(int until, int now, int seconds) {
        return Math.max(0, until - now) / (float) Math.max(1, seconds * 20);
    }

    static void remove(UUID id) {
        View v = views.remove(id);
        if (v == null) return;
        for (ServerBossBar bar : v.bars.values()) bar.clearPlayers();
    }

    static void clearAll() {
        for (View v : views.values()) {
            for (ServerBossBar bar : v.bars.values()) bar.clearPlayers();
        }
        views.clear();
    }

    private static void updateBar(View v, Element element, ServerPlayerEntity player, boolean visible,
                                  String text, float percent, BossBar.Color color) {
        if (!visible || text == null) {
            ServerBossBar bar = v.bars.get(element);
            if (bar != null) bar.clearPlayers();
            return;
        }
        ServerBossBar bar = bar(v, element);
        bar.setName(Text.literal(text));
        bar.setPercent(Math.max(0.0F, Math.min(1.0F, percent)));
        bar.setColor(color);
        show(bar, player);
    }

    private static ServerBossBar bar(View v, Element element) {
        return v.bars.computeIfAbsent(element,
                k -> new ServerBossBar(Text.literal(""), BossBar.Color.WHITE, BossBar.Style.PROGRESS));
    }

    private static void show(ServerBossBar bar, ServerPlayerEntity player) {
        if (!bar.getPlayers().contains(player)) {
            bar.clearPlayers();
            bar.addPlayer(player);
        }
    }

    private static void subtitle(ServerPlayerEntity player, Text text, int stay) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, stay, 5));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(text));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));
    }
}
