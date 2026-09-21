package dev.aquaguard.util;

/**
 * Допустимая дистанция удара с учётом пинга, TPS и запаса.
 * База — атрибут entity_interaction_range (в ванилле 3.0), а не «магические» 6 блоков.
 */
public final class ReachMath {
    private ReachMath() {}

    public static double allowed(double baseReach,
                                 double pingCoeff,
                                 double mixPingCoeff,
                                 double extraMargin,
                                 int attackerPing,
                                 int victimPing,
                                 double tps,
                                 double bedrockExtra) {
        int ping = MathUtil.clamp(attackerPing, 0, 400);
        int mix = MathUtil.clamp((Math.max(0, attackerPing) + Math.max(0, victimPing)) / 2, 0, 400);
        double tpsScale = MathUtil.clamp(20.0 / Math.max(18.0, tps), 1.0, 1.25);
        double allowed = baseReach + pingCoeff * ping + mixPingCoeff * mix + extraMargin + Math.max(0, bedrockExtra);
        return allowed * tpsScale;
    }
}
