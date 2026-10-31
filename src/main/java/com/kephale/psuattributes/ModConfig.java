package com.kephale.psuattributes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DATA_VERSION = 5;

    public static final String MACE_NO_COOLDOWN = "nocooldown";
    public static final String MACE_VANILLA = "vanilla";
    public static final String MACE_NO_WIND_BURST = "nowindburst";
    public static final String MACE_DISABLED = "disabled";
    public static final String[] MACE_MODES = {MACE_NO_COOLDOWN, MACE_VANILLA, MACE_NO_WIND_BURST, MACE_DISABLED};

    public int dataVersion = DATA_VERSION;

    public int maceCooldownSeconds = 20;
    public double maceMultiplier = 1.5;
    public String maceCooldownMode = MACE_DISABLED;
    public double maceLandGraceSeconds = 1.0;
    public boolean maceKillReset = true;

    public int spearCooldownSeconds = 15;
    public boolean spearSneakSave = true;

    public int swordComboHits = 5;
    public double swordMultiplier = 1.2;
    public int swordCooldownSeconds = 30;

    public boolean combatLogEnabled = true;
    public int combatSeconds = 120;
    public double logoutHearts = 8.0;

    public boolean flightEnabled = true;
    public boolean flightDrainOutsideCombat = false;
    public int flightStartSeconds = 20;
    public int flightMaxSeconds = 25;
    public int flightUnlockSeconds = 15;
    public double flightDamageHearts = 10.0;
    public double flightPerDamageSeconds = 2.5;
    public int flightPerKillSeconds = 5;

    public Map<String, PlayerEntry> players = new LinkedHashMap<>();

    public static final class PlayerEntry {
        public String name = "";
        public List<String> attributes = new ArrayList<>();
    }

    private transient Path path;
    private transient Map<UUID, EnumSet<AttributeType>> cache = new HashMap<>();

    public static ModConfig load(Path path) {
        ModConfig config = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                config = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                PsuAttributes.LOGGER.error("PsuAttributes: could not read {}, using defaults", path, e);
            }
        }
        if (config == null) config = new ModConfig();
        config.path = path;
        config.cache = new HashMap<>();
        if (config.players == null) config.players = new LinkedHashMap<>();
        int oldVersion = config.dataVersion;
        config.dataVersion = DATA_VERSION;
        if (oldVersion < 4) {
            config.resetValues();
            PsuAttributes.LOGGER.info("PsuAttributes: config was from an older version, settings reset to the new defaults");
        } else if (oldVersion < 5 && config.flightUnlockSeconds == 20) {
            config.flightUnlockSeconds = 15;
        }
        if (config.maceCooldownMode == null || maceModeIndex(config.maceCooldownMode) < 0) {
            config.maceCooldownMode = MACE_DISABLED;
        }
        config.rebuildCache();
        config.save();
        return config;
    }

    private void resetValues() {
        ModConfig d = new ModConfig();
        maceCooldownSeconds = d.maceCooldownSeconds;
        maceMultiplier = d.maceMultiplier;
        maceCooldownMode = d.maceCooldownMode;
        maceLandGraceSeconds = d.maceLandGraceSeconds;
        maceKillReset = d.maceKillReset;
        spearCooldownSeconds = d.spearCooldownSeconds;
        spearSneakSave = d.spearSneakSave;
        swordComboHits = d.swordComboHits;
        swordMultiplier = d.swordMultiplier;
        swordCooldownSeconds = d.swordCooldownSeconds;
        combatLogEnabled = d.combatLogEnabled;
        combatSeconds = d.combatSeconds;
        logoutHearts = d.logoutHearts;
        flightEnabled = d.flightEnabled;
        flightDrainOutsideCombat = d.flightDrainOutsideCombat;
        flightStartSeconds = d.flightStartSeconds;
        flightMaxSeconds = d.flightMaxSeconds;
        flightUnlockSeconds = d.flightUnlockSeconds;
        flightDamageHearts = d.flightDamageHearts;
        flightPerDamageSeconds = d.flightPerDamageSeconds;
        flightPerKillSeconds = d.flightPerKillSeconds;
    }

    public void resetSettings() {
        resetValues();
        save();
    }

    public static int maceModeIndex(String mode) {
        for (int i = 0; i < MACE_MODES.length; i++) {
            if (MACE_MODES[i].equalsIgnoreCase(mode)) return i;
        }
        return -1;
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            PsuAttributes.LOGGER.error("PsuAttributes: could not save {}", path, e);
        }
    }

    private void rebuildCache() {
        cache.clear();
        for (Map.Entry<String, PlayerEntry> entry : players.entrySet()) {
            UUID id;
            try {
                id = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            EnumSet<AttributeType> set = EnumSet.noneOf(AttributeType.class);
            if (entry.getValue() != null && entry.getValue().attributes != null) {
                for (String s : entry.getValue().attributes) {
                    AttributeType type = AttributeType.byId(s);
                    if (type != null) set.add(type);
                }
            }
            if (!set.isEmpty()) cache.put(id, set);
        }
    }

    public EnumSet<AttributeType> get(UUID id) {
        EnumSet<AttributeType> set = cache.get(id);
        return set == null ? EnumSet.noneOf(AttributeType.class) : set;
    }

    public boolean has(UUID id, AttributeType type) {
        EnumSet<AttributeType> set = cache.get(id);
        return set != null && set.contains(type);
    }

    public boolean grant(UUID id, String name, AttributeType type) {
        PlayerEntry entry = players.computeIfAbsent(id.toString(), k -> new PlayerEntry());
        entry.name = name;
        if (entry.attributes == null) entry.attributes = new ArrayList<>();
        if (entry.attributes.contains(type.id())) return false;
        entry.attributes.add(type.id());
        rebuildCache();
        save();
        return true;
    }

    public boolean revoke(UUID id, AttributeType type) {
        PlayerEntry entry = players.get(id.toString());
        if (entry == null || entry.attributes == null || !entry.attributes.remove(type.id())) return false;
        if (entry.attributes.isEmpty()) players.remove(id.toString());
        rebuildCache();
        save();
        return true;
    }
}
