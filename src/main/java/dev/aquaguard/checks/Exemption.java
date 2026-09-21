package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.Compat;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class Exemption {
    private final AquaGuard plugin;

    public Exemption(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public boolean bypass(Player player) {
        if (player == null) return true;
        if (plugin.bypass().isBypassed(player.getUniqueId())) return true;
        if (plugin.getConfig().getBoolean("respect-bypass-permission", true) && player.hasPermission("ag.bypass")) return true;
        return plugin.getConfig().getBoolean("owner.default-bypass", false) && plugin.owner().isOwner(player);
    }

    public boolean worldExempt(Player player) {
        List<String> worlds = plugin.getConfig().getStringList("exempt-worlds");
        String name = player.getWorld().getName();
        for (String world : worlds) {
            if (world.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public boolean creativeLike(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    public boolean lagging(Player player) {
        if (!plugin.getConfig().getBoolean("gates.cancel-sensitive-checks", true)) return false;
        if (Compat.ping(player) > plugin.getConfig().getInt("gates.max-ping-ms", 320)) return true;
        return Compat.tps() < plugin.getConfig().getDouble("gates.min-tps", 17.5);
    }

    public boolean grace(PlayerData data) {
        long now = System.currentTimeMillis();
        long join = plugin.getConfig().getLong("gates.join-grace-ms", 2000);
        long teleport = plugin.getConfig().getLong("gates.teleport-grace-ms", 900);
        return now - data.joinMs < join || now - data.lastTeleportMs < teleport;
    }

    public boolean bedrock(PlayerData data) {
        String brand = data.clientBrand == null ? "" : data.clientBrand.toLowerCase(Locale.ROOT);
        return brand.contains("geyser") || brand.contains("floodgate");
    }

    public double bedrockSpeedExtra() {
        return plugin.getConfig().getDouble("bedrock.speed-extra", 0.08);
    }

    public double bedrockReachExtra() {
        return plugin.getConfig().getDouble("bedrock.reach-extra", 0.35);
    }
}
