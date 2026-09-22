package dev.aquaguard.util;

/**
 * Допуск скорости. Множитель атрибута movement_speed (ваниль 0.1) уже включает зелья скорости.
 * Лёд и soul speed — отдельные бонусы трения, их атрибут не покрывает.
 */
public final class SpeedMath {
    private SpeedMath() {}

    public static double attributeMultiplier(double movementSpeedAttribute) {
        if (movementSpeedAttribute <= 0) return 1.0;
        return movementSpeedAttribute / 0.1;
    }

    public static double tickAllowed(double base,
                                     double attributeMultiplier,
                                     double environmentBonus,
                                     double margin) {
        return base * Math.max(0.2, attributeMultiplier) + Math.max(0, environmentBonus) + margin;
    }

    public static double bpsAllowed(double baseBps,
                                    double attributeMultiplier,
                                    double environmentMultiplier,
                                    double liquidMultiplier,
                                    double marginBps) {
        return baseBps
                * Math.max(0.2, attributeMultiplier)
                * Math.max(1.0, environmentMultiplier)
                * Math.max(1.0, liquidMultiplier)
                + marginBps;
    }

    /** Примерная высота прыжка: ваниль ~1.25, Jump Boost добавляет примерно по блоку на уровень. */
    public static double maxJumpHeight(int jumpBoostAmplifier, double jumpStrengthAttribute) {
        int level = Math.max(0, jumpBoostAmplifier + 1);
        if (jumpBoostAmplifier < 0) level = 0;
        double strengthScale = jumpStrengthAttribute > 0 ? jumpStrengthAttribute / 0.42 : 1.0;
        return (1.35 + level * 1.05) * Math.max(0.5, strengthScale);
    }
}
