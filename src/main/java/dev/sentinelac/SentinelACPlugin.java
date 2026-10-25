package dev.sentinelac;

import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentinelACPlugin extends JavaPlugin {
    private EvidenceStore evidenceStore;
    private ConnectionGuard connectionGuard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getConfig().getBoolean("storage.enabled", true)) {
            try {
                evidenceStore = new EvidenceStore(this);
                evidenceStore.open();
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "Evidence storage could not be initialized; disabling plugin.", ex);
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }

        connectionGuard = new ConnectionGuard(this);
        getServer().getPluginManager().registerEvents(connectionGuard, this);
        SentinelCommand command = new SentinelCommand(this);
        getCommand("sentinel").setExecutor(command);
        getCommand("sentinel").setTabCompleter(command);
        getLogger().info("SentinelAC enabled. Connection protection: "
                + getConfig().getBoolean("modules.connection.enabled", true));
    }

    @Override
    public void onDisable() {
        if (evidenceStore != null) {
            try {
                evidenceStore.close();
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Evidence storage shutdown did not complete cleanly.", ex);
            }
        }
    }

    public EvidenceStore getEvidenceStore() {
        return evidenceStore;
    }

    public ConnectionGuard getConnectionGuard() {
        return connectionGuard;
    }
}
