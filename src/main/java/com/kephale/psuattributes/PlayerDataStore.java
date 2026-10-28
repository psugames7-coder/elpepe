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

public final class PlayerDataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, Entry>>() {}.getType();

    public static final class Entry {
        public String name = "";
        public int flightTicks = -1;
        public boolean flightLocked;
        public double damageBank;
    }

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
        if (entry.flightTicks < 0) {
            entry.flightTicks = Math.max(0, PsuAttributes.get().config.flightStartSeconds) * 20;
            dirty = true;
        }
        return entry;
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
