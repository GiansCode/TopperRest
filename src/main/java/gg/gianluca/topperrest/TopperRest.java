package gg.gianluca.topperrest;

import gg.gianluca.topperrest.cache.TopperCache;
import gg.gianluca.topperrest.config.RestConfig;
import gg.gianluca.topperrest.geyser.FloodgateHook;
import gg.gianluca.topperrest.listener.EntryUpdateListener;
import gg.gianluca.topperrest.rest.RestServer;
import gg.gianluca.topperrest.service.TopperService;
import me.hsgamer.topper.spigot.plugin.TopperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class TopperRest extends JavaPlugin implements CommandExecutor {

    private RestServer restServer;
    private TopperService topperService;
    private FloodgateHook floodgateHook;
    private RestConfig restConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!initPlugin()) {
            Bukkit.getPluginManager().disablePlugin(this);
        }

        var cmd = getCommand("topperrest");
        if (cmd != null) cmd.setExecutor(this);
    }

    private boolean initPlugin() {
        restConfig = new RestConfig(getConfig());

        // Floodgate (Bedrock player support) — optional
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                floodgateHook = new FloodgateHook();
                getLogger().info("Floodgate (Bedrock) support enabled.");
            } catch (Exception e) {
                getLogger().warning("Floodgate found but hook failed to initialise: " + e.getMessage());
            }
        }

        // Topper dependency check
        Plugin topperPlugin = Bukkit.getPluginManager().getPlugin("Topper");
        if (!(topperPlugin instanceof TopperPlugin)) {
            getLogger().severe("Topper plugin not found or is not a recognised version. Disabling TopperRest.");
            return false;
        }

        TopperCache cache = new TopperCache(restConfig.getCacheTtlSeconds(), restConfig.getPlayerNameTtlSeconds());
        topperService = new TopperService((TopperPlugin) topperPlugin, this, cache, floodgateHook);

        // Listen for Topper data-update events to keep caches fresh
        Bukkit.getPluginManager().registerEvents(new EntryUpdateListener(topperService), this);

        // Start the REST server
        restServer = new RestServer(this, topperService, restConfig);
        try {
            restServer.start();
            getLogger().info("REST server started on " + restConfig.getBindAddress() + ":" + restConfig.getPort());
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to start REST server: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (restServer != null) {
            restServer.stop();
            getLogger().info("REST server stopped.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("topperrest")) return false;
        if (!sender.hasPermission("topperrest.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§eTopperRest §7v" + getDescription().getVersion());
            sender.sendMessage("§7Usage: /topperrest reload");
            return true;
        }

        sender.sendMessage("§eReloading TopperRest...");
        if (restServer != null) restServer.stop();

        reloadConfig();
        boolean ok = initPlugin();
        if (ok) {
            sender.sendMessage("§aTopperRest reloaded successfully.");
        } else {
            sender.sendMessage("§cTopperRest reload failed. Check console for errors.");
        }
        return true;
    }

    public RestConfig getRestConfig() {
        return restConfig;
    }
}
