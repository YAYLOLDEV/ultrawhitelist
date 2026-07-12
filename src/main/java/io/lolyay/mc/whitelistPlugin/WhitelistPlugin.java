package io.lolyay.mc.whitelistPlugin;

import io.lolyay.mc.whitelistPlugin.command.ConfirmationManager;
import io.lolyay.mc.whitelistPlugin.command.WhitelistCommand;
import io.lolyay.mc.whitelistPlugin.core.WhitelistService;
import io.lolyay.mc.whitelistPlugin.listener.LoginListener;
import io.lolyay.mc.whitelistPlugin.storage.SqliteStorage;
import io.lolyay.mc.whitelistPlugin.storage.Storage;
import io.lolyay.mc.whitelistPlugin.util.PlayerDbClient;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public final class WhitelistPlugin extends JavaPlugin {

    private Storage storage;
    @Getter
    @Accessors(fluent = true)
    private WhitelistService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            configurePlayerDb();
        } catch (IllegalArgumentException e) {
            getLogger().log(Level.SEVERE, "Invalid playerDbUrl - disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create data folder - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        storage = new SqliteStorage(new File(getDataFolder(), "whitelist.db"), getLogger());
        service = new WhitelistService(storage);
        try {
            storage.init();
            service.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialise storage - disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        int pluginId = 32575;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new SingleLineChart("whitelist_lists", () -> service.lists().size()));

        ConfirmationManager confirm = new ConfirmationManager();
        WhitelistCommand command = new WhitelistCommand(this, service, confirm);
        PluginCommand wl = getCommand("wl");
        if (wl != null) {
            wl.setExecutor(command);
            wl.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'wl' is missing from plugin.yml!");
        }

        getServer().getPluginManager().registerEvents(new LoginListener(service), this);

        getLogger().info("UltraWhitelist enabled. enabled=" + service.isEnabled()
                + ", activeList=" + service.activeListName()
                + ", lists=" + service.lists().size());
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        configurePlayerDb();
    }

    private void configurePlayerDb() {
        PlayerDbClient.configure(getConfig().getString("playerDbUrl", PlayerDbClient.defaultUrl));
    }
}
