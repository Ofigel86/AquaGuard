package dev.aquaguard.util;

/**
 * Разбор интервалов кликов/ударов: CPS, стандартное отклонение и коэффициент вариации.
 * Ровный высокий CPS — типичная подпись автокликера, а не просто «быстрые руки».
 */
public final class ClickPattern {
    private ClickPattern() {}

    public record Result(int samples, double cps, double meanMs, double stdMs, double cv, boolean robotic) {}

    public static Result analyze(long[] timestamps, double minCps, double maxStdMs, double maxCv) {
        if (timestamps == null || timestamps.length < 2) {
            return new Result(0, 0, 0, 999, 1, false);
        }
        int n = timestamps.length - 1;
        double[] iv = new double[n];
        for (int i = 1; i < timestamps.length; i++) {
            iv[i - 1] = timestamps[i] - timestamps[i - 1];
        }
        double mean = MathUtil.mean(iv);
        double std = MathUtil.stdDev(iv);
        double cv = mean > 0 ? std / mean : 1;
        double secs = Math.max(0.05, (timestamps[timestamps.length - 1] - timestamps[0]) / 1000.0);
        double cps = n / secs;
        boolean robotic = cps >= minCps && n >= 6 && (std <= maxStdMs || cv <= maxCv);
        return new Result(n, cps, mean, std, cv, robotic);
    }
}
