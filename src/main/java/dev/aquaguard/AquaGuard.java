package dev.aquaguard;

import dev.aquaguard.checks.MovementListener;
import dev.aquaguard.checks.VelocityListener;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.ViolationManager;
import dev.aquaguard.owner.OwnerAccess;
import dev.aquaguard.penalty.PenaltyManager;
import dev.aquaguard.cmd.AgCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class AquaGuard extends JavaPlugin {
    private static AquaGuard instance;
    private ViolationManager violationManager;
    private DataManager dataManager;
    private PenaltyManager penaltyManager;
    private OwnerAccess ownerAccess;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.violationManager = new ViolationManager(this);
        this.violationManager.load();

        this.dataManager = new DataManager();

        this.penaltyManager = new PenaltyManager(this, violationManager);

        this.ownerAccess = new OwnerAccess(this);
        getServer().getPluginManager().registerEvents(ownerAccess, this);

        getServer().getPluginManager().registerEvents(dataManager, this);
        getServer().getPluginManager().registerEvents(new MovementListener(this, dataManager, violationManager), this);
        getServer().getPluginManager().registerEvents(new VelocityListener(dataManager), this);
        getServer().getPluginManager().registerEvents(penaltyManager, this);

        long minute = 20L * 60L;
        getServer().getScheduler().runTaskTimer(this,
                () -> violationManager.decayAll(getConfig().getDouble("decay-per-minute", 0.5)),
                minute, minute);

        if (getCommand("ag") != null) {
            getCommand("ag").setExecutor(new AgCommand(this, violationManager, penaltyManager, ownerAccess));
        }

        getLogger().info("AquaGuard test build enabled.");
    }

    @Override
    public void onDisable() {
        violationManager.save();
    }

    public static AquaGuard get() { return instance; }
}