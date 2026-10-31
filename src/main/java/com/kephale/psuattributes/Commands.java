package com.kephale.psuattributes;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class Commands {

    private Commands() {}

    private static final SuggestionProvider<ServerCommandSource> TYPES = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (AttributeType type : AttributeType.values()) ids.add(type.id());
        return CommandSource.suggestMatching(ids, builder);
    };

    public static void register(PsuAttributes mod) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModConfig c = mod.config;
            dispatcher.register(literal("hud").executes(ctx -> hud(ctx)));
            dispatcher.register(literal("settings").executes(ctx -> hud(ctx)));

            dispatcher.register(literal("psu")
                    .requires(Commands::isAdmin)
                    .executes(ctx -> menu(ctx))
                    .then(literal("menu").executes(ctx -> menu(ctx)))
                    .then(literal("help").executes(ctx -> help(ctx)))
                    .then(literal("give")
                            .then(argument("player", EntityArgumentType.player())
                                    .then(argument("type", StringArgumentType.word()).suggests(TYPES)
                                            .executes(ctx -> give(ctx, mod)))))
                    .then(literal("take")
                            .then(argument("player", EntityArgumentType.player())
                                    .then(argument("type", StringArgumentType.word()).suggests(TYPES)
                                            .executes(ctx -> take(ctx, mod)))))
                    .then(literal("reset")
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> reset(ctx, mod))))
                    .then(literal("flight")
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> flightGet(ctx, mod))
                                    .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                                            .executes(ctx -> flightSet(ctx, mod)))))
                    .then(literal("list").executes(ctx -> list(ctx, mod)))
                    .then(literal("settings").executes(ctx -> settings(ctx, mod)))
                    .then(literal("config")
                            .then(literal("default").executes(ctx -> {
                                mod.config.resetSettings();
                                msg(ctx, "§aAll settings are back to default. Attribute grants were kept.");
                                return 1;
                            })))
                    .then(literal("set")
                            .then(intSetting(mod, "macecooldown", 0, 3600, () -> c.maceCooldownSeconds, v -> c.maceCooldownSeconds = v))
                            .then(doubleSetting(mod, "macemultiplier", () -> c.maceMultiplier, v -> c.maceMultiplier = v))
                            .then(maceModeSetting(mod))
                            .then(doubleSetting(mod, "macelandgrace", () -> c.maceLandGraceSeconds, v -> c.maceLandGraceSeconds = v))
                            .then(boolSetting(mod, "macekillreset", () -> c.maceKillReset, v -> c.maceKillReset = v))
                            .then(intSetting(mod, "spearcooldown", 0, 3600, () -> c.spearCooldownSeconds, v -> c.spearCooldownSeconds = v))
                            .then(boolSetting(mod, "spearsneaksave", () -> c.spearSneakSave, v -> c.spearSneakSave = v))
                            .then(intSetting(mod, "swordhits", 1, 100, () -> c.swordComboHits, v -> c.swordComboHits = v))
                            .then(doubleSetting(mod, "swordmultiplier", () -> c.swordMultiplier, v -> c.swordMultiplier = v))
                            .then(intSetting(mod, "swordcooldown", 0, 3600, () -> c.swordCooldownSeconds, v -> c.swordCooldownSeconds = v))
                            .then(boolSetting(mod, "combatlog", () -> c.combatLogEnabled, v -> c.combatLogEnabled = v))
                            .then(intSetting(mod, "combatseconds", 0, 3600, () -> c.combatSeconds, v -> c.combatSeconds = v))
                            .then(doubleSetting(mod, "logouthearts", () -> c.logoutHearts, v -> c.logoutHearts = v))
                            .then(boolSetting(mod, "flight", () -> c.flightEnabled, v -> c.flightEnabled = v))
                            .then(boolSetting(mod, "flightoutsidecombat", () -> c.flightDrainOutsideCombat, v -> c.flightDrainOutsideCombat = v))
                            .then(intSetting(mod, "flightstart", 0, 3600, () -> c.flightStartSeconds, v -> c.flightStartSeconds = v))
                            .then(intSetting(mod, "flightmax", 0, 3600, () -> c.flightMaxSeconds, v -> c.flightMaxSeconds = v))
                            .then(intSetting(mod, "flightunlock", 0, 3600, () -> c.flightUnlockSeconds, v -> c.flightUnlockSeconds = v))
                            .then(doubleSetting(mod, "flightdamagehearts", () -> c.flightDamageHearts, v -> c.flightDamageHearts = v))
                            .then(doubleSetting(mod, "flightperdamage", () -> c.flightPerDamageSeconds, v -> c.flightPerDamageSeconds = v))
                            .then(intSetting(mod, "flightperkill", 0, 3600, () -> c.flightPerKillSeconds, v -> c.flightPerKillSeconds = v))));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> intSetting(PsuAttributes mod, String name, int min, int max,
                                                                       IntSupplier getter, IntConsumer setter) {
        return literal(name)
                .executes(ctx -> {
                    msg(ctx, "§e" + name + ": §f" + getter.getAsInt());
                    return 1;
                })
                .then(argument("value", IntegerArgumentType.integer(min, max))
                        .executes(ctx -> {
                            setter.accept(IntegerArgumentType.getInteger(ctx, "value"));
                            mod.config.save();
                            msg(ctx, "§a" + name + " set to §f" + getter.getAsInt());
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> doubleSetting(PsuAttributes mod, String name,
                                                                          DoubleSupplier getter, DoubleConsumer setter) {
        return literal(name)
                .executes(ctx -> {
                    msg(ctx, "§e" + name + ": §f" + Abilities.fmt(getter.getAsDouble()));
                    return 1;
                })
                .then(argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
                        .executes(ctx -> {
                            setter.accept(DoubleArgumentType.getDouble(ctx, "value"));
                            mod.config.save();
                            msg(ctx, "§a" + name + " set to §f" + Abilities.fmt(getter.getAsDouble()));
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> maceModeSetting(PsuAttributes mod) {
        LiteralArgumentBuilder<ServerCommandSource> node = literal("macecooldownmode")
                .executes(ctx -> {
                    msg(ctx, "§emacecooldownmode: §f" + mod.config.maceCooldownMode);
                    return 1;
                });
        for (String mode : ModConfig.MACE_MODES) {
            node.then(literal(mode).executes(ctx -> {
                mod.config.maceCooldownMode = mode;
                mod.config.save();
                msg(ctx, "§amacecooldownmode set to §f" + mode);
                return 1;
            }));
        }
        return node;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> boolSetting(PsuAttributes mod, String name,
                                                                        BooleanSupplier getter, Consumer<Boolean> setter) {
        return literal(name)
                .executes(ctx -> {
                    msg(ctx, "§e" + name + ": §f" + (getter.getAsBoolean() ? "on" : "off"));
                    return 1;
                })
                .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            setter.accept(BoolArgumentType.getBool(ctx, "value"));
                            mod.config.save();
                            msg(ctx, "§a" + name + " set to §f" + (getter.getAsBoolean() ? "on" : "off"));
                            return 1;
                        }));
    }

    private static int flightGet(CommandContext<ServerCommandSource> ctx, PsuAttributes mod) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        PlayerDataStore.Entry d = mod.data.get(player);
        msg(ctx, "§f" + player.getGameProfile().name() + "§7 flight §f" + Flight.secs(d.flightTicks) + "s"
                + (d.flightLocked ? " §c(locked)" : ""));
        return 1;
    }

    private static int flightSet(CommandContext<ServerCommandSource> ctx, PsuAttributes mod) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        Flight.set(player, IntegerArgumentType.getInteger(ctx, "seconds"));
        return flightGet(ctx, mod);
    }

    private static int hud(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        HudMenu.open(player);
        return 1;
    }

    private static int menu(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return help(ctx);
        Menu.open(player);
        return 1;
    }

    private static int help(CommandContext<ServerCommandSource> ctx) {
        msg(ctx, "§6PsuAttributes");
        msg(ctx, "§e/psu §7opens the menu");
        msg(ctx, "§e/psu give <player> <mace|spear|sword>");
        msg(ctx, "§e/psu take <player> <mace|spear|sword>");
        msg(ctx, "§e/psu reset <player> §7clears cooldowns and combos");
        msg(ctx, "§e/psu flight <player> [seconds]");
        msg(ctx, "§e/psu list");
        msg(ctx, "§e/psu settings");
        msg(ctx, "§e/psu set <setting> [value]");
        msg(ctx, "§e/psu config default §7puts every setting back to default");
        msg(ctx, "§e/hud §7or §e/settings §7pick where each HUD shows (everyone)");
        return 1;
    }

    private static AttributeType type(CommandContext<ServerCommandSource> ctx) {
        AttributeType type = AttributeType.byId(StringArgumentType.getString(ctx, "type"));
        if (type == null) ctx.getSource().sendError(Text.literal("Unknown attribute. Use mace, spear or sword."));
        return type;
    }

    private static int give(CommandContext<ServerCommandSource> ctx, PsuAttributes mod) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        AttributeType type = type(ctx);
        if (type == null) return 0;
        String name = player.getGameProfile().name();
        if (mod.config.grant(player.getUuid(), name, type)) {
            mod.resetState(player.getUuid(), type);
            msg(ctx, "§aGave §f" + type.id() + "§a to §f" + name);
            return 1;
        }
        msg(ctx, "§e" + name + " already has " + type.id());
        return 0;
    }

    private static int take(CommandContext<ServerCommandSource> ctx, PsuAttributes mod) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        AttributeType type = type(ctx);
        if (type == null) return 0;
        String name = player.getGameProfile().name();
        if (mod.config.revoke(player.getUuid(), type)) {
            mod.resetState(player.getUuid(), type);
            msg(ctx, "§aTook §f" + type.id() + "§a from §f" + name);
            return 1;
        }
        msg(ctx, "§e" + name + " does not have " + type.id());
        return 0;
    }

    private static int reset(CommandContext<ServerCommandSource> ctx, PsuAttributes mod) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        mod.resetState(player.getUuid(), null);
        msg(ctx, "§aReset cooldowns and combos for §f" + player.getGameProfile().name());
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx, PsuAttributes mod) {
        if (mod.config.players.isEmpty()) {
            msg(ctx, "§7Nobody has attributes.");
            return 1;
        }
        for (Map.Entry<String, ModConfig.PlayerEntry> e : mod.config.players.entrySet()) {
            ModConfig.PlayerEntry entry = e.getValue();
            if (entry == null || entry.attributes == null || entry.attributes.isEmpty()) continue;
            String name = entry.name == null || entry.name.isEmpty() ? e.getKey() : entry.name;
            msg(ctx, "§f" + name + "§7: §e" + String.join(", ", entry.attributes));
        }
        return 1;
    }

    private static int settings(CommandContext<ServerCommandSource> ctx, PsuAttributes mod) {
        ModConfig c = mod.config;
        msg(ctx, "§6Mace §7cooldown §f" + c.maceCooldownSeconds + "s §7multiplier §fx" + Abilities.fmt(c.maceMultiplier)
                + " §7mode §f" + c.maceCooldownMode + " §7land grace §f" + Abilities.fmt(c.maceLandGraceSeconds)
                + "s §7kill reset §f" + (c.maceKillReset ? "on" : "off"));
        msg(ctx, "§6Spear §7cooldown §f" + c.spearCooldownSeconds + "s §7sneak save §f" + (c.spearSneakSave ? "on" : "off"));
        msg(ctx, "§6Sword §7hits §f" + c.swordComboHits + " §7multiplier §fx" + Abilities.fmt(c.swordMultiplier)
                + " §7cooldown §f" + c.swordCooldownSeconds + "s");
        msg(ctx, "§6Combat log §f" + (c.combatLogEnabled ? "on" : "off") + " §7timer §f" + c.combatSeconds
                + "s §7logout damage §f" + Abilities.fmt(c.logoutHearts) + " hearts");
        msg(ctx, "§6Flight §f" + (c.flightEnabled ? "on" : "off") + " §7outside combat drain §f"
                + (c.flightDrainOutsideCombat ? "on" : "off") + " §7start §f" + c.flightStartSeconds
                + "s §7max §f" + c.flightMaxSeconds + "s §7unlock §f" + c.flightUnlockSeconds + "s");
        msg(ctx, "§6Flight gain §f" + Abilities.fmt(c.flightPerDamageSeconds) + "s §7per §f" + Abilities.fmt(c.flightDamageHearts)
                + " hearts §7dealt, §f" + c.flightPerKillSeconds + "s §7per kill");
        return 1;
    }

    private static void msg(CommandContext<ServerCommandSource> ctx, String text) {
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
    }

    private static boolean isAdmin(ServerCommandSource src) {
        try {
            ServerPlayerEntity p = src.getPlayer();
            if (p == null) return true;
            MinecraftServer s = p.getEntityWorld().getServer();
            if (s == null || s.isSingleplayer()) return true;
            net.minecraft.server.PlayerConfigEntry entry =
                    new net.minecraft.server.PlayerConfigEntry(p.getGameProfile());
            if (s.isHost(entry)) return true;
            return s.getPlayerManager().isOperator(entry);
        } catch (Throwable t) {
            PsuAttributes.LOGGER.warn("PsuAttributes: isAdmin check failed, allowing", t);
            return true;
        }
    }
}
