package com.kephale.psuattributes;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class Menu {

    private Menu() {}

    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PAGE = 47;
    private static final int SLOT_SETTINGS = 49;
    private static final int SLOT_NEXT = 53;

    private static final int SLOT_HEAD = 4;
    private static final int SLOT_MACE = 20;
    private static final int SLOT_SPEAR = 22;
    private static final int SLOT_SWORD = 24;
    private static final int SLOT_FLIGHT = 38;
    private static final int SLOT_RESET = 40;
    private static final int SLOT_COMBAT = 42;

    private enum View { LIST, PLAYER, SETTINGS }

    private record Setting(int slot, Item icon, String label, String unit, double step, double min, double max,
                           boolean bool, boolean whole, DoubleSupplier get, DoubleConsumer set, String desc,
                           String[] options) {

        Setting(int slot, Item icon, String label, String unit, double step, double min, double max,
                boolean bool, boolean whole, DoubleSupplier get, DoubleConsumer set, String desc) {
            this(slot, icon, label, unit, step, min, max, bool, whole, get, set, desc, null);
        }
    }

    public static void open(ServerPlayerEntity admin) {
        SimpleInventory inv = new SimpleInventory(54);
        admin.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, ignored) -> new Handler(syncId, playerInv, inv),
                Text.literal("PsuAttributes")));
    }

    private static List<Setting> settings() {
        ModConfig c = PsuAttributes.get().config;
        List<Setting> list = new ArrayList<>();
        list.add(new Setting(10, Items.MACE, "Mace cooldown", "s", 1, 0, 3600, false, true,
                () -> c.maceCooldownSeconds, v -> c.maceCooldownSeconds = (int) v, "After landing from the empowered state"));
        list.add(new Setting(11, Items.HEAVY_CORE, "Mace multiplier", "x", 0.05, 0, 100, false, false,
                () -> c.maceMultiplier, v -> c.maceMultiplier = v, "Damage from hit 3 until landing"));
        list.add(new Setting(19, Items.WIND_CHARGE, "Mace during cooldown", "", 1, 0, 2, false, true,
                () -> Math.max(0, ModConfig.maceModeIndex(c.maceCooldownMode)),
                v -> c.maceCooldownMode = ModConfig.MACE_MODES[(int) v],
                "What the mace does during its cooldown", ModConfig.MACE_MODES));
        list.add(new Setting(20, Items.SLIME_BALL, "Mace land grace", "s", 0.25, 0, 10, false, false,
                () -> c.maceLandGraceSeconds, v -> c.maceLandGraceSeconds = v, "Delay after landing before the cooldown"));
        list.add(new Setting(21, Items.SKELETON_SKULL, "Mace kill reset", "", 1, 0, 1, true, true,
                () -> c.maceKillReset ? 1 : 0, v -> c.maceKillReset = v > 0, "A player kill clears the mace cooldown"));
        list.add(new Setting(23, Items.LEATHER_BOOTS, "Spear sneak save", "", 1, 0, 1, true, true,
                () -> c.spearSneakSave ? 1 : 0, v -> c.spearSneakSave = v > 0, "Sneaking jabs never lunge or cost hunger"));
        list.add(new Setting(12, spear(), "Spear cooldown", "s", 1, 0, 3600, false, true,
                () -> c.spearCooldownSeconds, v -> c.spearCooldownSeconds = (int) v, "After a ground lunge or an air chain"));
        list.add(new Setting(14, Items.IRON_SWORD, "Sword combo hits", "", 1, 1, 100, false, true,
                () -> c.swordComboHits, v -> c.swordComboHits = (int) v, "Hits without getting hit"));
        list.add(new Setting(15, Items.DIAMOND_SWORD, "Sword multiplier", "x", 0.05, 0, 100, false, false,
                () -> c.swordMultiplier, v -> c.swordMultiplier = v, "Damage while the combo buff is on"));
        list.add(new Setting(16, Items.NETHERITE_SWORD, "Sword cooldown", "s", 1, 0, 3600, false, true,
                () -> c.swordCooldownSeconds, v -> c.swordCooldownSeconds = (int) v, "After the buff ends"));

        list.add(new Setting(28, Items.BARRIER, "Combat log", "", 1, 0, 1, true, true,
                () -> c.combatLogEnabled ? 1 : 0, v -> c.combatLogEnabled = v > 0, "Logout damage while tagged"));
        list.add(new Setting(29, Items.CLOCK, "Combat timer", "s", 1, 0, 3600, false, true,
                () -> c.combatSeconds, v -> c.combatSeconds = (int) v, "Tag length after a PvP hit"));
        list.add(new Setting(30, Items.REDSTONE, "Logout damage", " hearts", 0.5, 0, 100, false, false,
                () -> c.logoutHearts, v -> c.logoutHearts = v, "Ignores armor, protection and resistance"));

        list.add(new Setting(32, Items.ELYTRA, "Flight time", "", 1, 0, 1, true, true,
                () -> c.flightEnabled ? 1 : 0, v -> c.flightEnabled = v > 0, "Limit elytra in combat"));
        list.add(new Setting(33, Items.PHANTOM_MEMBRANE, "Drain outside combat", "", 1, 0, 1, true, true,
                () -> c.flightDrainOutsideCombat ? 1 : 0, v -> c.flightDrainOutsideCombat = v > 0, "Off means free flight out of combat"));
        list.add(new Setting(34, Items.FEATHER, "Flight start", "s", 1, 0, 3600, false, true,
                () -> c.flightStartSeconds, v -> c.flightStartSeconds = (int) v, "What new players get"));

        list.add(new Setting(37, Items.FIREWORK_ROCKET, "Flight max", "s", 1, 0, 3600, false, true,
                () -> c.flightMaxSeconds, v -> c.flightMaxSeconds = (int) v, "Cap"));
        list.add(new Setting(38, Items.IRON_BARS, "Flight unlock", "s", 1, 0, 3600, false, true,
                () -> c.flightUnlockSeconds, v -> c.flightUnlockSeconds = (int) v, "Needed to fly again after running out"));
        list.add(new Setting(39, Items.GOLDEN_SWORD, "Damage per reward", " hearts", 0.5, 0.5, 100, false, false,
                () -> c.flightDamageHearts, v -> c.flightDamageHearts = v, "Hearts dealt to players per reward"));
        list.add(new Setting(40, Items.SUGAR, "Flight per reward", "s", 0.5, 0, 3600, false, false,
                () -> c.flightPerDamageSeconds, v -> c.flightPerDamageSeconds = v, "Seconds gained per reward"));
        list.add(new Setting(41, Items.TOTEM_OF_UNDYING, "Flight per kill", "s", 1, 0, 3600, false, true,
                () -> c.flightPerKillSeconds, v -> c.flightPerKillSeconds = (int) v, "Seconds gained per player kill"));
        return list;
    }

    private static Item spear() {
        Item item = Registries.ITEM.get(Identifier.ofVanilla("iron_spear"));
        return item == null || item == Items.AIR ? Items.TRIDENT : item;
    }

    private static final class Handler extends GenericContainerScreenHandler {

        private final SimpleInventory inv;
        private final List<UUID> shown = new ArrayList<>();
        private View view = View.LIST;
        private int page;
        private UUID selected;

        Handler(int syncId, PlayerInventory playerInv, SimpleInventory inv) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6);
            this.inv = inv;
            render();
        }

        @Override
        public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
            if (slot < 0 || slot >= 54) return;
            boolean left = button == 0;
            boolean shift = action == SlotActionType.QUICK_MOVE;
            if (action != SlotActionType.PICKUP && !shift) return;
            try {
                switch (view) {
                    case LIST -> clickList(slot);
                    case PLAYER -> clickPlayer(slot, left, shift);
                    case SETTINGS -> clickSettings(slot, left, shift);
                }
            } catch (Throwable t) {
                PsuAttributes.LOGGER.warn("PsuAttributes: menu click failed", t);
            }
            render();
            syncState();
        }

        private void clickList(int slot) {
            if (slot < PER_PAGE) {
                if (slot < shown.size()) {
                    selected = shown.get(slot);
                    view = View.PLAYER;
                }
            } else if (slot == SLOT_PREV && page > 0) {
                page--;
            } else if (slot == SLOT_NEXT) {
                page++;
            } else if (slot == SLOT_SETTINGS) {
                view = View.SETTINGS;
            }
        }

        private void clickPlayer(int slot, boolean left, boolean shift) {
            PsuAttributes mod = PsuAttributes.get();
            if (slot == SLOT_BACK || selected == null) {
                view = View.LIST;
                return;
            }
            AttributeType type = slot == SLOT_MACE ? AttributeType.MACE
                    : slot == SLOT_SPEAR ? AttributeType.SPEAR
                    : slot == SLOT_SWORD ? AttributeType.SWORD : null;
            if (type != null) {
                if (mod.config.has(selected, type)) mod.config.revoke(selected, type);
                else mod.config.grant(selected, nameOf(selected), type);
                mod.resetState(selected, type);
            } else if (slot == SLOT_FLIGHT) {
                PlayerDataStore.Entry d = entry(selected);
                if (d == null) return;
                int max = Math.max(0, mod.config.flightMaxSeconds) * 20;
                if (shift) d.flightTicks = left ? max : 0;
                else d.flightTicks = Math.max(0, Math.min(max, d.flightTicks + (left ? 100 : -100)));
                if (d.flightTicks <= 0) d.flightLocked = true;
                else if (d.flightTicks >= mod.config.flightUnlockSeconds * 20) d.flightLocked = false;
                mod.data.markDirty();
            } else if (slot == SLOT_RESET) {
                mod.resetState(selected, null);
            } else if (slot == SLOT_COMBAT) {
                CombatLog.clear(selected);
            }
        }

        private void clickSettings(int slot, boolean left, boolean shift) {
            if (slot == SLOT_BACK) {
                view = View.LIST;
                return;
            }
            for (Setting s : settings()) {
                if (s.slot() != slot) continue;
                if (s.bool()) {
                    s.set().accept(s.get().getAsDouble() > 0 ? 0 : 1);
                } else if (s.options() != null) {
                    int n = s.options().length;
                    int i = (int) s.get().getAsDouble();
                    s.set().accept(((i + (left ? 1 : -1)) % n + n) % n);
                } else {
                    double delta = s.step() * (shift ? 10 : 1) * (left ? 1 : -1);
                    double v = Math.max(s.min(), Math.min(s.max(), s.get().getAsDouble() + delta));
                    v = s.whole() ? Math.rint(v) : Math.round(v * 100.0) / 100.0;
                    s.set().accept(v);
                }
                PsuAttributes.get().config.save();
                return;
            }
        }

        private void render() {
            for (int i = 0; i < 54; i++) inv.setStack(i, filler());
            switch (view) {
                case LIST -> renderList();
                case PLAYER -> renderPlayer();
                case SETTINGS -> renderSettings();
            }
        }

        private void renderList() {
            List<UUID> all = rows();
            int maxPage = Math.max(0, (all.size() - 1) / PER_PAGE);
            if (page > maxPage) page = maxPage;
            shown.clear();
            for (int i = 0; i < PER_PAGE; i++) {
                int idx = page * PER_PAGE + i;
                if (idx >= all.size()) {
                    inv.setStack(i, ItemStack.EMPTY);
                    continue;
                }
                UUID id = all.get(idx);
                shown.add(id);
                List<Text> lore = info(id);
                lore.add(line(""));
                lore.add(line("§eClick §7to manage"));
                inv.setStack(i, head(id, lore));
            }
            if (page > 0) inv.setStack(SLOT_PREV, named(Items.ARROW, "§ePrevious page"));
            if (page < maxPage) inv.setStack(SLOT_NEXT, named(Items.ARROW, "§eNext page"));
            inv.setStack(SLOT_PAGE, named(Items.PAPER, "§fPage " + (page + 1) + "/" + (maxPage + 1),
                    line("§7Online players first, then offline ones with attributes")));
            inv.setStack(SLOT_SETTINGS, named(Items.COMPARATOR, "§6Settings", line("§7Cooldowns, multipliers, combat log, flight")));
        }

        private void renderPlayer() {
            if (selected == null) {
                view = View.LIST;
                renderList();
                return;
            }
            PsuAttributes mod = PsuAttributes.get();
            inv.setStack(SLOT_HEAD, head(selected, info(selected)));
            inv.setStack(SLOT_MACE, toggle(Items.MACE, "Mace", AttributeType.MACE,
                    "§7WB I, none, then WB III x" + Abilities.fmt(mod.config.maceMultiplier) + " until landing"));
            inv.setStack(SLOT_SPEAR, toggle(spear(), "Spear", AttributeType.SPEAR,
                    "§7One ground lunge, air lunges until landing"));
            inv.setStack(SLOT_SWORD, toggle(Items.DIAMOND_SWORD, "Sword", AttributeType.SWORD,
                    "§7" + mod.config.swordComboHits + " hits without getting hit, then x" + Abilities.fmt(mod.config.swordMultiplier)));

            PlayerDataStore.Entry d = entry(selected);
            if (d != null) {
                inv.setStack(SLOT_FLIGHT, named(Items.ELYTRA, "§bFlight: §f" + Flight.secs(d.flightTicks) + "s"
                                + (d.flightLocked ? " §c(locked)" : ""),
                        line("§eLeft §7+5s   §eRight §7-5s"),
                        line("§eShift left §7max   §eShift right §70")));
            } else {
                inv.setStack(SLOT_FLIGHT, named(Items.ELYTRA, "§8Flight: no data yet", line("§7Player has not joined since install")));
            }
            inv.setStack(SLOT_RESET, named(Items.CLOCK, "§fReset cooldowns and combos", line("§7Click to clear")));
            ServerPlayerEntity p = online(selected);
            int now = mod.now();
            if (p != null && CombatLog.inCombat(p, now)) {
                int secs = (CombatLog.ticksLeft(p, now) + 19) / 20;
                inv.setStack(SLOT_COMBAT, named(Items.REDSTONE, "§cIn combat: " + secs / 60 + ":" + String.format("%02d", secs % 60),
                        line("§7Click to clear the tag")));
            } else {
                inv.setStack(SLOT_COMBAT, named(Items.GUNPOWDER, "§7Not in combat"));
            }
            inv.setStack(SLOT_BACK, named(Items.ARROW, "§eBack"));
        }

        private void renderSettings() {
            inv.setStack(SLOT_HEAD, named(Items.COMPARATOR, "§6Settings",
                    line("§eLeft §7up   §eRight §7down"),
                    line("§eShift §7moves 10 steps"),
                    line("§7Toggles just need a click")));
            for (Setting s : settings()) {
                double v = s.get().getAsDouble();
                String value = s.bool() ? (v > 0 ? "§aON" : "§cOFF")
                        : s.options() != null ? "§f" + s.options()[(int) v]
                        : "§f" + (s.unit().equals("x") ? "x" + Abilities.fmt(v) : Abilities.fmt(v) + s.unit());
                ItemStack stack = named(s.icon(), "§e" + s.label() + ": " + value,
                        line("§7" + s.desc()),
                        line(s.bool() ? "§7Click to toggle"
                                : s.options() != null ? "§7Left next, right previous"
                                : "§7Step " + Abilities.fmt(s.step())));
                if (s.bool() && v > 0) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                inv.setStack(s.slot(), stack);
            }
            inv.setStack(SLOT_BACK, named(Items.ARROW, "§eBack"));
        }

        private ItemStack toggle(Item icon, String label, AttributeType type, String desc) {
            boolean on = PsuAttributes.get().config.has(selected, type);
            ItemStack s = named(icon, "§f" + label + ": " + (on ? "§aON" : "§cOFF"), line(desc), line("§7Click to toggle"));
            s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, on);
            return s;
        }

        private List<UUID> rows() {
            Map<UUID, Boolean> out = new LinkedHashMap<>();
            MinecraftServer s = PsuAttributes.get().server;
            if (s != null) {
                for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) out.put(p.getUuid(), true);
            }
            for (String key : PsuAttributes.get().config.players.keySet()) {
                try {
                    out.putIfAbsent(UUID.fromString(key), false);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return new ArrayList<>(out.keySet());
        }
    }

    private static PlayerDataStore.Entry entry(UUID id) {
        ServerPlayerEntity p = online(id);
        return p != null ? PsuAttributes.get().data.get(p) : PsuAttributes.get().data.find(id);
    }

    private static ServerPlayerEntity online(UUID id) {
        MinecraftServer s = PsuAttributes.get().server;
        return s == null ? null : s.getPlayerManager().getPlayer(id);
    }

    private static String nameOf(UUID id) {
        ServerPlayerEntity p = online(id);
        if (p != null) return p.getGameProfile().name();
        ModConfig.PlayerEntry e = PsuAttributes.get().config.players.get(id.toString());
        if (e != null && e.name != null && !e.name.isEmpty()) return e.name;
        PlayerDataStore.Entry d = PsuAttributes.get().data.find(id);
        if (d != null && d.name != null && !d.name.isEmpty()) return d.name;
        return id.toString().substring(0, 8);
    }

    private static List<Text> info(UUID id) {
        PsuAttributes mod = PsuAttributes.get();
        List<Text> lore = new ArrayList<>();
        List<String> attrs = new ArrayList<>();
        for (AttributeType t : mod.config.get(id)) attrs.add(t.id());
        lore.add(line("§7Attributes: " + (attrs.isEmpty() ? "§8none" : "§e" + String.join(", ", attrs))));
        PlayerDataStore.Entry d = entry(id);
        if (d != null) lore.add(line("§7Flight: §b" + Flight.secs(d.flightTicks) + "s" + (d.flightLocked ? " §c(locked)" : "")));
        ServerPlayerEntity p = online(id);
        if (p != null) {
            lore.add(line(CombatLog.inCombat(p, mod.now()) ? "§cIn combat" : "§7Not in combat"));
            lore.add(line("§aOnline"));
        } else {
            lore.add(line("§8Offline"));
        }
        return lore;
    }

    private static ItemStack head(UUID id, List<Text> lore) {
        ItemStack h = new ItemStack(Items.PLAYER_HEAD);
        ServerPlayerEntity p = online(id);
        ProfileComponent profile;
        try {
            profile = p != null ? ProfileComponent.ofStatic(p.getGameProfile()) : ProfileComponent.ofDynamic(id);
            h.set(DataComponentTypes.PROFILE, profile);
        } catch (Throwable ignored) {
        }
        h.set(DataComponentTypes.CUSTOM_NAME, line("§f" + nameOf(id)));
        h.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return h;
    }

    private static ItemStack named(Item item, String name, Text... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, line(name));
        if (lore.length > 0) s.set(DataComponentTypes.LORE, new LoreComponent(List.of(lore)));
        return s;
    }

    private static ItemStack filler() {
        ItemStack s = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return s;
    }

    private static Text line(String s) {
        return Text.literal(s).styled(st -> st.withItalic(false));
    }
}
