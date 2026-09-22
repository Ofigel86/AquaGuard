package dev.aquaguard.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Мелкие различия Paper 1.21.x: имена атрибутов GENERIC_* против коротких, TPS, бренд клиента.
 */
public final class Compat {
    private Compat() {}

    public static int ping(Player player) {
        try {
            return Math.max(0, player.getPing());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static double tps() {
        try {
            double[] tps = Bukkit.getServer().getTPS();
            if (tps.length > 0) return Math.min(20.0, tps[0]);
        } catch (Throwable ignored) {
        }
        return 20.0;
    }

    public static double averageTickMs() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getAverageTickTime");
            Object v = m.invoke(Bukkit.getServer());
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {
        }
        return 50.0;
    }

    public static String clientBrand(Player player) {
        try {
            Method m = player.getClass().getMethod("getClientBrandName");
            Object v = m.invoke(player);
            return v == null ? "" : v.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static double attribute(Player player, String key, double def) {
        Attribute attr = attributeByKey(key);
        if (attr == null || player == null) return def;
        try {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) return def;
            return inst.getValue();
        } catch (Throwable ignored) {
            return def;
        }
    }

    public static Attribute attributeByKey(String key) {
        if (key == null || key.isEmpty()) return null;
        String upper = key.toUpperCase(Locale.ROOT);
        Attribute found = fieldAttr(upper);
        if (found != null) return found;
        found = fieldAttr("GENERIC_" + upper);
        if (found != null) return found;
        found = registryAttr(key.toLowerCase(Locale.ROOT));
        if (found != null) return found;
        return enumAttr(upper);
    }

    private static Attribute fieldAttr(String name) {
        try {
            Field field = Attribute.class.getField(name);
            Object value = field.get(null);
            if (value instanceof Attribute attribute) return attribute;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Attribute registryAttr(String key) {
        try {
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Attribute enumAttr(String name) {
        try {
            if (!Attribute.class.isEnum()) return null;
            return (Attribute) Enum.valueOf((Class) Attribute.class, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isCritical(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        try {
            Method m = event.getClass().getMethod("isCritical");
            Object v = m.invoke(event);
            return v instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
