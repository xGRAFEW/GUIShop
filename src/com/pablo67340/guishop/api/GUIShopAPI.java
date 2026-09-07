package com.pablo67340.guishop.api;

import com.pablo67340.guishop.GUIShop;
import com.pablo67340.guishop.definition.Item;
import com.pablo67340.guishop.economy.EconomyManager;
import com.pablo67340.guishop.statistics.PlayerStats;
import com.pablo67340.guishop.statistics.StatisticsManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Officially supported API for interacting with GuiShop. <br>
 * <br>
 * Accessing GuiShop internals is not supported and liable to change at any
 * time.
 */
public abstract class GUIShopAPI {

    /**
     * Determines whether the specified item could be bought (has a buy price).
     * An item is considered to be able to be purchased even if it is not
     * displayed in the GUI. <br>
     * <br>
     * Formally, if an item is listed in the shops.yml with a defined buy price,
     * it can be bought.
     *
     * @param item the itemstack which would be bought
     * @return whether it can be bought
     */
    public static boolean canBeBought(ItemStack item) {
        Item shopItem = null;
        String itemString = item.getType().toString();
        List<Item> itemList = GUIShop.getINSTANCE().getITEMTABLE().get(itemString);

        if (itemList != null) {
            for (Item iterator : itemList) {
                if (iterator.isItemFromItemStack(item)) {
                    shopItem = iterator;
                }
            }
        }
        return shopItem != null && shopItem.hasBuyPrice();
    }

    /**
     * Gets the buy price for an item with specified quantity. <br>
     * If the item does not exist or does not have a buy price, <code>-1</code>
     * is returned.
     *
     * @param item the itemstack
     * @param quantity the quantity which would be purchased
     * @return the buy price or minus 1 if not set
     */
    public static BigDecimal getBuyPrice(ItemStack item, int quantity) {
        Item shopItem = null;
        String itemString = item.getType().toString();
        List<Item> itemList = GUIShop.getINSTANCE().getITEMTABLE().get(itemString);

        if (itemList != null) {
            for (Item iterator : itemList) {
                if (iterator.isItemFromItemStack(item)) {
                    shopItem = iterator;
                }
            }
        }
        return (shopItem != null && shopItem.hasBuyPrice()) ? shopItem.calculateBuyPrice(quantity) : BigDecimal.valueOf(-1);
    }

    /**
     * Indicates to GUIShop that the item has been purchased with the specified
     * quantity. <br>
     * If dynamic pricing is enabled, GUIShop will then inform the dynamic
     * pricing provider that the purchase has occurred. (If disabled, nothing
     * happens) <br>
     * <br>
     * Note that even if you are not using dynamic pricing, calling this method
     * is recommended because it automatically ensures compatibility with
     * dynamic pricing.
     *
     * @param item the itemstack
     * @param quantity the quantity which was purchased
     */
    public static void indicateBoughtItems(ItemStack item, int quantity) {
        Item shopItem = null;
        String itemString = item.getType().toString();
        List<Item> itemList = GUIShop.getINSTANCE().getITEMTABLE().get(itemString);

        if (itemList != null) {
            for (Item iterator : itemList) {
                if (iterator.isItemFromItemStack(item)) {
                    shopItem = iterator;
                }
            }
        }

        if (shopItem != null && shopItem.shouldUseDynamicPricing() && shopItem.hasBuyPrice()
                && shopItem.hasSellPrice()) {
            DynamicPriceProvider dynamicProvider = GUIShop.getINSTANCE().getMiscUtils().getDYNAMICPRICING();
            if (dynamicProvider != null) {
                dynamicProvider.buyItem(itemString, quantity);
            }
        }
    }

    // ==================== Statistics API ====================

    /**
     * Check if the statistics system is available and initialized.
     *
     * @return true if statistics tracking is available
     */
    public static boolean isStatisticsEnabled() {
        StatisticsManager manager = StatisticsManager.getInstance();
        return manager != null && manager.isAvailable();
    }

    /**
     * Get a player's shop statistics.
     *
     * @param player The player (online or offline)
     * @return PlayerStats object containing all statistics, or null if unavailable
     */
    public static PlayerStats getPlayerStats(OfflinePlayer player) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return null;
        }
        return manager.getStats(player.getUniqueId());
    }

    /**
     * Get a player's shop statistics by UUID.
     *
     * @param uuid The player's UUID
     * @return PlayerStats object containing all statistics, or null if unavailable
     */
    public static PlayerStats getPlayerStats(UUID uuid) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return null;
        }
        return manager.getStats(uuid);
    }

    /**
     * Get the total money a player has spent in shops.
     *
     * @param player The player
     * @return Total spent, or BigDecimal.ZERO if unavailable
     */
    public static BigDecimal getTotalSpent(OfflinePlayer player) {
        PlayerStats stats = getPlayerStats(player);
        return stats != null ? stats.getTotalSpent() : BigDecimal.ZERO;
    }

    /**
     * Get the total money a player has earned from selling.
     *
     * @param player The player
     * @return Total earned, or BigDecimal.ZERO if unavailable
     */
    public static BigDecimal getTotalEarned(OfflinePlayer player) {
        PlayerStats stats = getPlayerStats(player);
        return stats != null ? stats.getTotalEarned() : BigDecimal.ZERO;
    }

    /**
     * Get the total number of items a player has bought.
     *
     * @param player The player
     * @return Total items bought, or 0 if unavailable
     */
    public static int getItemsBought(OfflinePlayer player) {
        PlayerStats stats = getPlayerStats(player);
        return stats != null ? stats.getItemsBought() : 0;
    }

    /**
     * Get the total number of items a player has sold.
     *
     * @param player The player
     * @return Total items sold, or 0 if unavailable
     */
    public static int getItemsSold(OfflinePlayer player) {
        PlayerStats stats = getPlayerStats(player);
        return stats != null ? stats.getItemsSold() : 0;
    }

    /**
     * Get the total spent formatted as a string.
     *
     * @param player The player
     * @param abbreviated If true, format as 1.5K, 2M, etc. If false, use commas
     * @return Formatted string
     */
    public static String getTotalSpentFormatted(OfflinePlayer player, boolean abbreviated) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return "0";
        }
        return manager.getTotalSpentFormatted(player.getUniqueId(), abbreviated);
    }

    /**
     * Get the total earned formatted as a string.
     *
     * @param player The player
     * @param abbreviated If true, format as 1.5K, 2M, etc. If false, use commas
     * @return Formatted string
     */
    public static String getTotalEarnedFormatted(OfflinePlayer player, boolean abbreviated) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return "0";
        }
        return manager.getTotalEarnedFormatted(player.getUniqueId(), abbreviated);
    }

    /**
     * Get top spenders on the server.
     *
     * @param limit Number of players to return (max 100)
     * @return List of UUID -> Amount spent pairs, sorted descending
     */
    public static List<Map.Entry<UUID, BigDecimal>> getTopSpenders(int limit) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return List.of();
        }
        return manager.getTopSpenders(Math.min(limit, 100));
    }

    /**
     * Get top earners on the server.
     *
     * @param limit Number of players to return (max 100)
     * @return List of UUID -> Amount earned pairs, sorted descending
     */
    public static List<Map.Entry<UUID, BigDecimal>> getTopEarners(int limit) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return List.of();
        }
        return manager.getTopEarners(Math.min(limit, 100));
    }

    /**
     * Reset a player's statistics.
     *
     * @param player The player whose stats to reset
     */
    public static void resetPlayerStats(OfflinePlayer player) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager != null && manager.isAvailable()) {
            manager.resetStats(player.getUniqueId());
        }
    }

    // ==================== Top Items API (Server-wide) ====================

    /**
     * Get top sold items server-wide (all players combined).
     *
     * @param limit Max number of items to return
     * @return LinkedHashMap of material name to total quantity sold, ordered by quantity desc
     */
    public static Map<String, Long> getServerTopSoldItems(int limit) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return new LinkedHashMap<>();
        }
        return manager.getServerTopSoldItems(limit);
    }

    /**
     * Get top bought items server-wide (all players combined).
     *
     * @param limit Max number of items to return
     * @return LinkedHashMap of material name to total quantity bought, ordered by quantity desc
     */
    public static Map<String, Long> getServerTopBoughtItems(int limit) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return new LinkedHashMap<>();
        }
        return manager.getServerTopBoughtItems(limit);
    }

    // ==================== Top Items API (Per-player) ====================

    /**
     * Get a player's top sold items.
     *
     * @param player The player
     * @param limit Max number of items to return
     * @return LinkedHashMap of material name to total quantity sold, ordered by quantity desc
     */
    public static Map<String, Long> getPlayerTopSoldItems(OfflinePlayer player, int limit) {
        return getPlayerTopSoldItems(player.getUniqueId(), limit);
    }

    /**
     * Get a player's top sold items.
     *
     * @param uuid The player's UUID
     * @param limit Max number of items to return
     * @return LinkedHashMap of material name to total quantity sold, ordered by quantity desc
     */
    public static Map<String, Long> getPlayerTopSoldItems(UUID uuid, int limit) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return new LinkedHashMap<>();
        }
        return manager.getPlayerTopSoldItems(uuid, limit);
    }

    /**
     * Get a player's top bought items.
     *
     * @param player The player
     * @param limit Max number of items to return
     * @return LinkedHashMap of material name to total quantity bought, ordered by quantity desc
     */
    public static Map<String, Long> getPlayerTopBoughtItems(OfflinePlayer player, int limit) {
        return getPlayerTopBoughtItems(player.getUniqueId(), limit);
    }

    /**
     * Get a player's top bought items.
     *
     * @param uuid The player's UUID
     * @param limit Max number of items to return
     * @return LinkedHashMap of material name to total quantity bought, ordered by quantity desc
     */
    public static Map<String, Long> getPlayerTopBoughtItems(UUID uuid, int limit) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return new LinkedHashMap<>();
        }
        return manager.getPlayerTopBoughtItems(uuid, limit);
    }

    // ==================== Internal Economy API ====================
    
    /**
     * Check if GUIShop's internal economy is enabled and available.
     * 
     * @return true if internal economy is enabled and functional
     */
    public static boolean isInternalEconomyEnabled() {
        EconomyManager manager = EconomyManager.getInstance();
        return manager != null && manager.isAvailable();
    }
    
    /**
     * Get a player's balance using the internal economy.
     * 
     * @param player the player
     * @return the player's balance, or BigDecimal.ZERO if not available
     */
    public static BigDecimal getInternalBalance(OfflinePlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return BigDecimal.ZERO;
        }
        return manager.getBalance(player.getUniqueId());
    }
    
    /**
     * Set a player's balance using the internal economy.
     * 
     * @param player the player
     * @param amount the new balance
     * @return true if successful
     */
    public static boolean setInternalBalance(OfflinePlayer player, BigDecimal amount) {
        EconomyManager manager = EconomyManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return false;
        }
        return manager.setBalance(player.getUniqueId(), amount);
    }
    
    /**
     * Give money to a player using the internal economy.
     * 
     * @param player the player
     * @param amount the amount to give
     * @return true if successful
     */
    public static boolean giveInternalMoney(OfflinePlayer player, BigDecimal amount) {
        EconomyManager manager = EconomyManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return false;
        }
        return manager.deposit(player.getUniqueId(), amount);
    }
    
    /**
     * Take money from a player using the internal economy.
     * 
     * @param player the player
     * @param amount the amount to take
     * @return true if successful (player has sufficient funds)
     */
    public static boolean takeInternalMoney(OfflinePlayer player, BigDecimal amount) {
        EconomyManager manager = EconomyManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return false;
        }
        return manager.withdraw(player.getUniqueId(), amount);
    }
    
    /**
     * Format a balance using the internal economy's settings.
     * 
     * @param amount the amount to format
     * @return formatted string (e.g., "$1,000.00" or "1.5M")
     */
    public static String formatInternalBalance(BigDecimal amount) {
        EconomyManager manager = EconomyManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return amount.toPlainString();
        }
        return manager.format(amount);
    }

    // ==================== Payment Notification Preferences ====================
    
    /**
     * Check if a player has payment notifications enabled.
     * 
     * @param player the player to check
     * @return true if notifications are enabled (default true)
     */
    public static boolean isPayNotificationsEnabled(OfflinePlayer player) {
        return isPayNotificationsEnabled(player.getUniqueId());
    }
    
    /**
     * Check if a player has payment notifications enabled.
     * 
     * @param uuid the player's UUID
     * @return true if notifications are enabled (default true)
     */
    public static boolean isPayNotificationsEnabled(UUID uuid) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return true; // Default enabled
        }
        return manager.isPayNotificationsEnabled(uuid);
    }
    
    /**
     * Set whether a player has payment notifications enabled.
     * 
     * @param player the player
     * @param enabled true to enable, false to disable
     */
    public static void setPayNotificationsEnabled(OfflinePlayer player, boolean enabled) {
        setPayNotificationsEnabled(player.getUniqueId(), enabled);
    }
    
    /**
     * Set whether a player has payment notifications enabled.
     * 
     * @param uuid the player's UUID
     * @param enabled true to enable, false to disable
     */
    public static void setPayNotificationsEnabled(UUID uuid, boolean enabled) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager != null && manager.isAvailable()) {
            manager.setPayNotificationsEnabled(uuid, enabled);
        }
    }
    
    /**
     * Toggle payment notifications for a player.
     * 
     * @param player the player
     * @return the new state (true = enabled)
     */
    public static boolean togglePayNotifications(OfflinePlayer player) {
        return togglePayNotifications(player.getUniqueId());
    }
    
    /**
     * Toggle payment notifications for a player.
     * 
     * @param uuid the player's UUID
     * @return the new state (true = enabled)
     */
    public static boolean togglePayNotifications(UUID uuid) {
        StatisticsManager manager = StatisticsManager.getInstance();
        if (manager == null || !manager.isAvailable()) {
            return true; // Default enabled
        }
        return manager.togglePayNotifications(uuid);
    }
    
}
