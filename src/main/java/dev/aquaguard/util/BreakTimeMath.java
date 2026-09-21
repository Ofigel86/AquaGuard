package dev.aquaguard.util;

/**
 * Упрощённая ванильная формула времени добычи блока.
 * Флагаем только если сломали заметно быстрее оценки — запас гасит неточности (вода, неверный инструмент).
 */
public final class BreakTimeMath {
    private BreakTimeMath() {}

    /**
     * @param hardness           твёрдость блока, &lt;= 0 означает мгновенно или неломаемо
     * @param toolSpeed          скорость инструмента (рука 1, дерево 2, камень 4, железо 6, алмаз 8, незерит 9, золото 12)
     * @param correctTool        подходящий инструмент
     * @param efficiencyLevel    уровень Efficiency, 0 если нет
     * @param hasteAmplifier     -1 если эффекта нет
     * @param blockBreakSpeed    атрибут block_break_speed, ваниль 1.0
     */
    public static long expectedMillis(float hardness,
                                      double toolSpeed,
                                      boolean correctTool,
                                      int efficiencyLevel,
                                      int hasteAmplifier,
                                      double blockBreakSpeed) {
        if (hardness <= 0f) return 0L;
        double speed = Math.max(0.1, toolSpeed);
        if (correctTool && efficiencyLevel > 0) {
            speed += (efficiencyLevel * efficiencyLevel) + 1.0;
        }
        if (hasteAmplifier >= 0) {
            speed *= 1.0 + 0.2 * (hasteAmplifier + 1);
        }
        speed *= Math.max(0.1, blockBreakSpeed);
        double damage = speed / hardness / (correctTool ? 30.0 : 100.0);
        if (damage <= 0) return Long.MAX_VALUE / 4;
        int ticks = (int) Math.ceil(1.0 / damage);
        return ticks * 50L;
    }

    public static int toolSpeed(String materialName) {
        if (materialName == null) return 1;
        String n = materialName.toUpperCase();
        if (n.startsWith("NETHERITE_")) return 9;
        if (n.startsWith("DIAMOND_")) return 8;
        if (n.startsWith("IRON_")) return 6;
        if (n.startsWith("STONE_")) return 4;
        if (n.startsWith("GOLDEN_")) return 12;
        if (n.startsWith("WOODEN_") || n.startsWith("WOOD_")) return 2;
        return 1;
    }
}
