package dev.aquaguard.util;

/**
 * Очки scaffold. Обычная стройка почти не набирает порог:
 * нужны мост назад + взгляд вниз + линия блоков, а не просто «поставил блок снизу».
 */
public final class ScaffoldMath {
    private ScaffoldMath() {}

    public static int placementScore(boolean belowFeet,
                                     boolean extendingLine,
                                     boolean sneaking,
                                     boolean sprinting,
                                     double pitch,
                                     double forwardDot,
                                     boolean lookingAtBlock) {
        int score = 0;
        boolean bridgingBack = belowFeet && forwardDot < -0.15;
        if (bridgingBack) score += 2;
        if (extendingLine && belowFeet) score += 2;
        if (pitch > 70 && belowFeet) score += 2;
        if (sneaking && belowFeet) score += 1;
        if (sprinting && belowFeet) score += 1;
        if (!lookingAtBlock && belowFeet) score += 2;
        if (!belowFeet && pitch < 60) score -= 1;
        return score;
    }
}
