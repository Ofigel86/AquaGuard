package dev.aquaguard.core;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище VL без Bukkit. Суммы, распад и сброс тестируются отдельно от сервера.
 */
public final class VlLedger {
    private final Map<UUID, Map<String, Double>> vls = new ConcurrentHashMap<>();

    public double add(UUID uuid, String check, double amount) {
        if (amount == 0) return get(uuid, check);
        Map<String, Double> map = vls.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        double next = Math.max(0, map.getOrDefault(check, 0.0) + amount);
        if (next <= 0.0001) map.remove(check);
        else map.put(check, next);
        if (map.isEmpty()) vls.remove(uuid);
        return next;
    }

    public double get(UUID uuid, String check) {
        Map<String, Double> map = vls.get(uuid);
        if (map == null) return 0;
        return map.getOrDefault(check, 0.0);
    }

    public Map<String, Double> view(UUID uuid) {
        Map<String, Double> map = vls.get(uuid);
        if (map == null || map.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(map));
    }

    public double total(UUID uuid) {
        Map<String, Double> map = vls.get(uuid);
        if (map == null) return 0;
        double s = 0;
        for (double v : map.values()) s += v;
        return s;
    }

    public void reset(UUID uuid) {
        vls.remove(uuid);
    }

    public void reset(UUID uuid, String check) {
        Map<String, Double> map = vls.get(uuid);
        if (map == null) return;
        map.remove(check);
        if (map.isEmpty()) vls.remove(uuid);
    }

    public void decayAll(double amount) {
        if (amount <= 0) return;
        for (UUID id : vls.keySet().toArray(UUID[]::new)) {
            Map<String, Double> map = vls.get(id);
            if (map == null) continue;
            for (String check : map.keySet().toArray(String[]::new)) {
                double val = map.getOrDefault(check, 0.0) - amount;
                if (val <= 0.0001) map.remove(check);
                else map.put(check, val);
            }
            if (map.isEmpty()) vls.remove(id);
        }
    }

    public Map<UUID, Map<String, Double>> snapshot() {
        Map<UUID, Map<String, Double>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Map<String, Double>> e : vls.entrySet()) {
            copy.put(e.getKey(), new ConcurrentHashMap<>(e.getValue()));
        }
        return copy;
    }

    public void load(UUID uuid, Map<String, Double> values) {
        if (values == null || values.isEmpty()) return;
        Map<String, Double> map = vls.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Double> e : values.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) map.put(e.getKey(), e.getValue());
        }
    }
}
