package com.pablo67340.guishop;

import com.pablo67340.guishop.commands.*;
import com.pablo67340.guishop.config.Config;
import com.pablo67340.guishop.definition.CommandsMode;
import com.pablo67340.guishop.definition.Item;
import com.pablo67340.guishop.definition.MenuItem;
import com.pablo67340.guishop.definition.MenuPage;
import com.pablo67340.guishop.listenable.Menu;
import com.pablo67340.guishop.listenable.PlayerListener;
import com.pablo67340.guishop.listenable.Shop;
import com.pablo67340.guishop.gui.GuiListener;
import com.pablo67340.guishop.economy.DynamicPricingManager;
import com.pablo67340.guishop.listenable.editor.ChatInputHandler;
import com.pablo67340.guishop.statistics.GUIShopPlaceholderExpansion;
import com.pablo67340.guishop.statistics.StatisticsManager;
import com.pablo67340.guishop.util.ConfigManager;
import com.pablo67340.guishop.util.LogUtil;
import com.pablo67340.guishop.util.MiscUtils;
import com.pablo67340.guishop.util.RowChart;
import com.pablo67340.guishop.util.SchedulerUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class GUIShop extends JavaPlugin {

    /**
     * An instance of this class.
     */
    @Getter
    public static GUIShop INSTANCE;

    /**
     * A {@link Set} that will store every command that can be used by a
     * {@link Player} to open the {@link Menu}.
     */
    @Getter
    public static final Set<String> BUY_COMMANDS = new HashSet<>();

    @Getter
    @Setter
    public static boolean noEconomySystem = false;

    @Getter
    public Map<String, Object> loadedShops = new HashMap<>();

    @Getter
    @Setter
    private MenuItem loadedMenu = null;

    @Getter
    private final Map<String, List<Item>> ITEMTABLE = new HashMap<>();

    /**
     * A {@link Map} that will store our Creators when the server first starts.
     */
    @Getter
    public static final List<UUID> CREATOR = new ArrayList<>();
    
    /**
     * A {@link Set} that tracks players who have item info debug mode enabled.
     * When enabled, inventory interactions will log PDC/NBT data to console.
     */
    @Getter
    public static final Set<UUID> ITEM_INFO_DEBUG = new HashSet<>();

    public static final RowChart rowChart = new RowChart();

    @Getter
    public CommandManager commandManager;
    
    private GUIShopPlaceholderExpansion placeholderExpansion;

    @Getter
    public ConfigManager configManager;

    @Getter
    public MiscUtils miscUtils;

    @Getter
    @Setter
    public Boolean isReload = false;

    @Getter
    @Setter
    public LogUtil logUtil;

    /**
     * The statistics manager for tracking player shop transactions.
     */
    @Getter
    private StatisticsManager statisticsManager;

    /**
     * The scheduled task for log flushing, used to cancel on disable.
     */
    private final SchedulerUtil.TaskHolder logFlushTask = new SchedulerUtil.TaskHolder();

    @Override
    public void onEnable() {
        INSTANCE = this;
        
        // Initialize Folia/Paper/Spigot scheduler compatibility
        SchedulerUtil.init(this);

        this.configManager = new ConfigManager();
        this.logUtil = new LogUtil();
        this.commandManager = new CommandManager();
        this.miscUtils = new MiscUtils(); // Must be initialized before initConfigs() for dynamic pricing
        this.configManager.initConfigs();
        

        warmup();
        initWriteCache();

        // Hook into whatever economy plugin is registered with Vault. Economy plugins
        // that enable after GUIShop (CMI, for example) are picked up by the retry below,
        // so the rest of the plugin is always registered regardless of load order.
        resolveEconomy(true);

        getServer().getPluginManager().registerEvents(PlayerListener.INSTANCE, this);
        getServer().getPluginManager().registerEvents(GuiListener.getInstance(), this);
        
        // Register guishop command with tab completion
        getServer().getPluginCommand("guishop").setExecutor(new GuishopCommand());
        getServer().getPluginCommand("guishop").setTabCompleter(new com.pablo67340.guishop.commands.GuishopTabCompleter());
        
        getServer().getPluginCommand("guishopuser").setExecutor(new UserCommand());

        // Initialize Statistics System
        initStatistics();
    }

    @Override
    public void onDisable() {
        // Cancel the log flush task
        SchedulerUtil.cancelTask(logFlushTask);

        // Flush any remaining logs to disk
        if (logUtil != null) {
            logUtil.flushLogs();
        }

        // IMPORTANT: Reset static instances FIRST so getInstance() returns null
        // This prevents "connection closed" errors during plugin reload
        StatisticsManager.resetInstance();
        DynamicPricingManager.resetInstance();
        GuiListener.resetInstance();
        ChatInputHandler.resetInstance();

        // Unregister PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }

        // Shutdown statistics system
        if (statisticsManager != null) {
            statisticsManager.shutdown();
        }
        
        // Shutdown built-in dynamic pricing system
        if (dynamicPricingManager != null) {
            dynamicPricingManager.shutdown();
        }
    }

    /**
     * Initialize the Statistics system and PlaceholderAPI expansion.
     */
    private void initStatistics() {
        try {
            statisticsManager = new StatisticsManager(this);
            statisticsManager.initialize();
            
            // Register PlaceholderAPI expansion if available
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                // Unregister old expansion first (important for PlugMan reloads)
                if (placeholderExpansion != null) {
                    placeholderExpansion.unregister();
                }
                placeholderExpansion = new GUIShopPlaceholderExpansion(this);
                placeholderExpansion.register();
                getLogUtil().log("PlaceholderAPI expansion registered.");
            }
            
            // Load stats for all currently online players (important for PlugMan reloads)
            for (Player player : Bukkit.getOnlinePlayers()) {
                statisticsManager.loadPlayerCache(player);
                statisticsManager.loadPreferencesCache(player.getUniqueId());
            }
            
        } catch (Exception e) {
            getLogUtil().log("Failed to initialize Statistics: " + e.getMessage());
            if (Config.isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Number of times {@link #resolveEconomy(boolean)} has already retried.
     */
    private int economyRetries = 0;

    /**
     * Name of the economy plugin currently hooked, used to log a change only once.
     */
    private String hookedEconomyName = null;

    /**
     * How many times to retry hooking Vault's economy before giving up.
     * Economy plugins such as CMI enable well after GUIShop, so the first
     * attempt during onEnable() usually finds nothing.
     */
    private static final int MAX_ECONOMY_RETRIES = 40;

    /**
     * Hooks into the economy plugin registered with Vault.
     * <p>
     * GUIShop has no economy of its own — it always spends the balance owned by whatever
     * economy plugin is registered with Vault (CMI, EssentialsX, ...). Because Bukkit may
     * enable GUIShop before that plugin, a failed lookup is not fatal: the lookup is simply
     * retried on a delay until an economy shows up.
     *
     * @param scheduleRetry whether to schedule another attempt when no economy is found yet.
     */
    public void resolveEconomy(boolean scheduleRetry) {
        if (getMiscUtils().setupEconomy()) {
            String name = String.valueOf(getMiscUtils().getECONOMY().getName());
            if (!name.equals(hookedEconomyName)) {
                getLogUtil().log("Economy hooked: " + name + " (via Vault)");
                hookedEconomyName = name;
            }
            setNoEconomySystem(false);
            economyRetries = 0;
            return;
        }

        hookedEconomyName = null;
        setNoEconomySystem(true);

        if (!scheduleRetry) {
            return;
        }

        if (economyRetries == 0) {
            getLogUtil().log("No Vault economy registered yet - waiting for an economy plugin to enable...");
        }

        if (economyRetries++ >= MAX_ECONOMY_RETRIES) {
            getLogUtil().log("Vault could not detect an economy plugin! Buying is disabled until one is installed.");
            return;
        }

        // 20 ticks: economy plugins normally register during their own onEnable, so this
        // resolves on the first retry in practice.
        SchedulerUtil.runTaskLater(() -> resolveEconomy(true), 20L);
    }

    @Getter
    private com.pablo67340.guishop.economy.DynamicPricingManager dynamicPricingManager;
    
    /**
     * Initialize the built-in dynamic pricing system.
     * This is used when no external DynamicPriceProvider is found.
     */
    public void initBuiltInDynamicPricing() {
        try {
            dynamicPricingManager = new com.pablo67340.guishop.economy.DynamicPricingManager(this);
            if (dynamicPricingManager.initialize()) {
                // Set it as the dynamic pricing provider in MiscUtils
                miscUtils.setDYNAMICPRICING(dynamicPricingManager);
                getLogUtil().log("Built-in dynamic pricing enabled.");
                getLogUtil().log("  Price change per item: " + (dynamicPricingManager.getPriceChangePerTransaction() * 100) + "%");
                getLogUtil().log("  Price bounds: " + (dynamicPricingManager.getMinPriceMultiplier() * 100) + "% - " + (dynamicPricingManager.getMaxPriceMultiplier() * 100) + "%");
            } else {
                getLogUtil().log("Failed to initialize built-in dynamic pricing.");
                dynamicPricingManager = null;
            }
        } catch (Exception e) {
            getLogUtil().log("Error initializing built-in dynamic pricing: " + e.getMessage());
            if (Config.isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    public UserCommand getUserCommands() {
        return (UserCommand) getServer().getPluginCommand("guishopuser").getExecutor();
    }

    public void warmup() {
        long startTime = System.currentTimeMillis();
        
        try {
        new Menu().loadItems(true);
        } catch (Exception e) {
            getLogUtil().log("[Critical] Failed to load menu: " + e.getMessage());
            if (Config.isDebugMode()) {
                e.printStackTrace();
            }
        }
        
        // Only process menu items if menu loaded successfully
        if (loadedMenu != null && loadedMenu.getPages() != null) {
            // First, load shops linked from menu items
        for (MenuPage page : loadedMenu.getPages().values()) {
            for (Item item : page.getItems().values()) {
                if (item.getTargetShop() != null) {
                        try {
                    getLogUtil().debugLog("Starting Warmup for Shop: " + item.getTargetShop());
                    new Shop(item.getTargetShop()).loadItems(true);
                        } catch (Exception e) {
                            getLogUtil().log("[Warning] Failed to load shop '" + item.getTargetShop() + "': " + e.getMessage());
            }
        }
                }
            }
        } else {
            getLogUtil().log("[Warning] Menu failed to load - shops linked from menu won't be loaded.");
        }
        
        // Also load ALL shops from shops folder (including hidden ones not linked in menu)
        // This ensures items from all shops are registered in ITEMTABLE for selling/worth
        try {
            Set<String> shopNames = configManager.getShopNames();
            for (String shopName : shopNames) {
                if (!loadedShops.containsKey(shopName)) {
                    try {
                        getLogUtil().debugLog("Loading unlinked shop for worth/sell registration: " + shopName);
                        new Shop(shopName).loadItems(true);
                    } catch (Exception e) {
                        getLogUtil().log("[Warning] Failed to load shop '" + shopName + "': " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            getLogUtil().log("[Warning] Failed to load shops from config: " + e.getMessage());
        }
        
        long estimatedTime = System.currentTimeMillis() - startTime;
        getLogUtil().debugLog("Item warming completed in: " + estimatedTime + "ms");
    }

    public void reload(CommandSender sender, boolean ignoreCreator) {
        this.setIsReload(true);
        boolean hadErrors = false;
        long startTime = System.currentTimeMillis();
        
        getLogUtil().log("Starting hard reload - destroying and recreating all systems...");
        
        // ========== PHASE 1: Close all GUIShop inventories ==========
        try {
            String menuTitle = Config.getTitlesConfig().getMenuTitle().replace("%page-number%", "");
            String shopTitle = Config.getTitlesConfig().getShopTitle().replace("%shopname%", "");
            String qtyTitle = Config.getTitlesConfig().getQtyTitle();

            Bukkit.getOnlinePlayers().stream().filter(player -> {
                if (player.getOpenInventory() == null) return false;
                String title = player.getOpenInventory().getTitle();
                return title.contains(menuTitle)
                        || title.contains(shopTitle)
                        || title.contains(qtyTitle);
            }).forEach(Player::closeInventory);
            getLogUtil().debugLog("Closed all GUIShop inventories");
        } catch (Exception e) {
            getLogUtil().debugLog("Error closing inventories during reload: " + e.getMessage());
        }

        // ========== PHASE 2: Shutdown all singletons ==========

        // Shutdown statistics system
        try {
            if (statisticsManager != null) {
                statisticsManager.shutdown();
            }
            StatisticsManager.resetInstance();
            statisticsManager = null;
            getLogUtil().debugLog("Statistics manager shutdown");
        } catch (Exception e) {
            getLogUtil().log("[Warning] Error shutting down statistics: " + e.getMessage());
        }
        
        // Shutdown dynamic pricing system
        try {
            if (dynamicPricingManager != null) {
                dynamicPricingManager.shutdown();
            }
            DynamicPricingManager.resetInstance();
            dynamicPricingManager = null;
            getLogUtil().debugLog("Dynamic pricing manager shutdown");
        } catch (Exception e) {
            getLogUtil().log("[Warning] Error shutting down dynamic pricing: " + e.getMessage());
        }
        
        // Reset GUI listener
        try {
            GuiListener.resetInstance();
            getLogUtil().debugLog("GUI listener reset");
        } catch (Exception e) {
            getLogUtil().log("[Warning] Error resetting GUI listener: " + e.getMessage());
        }
        
        // Reset chat input handler
        try {
            ChatInputHandler.resetInstance();
            getLogUtil().debugLog("Chat input handler reset");
        } catch (Exception e) {
            getLogUtil().log("[Warning] Error resetting chat input handler: " + e.getMessage());
        }

        // ========== PHASE 3: Clear all cached data ==========
        ITEMTABLE.clear();
        BUY_COMMANDS.clear();
        loadedShops.clear();
        loadedMenu = null;
        ITEM_INFO_DEBUG.clear();

        if (!ignoreCreator) {
            CREATOR.clear();
        }
        getLogUtil().debugLog("All caches cleared");

        // ========== PHASE 4: Reload configuration files ==========
        try {
            configManager.reloadConfigs();
            getLogUtil().debugLog("Configs reloaded");
        } catch (Exception e) {
            getLogUtil().log("[Critical] Failed to reload configs: " + e.getMessage());
            hadErrors = true;
        }

        // ========== PHASE 5: Reinitialize all systems ==========
        
        // Reinitialize GUI listener
        try {
            getServer().getPluginManager().registerEvents(GuiListener.getInstance(), this);
            getLogUtil().debugLog("GUI listener reinitialized");
        } catch (Exception e) {
            getLogUtil().log("[Warning] Failed to reinitialize GUI listener: " + e.getMessage());
            hadErrors = true;
        }
        
        // Re-hook the Vault economy (an economy plugin may have been added since startup)
        try {
            resolveEconomy(false);
            getLogUtil().debugLog("Economy hook refreshed");
        } catch (Exception e) {
            getLogUtil().log("[Warning] Failed to refresh the economy hook: " + e.getMessage());
        }

        // Reload all shops and menu items (warmup)
        warmup();

        // ALWAYS re-register commands, even if config loading failed
        try {
            CommandsMode cmdMode = Config.getCommandsMode();
            commandManager.unregisterAll();

            if (cmdMode == CommandsMode.REGISTER) {
                commandManager.registerCommands();
            }

            // Handle command interception
            if (cmdMode == CommandsMode.INTERCEPT) {
                CommandsInterceptor.register();
            } else {
                CommandsInterceptor.unregister();
            }
            getLogUtil().debugLog("Commands reregistered");
        } catch (Exception e) {
            getLogUtil().log("[Warning] Failed to register commands: " + e.getMessage());
            hadErrors = true;
        }

        // Reinitialize statistics system
        try {
            initStatistics();
            getLogUtil().debugLog("Statistics system reinitialized");
        } catch (Exception e) {
            getLogUtil().log("[Warning] Failed to reinitialize statistics: " + e.getMessage());
        }

        // ========== PHASE 6: Report results ==========
        long elapsed = System.currentTimeMillis() - startTime;
        
        if (hadErrors) {
            logUtil.log("GUIShop hard reload completed with errors in " + elapsed + "ms! Check the logs above.");
            if (sender != null) {
                getMiscUtils().sendPrefix(sender, "reload.execute");
                sender.sendMessage(ChatColor.RED + "[GUIShop] Reload completed with errors - check console!");
            }
        } else {
            logUtil.log("GUIShop hard reload completed successfully in " + elapsed + "ms!");
            if (sender != null) {
                getMiscUtils().sendPrefix(sender, "reload.execute");
            }
        }

        this.setIsReload(false);
    }

    public void initWriteCache() {
        // Cancel any existing task from a previous load/reload
        SchedulerUtil.cancelTask(logFlushTask);

        // Schedule periodic log flushing and rotation (every 5 minutes = 6000 ticks)
        SchedulerUtil.runTaskTimerAsync(logFlushTask, () -> {
            if (logUtil != null) {
                // Flush cached logs to disk
                logUtil.flushLogs();
                // Check and rotate oversized log files
                logUtil.checkAndRotateLogs();
            }
        }, 6000, 6000);
    }
}
