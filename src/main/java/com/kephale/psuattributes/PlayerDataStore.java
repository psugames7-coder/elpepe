package com.kephale.psuattributes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerDataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, Entry>>() {}.getType();

    public static final class Entry {
        public String name = "";
        public int flightTicks = -1;
        public boolean flightLocked;
        public double damageBank;
        public String hudCombat = "ACTION_BAR";
        public String hudFlight = "ACTION_BAR";
        public String hudAbility = "CHAT";
        public String hudCooldown = "ACTION_BAR";
        public int hudVersion;
    }

    private static final int HUD_VERSION = 1;

    private final Path path;
    private Map<String, Entry> players = new LinkedHashMap<>();
    private boolean dirty;

    public PlayerDataStore(Path path) {
        this.path = path;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Map<String, Entry> loaded = GSON.fromJson(reader, TYPE);
                if (loaded != null) players = loaded;
            } catch (Exception e) {
                PsuAttributes.LOGGER.error("PsuAttributes: could not read {}", path, e);
            }
        }
    }

    public Entry get(ServerPlayerEntity player) {
        Entry entry = players.computeIfAbsent(player.getUuid().toString(), k -> new Entry());
        String name = player.getGameProfile().name();
        if (!name.equals(entry.name)) {
            entry.name = name;
            dirty = true;
        }
        ModConfig config = PsuAttributes.get().config;
        if (entry.flightTicks < 0) {
            entry.flightTicks = Math.max(0, config.flightStartSeconds) * 20;
            dirty = true;
        }
        int max = Math.max(0, config.flightMaxSeconds) * 20;
        if (entry.flightTicks > max) {
            entry.flightTicks = max;
            dirty = true;
        }
        if (entry.hudVersion < HUD_VERSION) {
            entry.hudAbility = "CHAT";
            entry.hudCooldown = "ACTION_BAR";
            entry.hudVersion = HUD_VERSION;
            dirty = true;
        }
        if (entry.hudCombat == null) entry.hudCombat = "ACTION_BAR";
        if (entry.hudFlight == null) entry.hudFlight = "ACTION_BAR";
        if (entry.hudAbility == null) entry.hudAbility = "CHAT";
        if (entry.hudCooldown == null) entry.hudCooldown = "ACTION_BAR";
        return entry;
    }

    public Entry find(UUID id) {
        return players.get(id.toString());
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(players, TYPE, writer);
            }
            dirty = false;
        } catch (Exception e) {
            PsuAttributes.LOGGER.error("PsuAttributes: could not save {}", path, e);
        }
    }
}
