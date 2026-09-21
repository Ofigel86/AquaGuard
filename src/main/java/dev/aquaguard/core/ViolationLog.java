package dev.aquaguard.core;

import dev.aquaguard.AquaGuard;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ViolationLog {
    private final AquaGuard plugin;
    private final Object lock = new Object();

    public ViolationLog(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public void write(FlagRecord record) {
        if (!plugin.getConfig().getBoolean("log.enabled", true)) return;
        String line = String.format("%s | %s | %s | %s | vl=%.1f total=%.1f ping=%d world=%s | %s%n",
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                record.name(), record.uuid(), record.check(), record.vl(), record.total(),
                record.ping(), record.world(), record.debug());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> append(line));
    }

    private void append(String line) {
        synchronized (lock) {
            File dir = new File(plugin.getDataFolder(), "logs");
            if (!dir.exists() && !dir.mkdirs() && !dir.exists()) return;
            File file = new File(dir, "violations-" + LocalDate.now() + ".log");
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(line);
            } catch (IOException ex) {
                plugin.getLogger().warning("log: " + ex.getMessage());
            }
        }
    }
}
