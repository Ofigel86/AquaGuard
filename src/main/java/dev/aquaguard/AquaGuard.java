package dev.aquaguard;

import dev.aquaguard.bypass.BypassManager;
import dev.aquaguard.checks.*;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.ViolationManager;
import dev.aquaguard.freeze.FreezeManager;
import dev.aquaguard.gui.GuiManager;
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

    // services
    private BypassManager bypassManager;
    private CheckManager checkManager;
    private FreezeManager freezeManager;

    // gui
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // core
        this.violationManager = new ViolationManager(this);
        this.violationManager.load();
        this.dataManager = new DataManager();
        this.penaltyManager = new PenaltyManager(this, violationManager);
        this.ownerAccess = new OwnerAccess(this);

        // services
        this.bypassManager = new BypassManager(this);
        this.checkManager = new CheckManager(this);
        this.freezeManager = new FreezeManager();

        // gui
        this.guiManager = new GuiManager(this, violationManager, penaltyManager, checkManager, bypassManager, freezeManager);

        var pm = getServer().getPluginManager();
        pm.registerEvents(ownerAccess, this);
        pm.registerEvents(dataManager, this);
        pm.registerEvents(new MovementListener(this, dataManager, violationManager, checkManager, bypassManager, freezeManager), this);
        pm.registerEvents(new VelocityListener(dataManager), this);
        pm.registerEvents(new WorldListener(this, dataManager, violationManager, checkManager, bypassManager), this);
        pm.registerEvents(new CombatListener(this, dataManager, violationManager, checkManager, bypassManager), this);
        pm.registerEvents(penaltyManager, this);
        pm.registerEvents(guiManager, this);

        long minute = 20L * 60L;
        getServer().getScheduler().runTaskTimer(this,
                () -> violationManager.decayAll(getConfig().getDouble("decay-per-minute", 0.5)),
                minute, minute);

        if (getCommand("ag") != null) {
            getCommand("ag").setExecutor(new AgCommand(this, violationManager, penaltyManager, ownerAccess, guiManager, bypassManager, checkManager, freezeManager));
        }

        getLogger().info("AquaGuard enabled.");
    }

    @Override
    public void onDisable() {
        violationManager.save();
        getLogger().info("AquaGuard disabled.");
    }

    public static AquaGuard get() { return instance; }
}
