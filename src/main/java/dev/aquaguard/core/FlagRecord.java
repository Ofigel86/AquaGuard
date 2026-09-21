package dev.aquaguard.core;

import java.util.UUID;

public record FlagRecord(
        long time,
        UUID uuid,
        String name,
        String check,
        double vl,
        double total,
        int ping,
        String world,
        String debug
) {}
