package dev.aquaguard.util;

/**
 * Чистая математика чеков. Без Bukkit, чтобы формулы можно было гонять тестами.
 */
public final class MathUtil {
    private MathUtil() {}

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Наименьшая разница двух углов в градусах, 0..180. */
    public static double angleDiff(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        if (d > 180f) d = 360f - d;
        return d;
    }

    public static double angleDiff(double a, double b) {
        return angleDiff((float) a, (float) b);
    }

    public static double mean(double[] values) {
        if (values.length == 0) return 0;
        double s = 0;
        for (double v : values) s += v;
        return s / values.length;
    }

    /** Популяционное стандартное отклонение. */
    public static double stdDev(double[] values) {
        if (values.length == 0) return 0;
        double m = mean(values);
        double var = 0;
        for (double v : values) {
            double d = v - m;
            var += d * d;
        }
        return Math.sqrt(var / values.length);
    }

    public static double coefficientOfVariation(double[] values) {
        double m = mean(values);
        if (m <= 1e-9) return 1;
        return stdDev(values) / m;
    }

    /** Горизонтальный вектор взгляда Minecraft: yaw 0 = +Z, yaw 90 = -X. */
    public static double[] lookXZ(double yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    /**
     * Косинус угла между взглядом и горизонтальным вектором, нормированный.
     * +1 строго вперёд, -1 строго назад, 0 вбок.
     */
    public static double forwardDot(double yawDeg, double vx, double vz) {
        double len = Math.hypot(vx, vz);
        if (len < 1e-8) return 0;
        double[] look = lookXZ(yawDeg);
        return (look[0] * vx + look[1] * vz) / len;
    }

    /** Угол в градусах между двумя горизонтальными векторами, 0..180. */
    public static double horizontalAngle(double ax, double az, double bx, double bz) {
        double la = Math.hypot(ax, az);
        double lb = Math.hypot(bx, bz);
        if (la < 1e-8 || lb < 1e-8) return 0;
        double dot = clamp((ax * bx + az * bz) / (la * lb), -1, 1);
        return Math.toDegrees(Math.acos(dot));
    }

    /** Дистанция от точки до ближайшей точки AABB. 0, если точка внутри. */
    public static double distanceToAabb(double x, double y, double z,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        double cx = clamp(x, minX, maxX);
        double cy = clamp(y, minY, maxY);
        double cz = clamp(z, minZ, maxZ);
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean aabbOverlap(double aMinX, double aMinY, double aMinZ,
                                      double aMaxX, double aMaxY, double aMaxZ,
                                      double bMinX, double bMinY, double bMinZ,
                                      double bMaxX, double bMaxY, double bMaxZ) {
        return aMinX < bMaxX && aMaxX > bMinX
                && aMinY < bMaxY && aMaxY > bMinY
                && aMinZ < bMaxZ && aMaxZ > bMinZ;
    }
}
