package dev.aquaguard.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class History {
    private final int globalLimit;
    private final int perPlayerLimit;
    private final Deque<FlagRecord> global = new ArrayDeque<>();
    private final Map<UUID, Deque<FlagRecord>> perPlayer = new ConcurrentHashMap<>();

    public History(int globalLimit, int perPlayerLimit) {
        this.globalLimit = Math.max(10, globalLimit);
        this.perPlayerLimit = Math.max(5, perPlayerLimit);
    }

    public synchronized void add(FlagRecord record) {
        push(global, record, globalLimit);
        push(perPlayer.computeIfAbsent(record.uuid(), id -> new ArrayDeque<>()), record, perPlayerLimit);
    }

    public synchronized List<FlagRecord> recent(int limit) {
        return tail(global, limit);
    }

    public synchronized List<FlagRecord> recent(UUID uuid, int limit) {
        Deque<FlagRecord> deque = perPlayer.get(uuid);
        if (deque == null) return List.of();
        return tail(deque, limit);
    }

    private static void push(Deque<FlagRecord> deque, FlagRecord record, int limit) {
        deque.addLast(record);
        while (deque.size() > limit) deque.removeFirst();
    }

    private static List<FlagRecord> tail(Deque<FlagRecord> deque, int limit) {
        List<FlagRecord> list = new ArrayList<>(deque);
        int from = Math.max(0, list.size() - Math.max(1, limit));
        List<FlagRecord> slice = new ArrayList<>(list.subList(from, list.size()));
        java.util.Collections.reverse(slice);
        return slice;
    }
}
