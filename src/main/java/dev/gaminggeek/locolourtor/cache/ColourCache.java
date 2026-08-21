package dev.gaminggeek.locolourtor.cache;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ColourCache {

    public static final ColourCache INSTANCE = new ColourCache();
    private final ConcurrentHashMap<UUID, OptionalInt> cache = new ConcurrentHashMap<>();

    private ColourCache() {
    }

    public boolean contains(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public OptionalInt get(UUID uuid) {
        return cache.getOrDefault(uuid, OptionalInt.empty());
    }

    public void put(UUID uuid, int argb) {
        cache.put(uuid, OptionalInt.of(argb));
    }

    public void putDefault(UUID uuid) {
        cache.put(uuid, OptionalInt.empty());
    }

    public void putAll(Map<UUID, Integer> entries) {
        entries.forEach(this::put);
    }

    public void retainAll(java.util.Collection<UUID> activeUuids, UUID keepAlways) {
        cache.keySet().removeIf(uuid -> !uuid.equals(keepAlways) && !activeUuids.contains(uuid));
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
    }

    public void clear() {
        cache.clear();
    }
}
