package dev.aquaguard.cmd;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.ViolationManager;
import dev.aquaguard.gui.GuiManager;
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
    private final GuiManager gui;

    public AgCommand(AquaGuard plugin,
                     ViolationManager vl,
                     PenaltyManager penalties,
                     OwnerAccess owner,
                     GuiManager gui) {
        this.plugin = plugin;
        this.vl = vl;
        this.penalties = penalties;
        this.owner = owner;
        this.gui = gui;
    }

    private boolean auth(CommandSender s) {
        if (owner.isOwner(s)) return true; // владелец или ag.admin
        s.sendMessage("Нет прав.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(s);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "ping" -> {
                if (!auth(s)) return true;
                boolean sb = plugin.getConfig().getBoolean("setback.enabled", true);
                s.sendMessage("AquaGuard OK | penalties=" + penalties.mode().name().toLowerCase()
                        + " | setback=" + (sb ? "on" : "off"));
                return true;
            }

            case "gui" -> {
                if (!auth(s)) return true;
                if (s instanceof Player p) {
                    gui.openMain(p);
                } else s.sendMessage("Только в игре.");
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
                        if (op != null && (op.isOnline() || op.hasPlayedBefore())) {
                            target = op.getUniqueId(); name = op.getName();
                        }
                    }
                } else if (s instanceof Player p) { target = p.getUniqueId(); name = p.getName(); }
                if (target == null) { s.sendMessage("Игрок не найден."); return true; }

                Map<String, Double> map = vl.get(target);
                if (map.isEmpty()) { s.sendMessage("VL пуст для " + name); return true; }

                s.sendMessage("VL для " + name + ":");
                map.entrySet().stream()
                        .sorted(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed())
                        .forEach(e -> s.sendMessage(" - " + e.getKey() + ": " + String.format("%.1f", e.getValue())));
                s.sendMessage("Итого: " + String.format("%.1f", vl.total(target)));
                return true;
            }

            case "penalties" -> {
                if (!auth(s)) return true;
                if (args.length < 2) {
                    s.sendMessage("Текущий режим: " + penalties.mode().name().toLowerCase());
                    s.sendMessage("Использование: /" + label + " penalties <off|simulate|soft|hard>");
                    return true;
                }
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

            case "setback" -> {
                if (!auth(s)) return true;
                if (args.length < 2) {
                    boolean sb = plugin.getConfig().getBoolean("setback.enabled", true);
                    s.sendMessage("Setback сейчас: " + (sb ? "on" : "off"));
                    s.sendMessage("Использование: /" + label + " setback <on|off|toggle>");
                    return true;
                }
                String v = args[1].toLowerCase();
                boolean cur = plugin.getConfig().getBoolean("setback.enabled", true);
                boolean next = switch (v) {
                    case "on" -> true;
                    case "off" -> false;
                    case "toggle" -> !cur;
                    default -> { s.sendMessage("on|off|toggle"); yield cur; }
                };
                plugin.getConfig().set("setback.enabled", next);
                plugin.saveConfig();
                s.sendMessage("Setback: " + (next ? "on" : "off"));
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
                if (s instanceof Player p) {
                    s.sendMessage("Ты " + p.getName() + " UUID=" + p.getUniqueId()
                            + (owner.isOwner(s) ? " [OWNER]" : ""));
                } else {
                    s.sendMessage("Консоль [OWNER]");
                }
                return true;
            }

            default -> {
                sendHelp(s);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("/ag ping — статус");
        s.sendMessage("/ag gui — открыть GUI");
        s.sendMessage("/ag vl [ник] — показать VL");
        s.sendMessage("/ag penalties <off|simulate|soft|hard> — режим уреза урона");
        s.sendMessage("/ag setback <on|off|toggle> — переключить setback");
        s.sendMessage("/ag reload — перезагрузка конфига");
        s.sendMessage("/ag whoami — показать твой UUID/статус");
    }
}
