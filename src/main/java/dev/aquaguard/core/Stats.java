package dev.aquaguard.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class Stats {
    private final LongAdder flags = new LongAdder();
    private final Map<String, LongAdder> byCheck = new ConcurrentHashMap<>();
    private final long startedAt = System.currentTimeMillis();

    public void increment(String check) {
        flags.increment();
        byCheck.computeIfAbsent(check, k -> new LongAdder()).increment();
    }

    public long totalFlags() {
        return flags.sum();
    }

    public long flags(String check) {
        LongAdder adder = byCheck.get(check);
        return adder == null ? 0 : adder.sum();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> copy = new ConcurrentHashMap<>();
        for (Map.Entry<String, LongAdder> e : byCheck.entrySet()) {
            copy.put(e.getKey(), e.getValue().sum());
        }
        return copy;
    }

    public long startedAt() {
        return startedAt;
    }
}
