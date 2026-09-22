package dev.aquaguard.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Лестница наказаний по суммарному VL. Шаг выбирается наибольший, чей порог уже пройден.
 * Повторно тот же шаг не исполняется — это решает вызывающий код.
 */
public final class PunishmentLadder {
    public record Step(double minTotalVl, List<String> commands) {}

    private final List<Step> steps;

    public PunishmentLadder(List<Step> steps) {
        List<Step> copy = new ArrayList<>();
        if (steps != null) {
            for (Step s : steps) {
                if (s == null || s.commands == null || s.commands.isEmpty()) continue;
                copy.add(new Step(s.minTotalVl, List.copyOf(s.commands)));
            }
        }
        copy.sort((a, b) -> Double.compare(a.minTotalVl, b.minTotalVl));
        this.steps = List.copyOf(copy);
    }

    public int size() {
        return steps.size();
    }

    public Step step(int index) {
        if (index < 0 || index >= steps.size()) return null;
        return steps.get(index);
    }

    /** Индекс наибольшего достигнутого шага или -1. */
    public int highestReached(double totalVl) {
        int found = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (totalVl + 1e-6 >= steps.get(i).minTotalVl) found = i;
        }
        return found;
    }

    public static String applyPlaceholders(String command, String player, String check, String vl, String ping, String world) {
        if (command == null) return "";
        return command
                .replace("%player%", player == null ? "" : player)
                .replace("%check%", check == null ? "" : check)
                .replace("%vl%", vl == null ? "" : vl)
                .replace("%ping%", ping == null ? "" : ping)
                .replace("%world%", world == null ? "" : world);
    }
}
