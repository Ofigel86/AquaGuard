package dev.aquaguard.cmd;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.ViolationManager;
import dev.aquaguard.owner.OwnerAccess;
import dev.aquaguard.penalty.PenaltyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public class AgCommand implements CommandExecutor {
    private final AquaGuard plugin;
    private final ViolationManager vl;
    private final PenaltyManager penalties;
    private final OwnerAccess owner;

    public AgCommand(AquaGuard plugin, ViolationManager vl, PenaltyManager penalties, OwnerAccess owner) {
        this.plugin = plugin; this.vl = vl; this.penalties = penalties; this.owner = owner;
    }

    private boolean auth(CommandSender s) {
        if (!owner.isOwner(s)) { s.sendMessage("Нет прав."); return false; }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            s.sendMessage("/ag ping | /ag vl [ник] | /ag penalties <off|simulate|soft|hard> | /ag reload | /ag whoami");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "ping" -> {
                if (!auth(s)) return true;
                s.sendMessage("AquaGuard OK. Silent=" + plugin.getConfig().getBoolean("silent-mode", true)
                        + " Penalties=" + penalties.mode().name().toLowerCase());
                return true;
            }
            case "vl" -> {
                if (!auth(s)) return true;
                UUID target = null; String name = null;
                if (args.length >= 2) {
                    Player p = Bukkit.getPlayerExact(args[1]);
                    if (p != null) { target = p.getUniqueId(); name = p.getName(); }
                    else {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                        if (op != null && (op.hasPlayedBefore() || op.isOnline())) { target = op.getUniqueId(); name = op.getName(); }
                    }
                } else if (s instanceof Player p) { target = p.getUniqueId(); name = p.getName(); }
                if (target == null) { s.sendMessage("Игрок не найден."); return true; }
                Map<String, Double> map = vl.get(target);
                if (map.isEmpty()) { s.sendMessage("VL пуст для " + name); return true; }
                s.sendMessage("VL для " + name + ":");
                map.entrySet().stream().sorted(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed())
                        .forEach(e -> s.sendMessage(" - " + e.getKey() + ": " + String.format("%.1f", e.getValue())));
                s.sendMessage("Итого: " + String.format("%.1f", vl.total(target)));
                return true;
            }
            case "penalties" -> {
                if (!auth(s)) return true;
                if (args.length < 2) { s.sendMessage("Текущий режим: " + penalties.mode().name().toLowerCase()); return true; }
                switch (args[1].toLowerCase()) {
                    case "off" -> penalties.setMode(PenaltyManager.Mode.OFF);
                    case "simulate" -> penalties.setMode(PenaltyManager.Mode.SIMULATE);
                    case "soft" -> penalties.setMode(PenaltyManager.Mode.SOFT);
                    case "hard" -> penalties.setMode(PenaltyManager.Mode.HARD);
                    default -> { s.sendMessage("off|simulate|soft|hard"); return true; }
                }
                s.sendMessage("Penalties: " + penalties.mode().name().toLowerCase());
                return true;
            }
            case "reload" -> {
                if (!auth(s)) return true;
                plugin.reloadConfig();
                owner.reload();
                s.sendMessage("AquaGuard config reloaded.");
                return true;
            }
            case "whoami" -> {
                s.sendMessage(owner.whoAmI(s));
                return true;
            }
        }
        return false;
    }
}