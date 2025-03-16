package com.twoweek.twPoints;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.EventHandler;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class twPoints extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final NamespacedKey pointsKey = new NamespacedKey(this, "player_points");
    private File pointsFile;
    private FileConfiguration pointsConfig;
    private Player selectedPlayer;
    private int pointsAmount;

    @Override
    public void onEnable() {
        createPointsFile();
        loadPoints();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("points") != null) {
            getCommand("points").setExecutor(this);
        }
        if (getCommand("pointsadmin") != null) {
            getCommand("pointsadmin").setExecutor(this);
        }
        if (getCommand("mcpointshelp") != null) {
            getCommand("mcpointshelp").setExecutor(this);
        }
        if (getCommand("reloadpoints") != null) {
            getCommand("reloadpoints").setExecutor(this);
        }
        getLogger().info("2week Points System enabled!");
    }

    @Override
    public void onDisable() {
        savePoints();
        for (BossBar bossBar : playerBossBars.values()) {
            bossBar.removeAll();
        }
        getLogger().info("2week Points System disabled!");
    }

    private void createPointsFile() {
        pointsFile = new File(getDataFolder(), "points.yml");
        if (!pointsFile.exists()) {
            pointsFile.getParentFile().mkdirs();
            saveResource("points.yml", false);
        }
        pointsConfig = YamlConfiguration.loadConfiguration(pointsFile);
    }

    private void savePoints() {
        for (UUID uuid : playerPoints.keySet()) {
            pointsConfig.set(uuid.toString(), playerPoints.get(uuid));
        }
        try {
            pointsConfig.save(pointsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPoints() {
        for (String key : pointsConfig.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            int points = pointsConfig.getInt(key);
            playerPoints.put(uuid, points);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PersistentDataContainer data = player.getPersistentDataContainer();
        playerPoints.put(player.getUniqueId(), data.getOrDefault(pointsKey, PersistentDataType.INTEGER, 0));
        updatePlayerTabName(player);
        createBossBar(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material block = event.getBlock().getType();
        int points = switch (block) {
            case COAL_ORE, COPPER_ORE -> 1;
            case IRON_ORE -> 2;
            case REDSTONE_ORE -> 3;
            case GOLD_ORE, LAPIS_ORE -> 5;
            case DIAMOND_ORE -> 10;
            case EMERALD_ORE -> 15;
            case ANCIENT_DEBRIS -> 25;
            default -> 0;
        };
        if (points > 0) {
            addPoints(player.getUniqueId(), points);
            updatePlayerTabName(player);
            updateBossBar(player);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player player = event.getEntity().getKiller();
            int points = switch (event.getEntity().getType()) {
                case PIG, COW, SHEEP, CHICKEN, RABBIT, HORSE, DONKEY, LLAMA, TURTLE, STRIDER, SALMON, COD, PUFFERFISH, TROPICAL_FISH, BEE, VILLAGER -> 1;
                case WOLF, FOX, ENDERMAN, SPIDER, POLAR_BEAR, TRADER_LLAMA, OCELOT, PANDA, GLOW_SQUID -> 5;
                case ZOMBIE, SKELETON, CREEPER, ENDERMITE, SLIME, MAGMA_CUBE, DROWNED, PHANTOM, HUSK, STRAY, PILLAGER, VINDICATOR, ILLUSIONER, EVOKER, WITCH, BAT, GHAST, BLAZE -> 10;
                case RAVAGER, ELDER_GUARDIAN, WITHER_SKELETON, SHULKER -> 100;
                case ENDER_DRAGON, WITHER, WARDEN -> 500;
                default -> 0;
            };
            addPoints(player.getUniqueId(), points);
            updatePlayerTabName(player);
            updateBossBar(player);
        }
    }
    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (selectedPlayer == null) {
            selectedPlayer = Bukkit.getPlayer(message);
            if (selectedPlayer != null) {
                player.sendMessage(ChatColor.GRAY + "Selected player: " + ChatColor.YELLOW + selectedPlayer.getName());
            } else {
                player.sendMessage(ChatColor.RED + "Player not found.");
            }
            event.setCancelled(true);
        } else {
            try {
                pointsAmount = Integer.parseInt(message);
                player.sendMessage(ChatColor.GRAY + "Set amount: " + ChatColor.YELLOW + pointsAmount);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number.");
            }
            event.setCancelled(true);
        }
    }

    private void addPoints(UUID uuid, int amount) {
        playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + amount);
    }

    private void removePoints(UUID uuid, int amount) {
        playerPoints.put(uuid, Math.max(0, playerPoints.getOrDefault(uuid, 0) - amount));
    }

    private void resetPoints(UUID uuid) {
        playerPoints.put(uuid, 0);
    }

    public int getPoints(UUID uuid) {
        return playerPoints.getOrDefault(uuid, 0);
    }

    private void updatePlayerTabName(Player player) {
        int points = getPoints(player.getUniqueId());
        player.setPlayerListName(player.getName() + " [" + points + " points]");
    }

    private void createBossBar(Player player) {
        BossBar bossBar = Bukkit.createBossBar(
                ChatColor.YELLOW + "Points: " + ChatColor.GREEN + getPoints(player.getUniqueId()),
                BarColor.GREEN,
                BarStyle.SOLID
        );
        bossBar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bossBar);
    }

    private void updateBossBar(Player player) {
        BossBar bossBar = playerBossBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.setTitle(ChatColor.YELLOW + "Points: " + ChatColor.GREEN + getPoints(player.getUniqueId()));
        }
    }

    private void openPointsAdminGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.YELLOW + "Points Admin");

        ItemStack selectPlayerItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta selectPlayerMeta = selectPlayerItem.getItemMeta();
        selectPlayerMeta.setDisplayName(ChatColor.GREEN + "Select Player");
        selectPlayerItem.setItemMeta(selectPlayerMeta);

        ItemStack setAmountItem = new ItemStack(Material.PAPER);
        ItemMeta setAmountMeta = setAmountItem.getItemMeta();
        setAmountMeta.setDisplayName(ChatColor.GREEN + "Set Amount");
        setAmountItem.setItemMeta(setAmountMeta);

        ItemStack addPointsItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta addPointsMeta = addPointsItem.getItemMeta();
        addPointsMeta.setDisplayName(ChatColor.GREEN + "Add Points");
        addPointsItem.setItemMeta(addPointsMeta);

        ItemStack removePointsItem = new ItemStack(Material.RED_WOOL);
        ItemMeta removePointsMeta = removePointsItem.getItemMeta();
        removePointsMeta.setDisplayName(ChatColor.RED + "Remove Points");
        removePointsItem.setItemMeta(removePointsMeta);

        ItemStack resetPointsItem = new ItemStack(Material.BARRIER);
        ItemMeta resetPointsMeta = resetPointsItem.getItemMeta();
        resetPointsMeta.setDisplayName(ChatColor.RED + "Reset Points");
        resetPointsItem.setItemMeta(resetPointsMeta);

        gui.setItem(10, selectPlayerItem);
        gui.setItem(12, setAmountItem);
        gui.setItem(14, addPointsItem);
        gui.setItem(16, removePointsItem);
        gui.setItem(22, resetPointsItem);

        player.openInventory(gui);
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.YELLOW + "Points Admin")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            String displayName = clickedItem.getItemMeta().getDisplayName();

            if (displayName.equals(ChatColor.GREEN + "Select Player")) {
                player.sendMessage(ChatColor.GRAY + "Please type the player's name in chat.");
// Handle player name input in chat
            } else if (displayName.equals(ChatColor.GREEN + "Set Amount")) {
                player.sendMessage(ChatColor.GRAY + "Please type the amount of points in chat.");
// Handle amount input in chat
            } else if (displayName.equals(ChatColor.GREEN + "Add Points")) {
                if (selectedPlayer != null) {
                    addPoints(selectedPlayer.getUniqueId(), pointsAmount);
                    player.sendMessage(ChatColor.GRAY + "Added " + ChatColor.YELLOW + pointsAmount + ChatColor.GRAY + " points to " + ChatColor.YELLOW + selectedPlayer.getName());
                    updatePlayerTabName(selectedPlayer);
                    updateBossBar(selectedPlayer);
                } else {
                    player.sendMessage(ChatColor.RED + "No player selected.");
                }
            } else if (displayName.equals(ChatColor.RED + "Remove Points")) {
                if (selectedPlayer != null) {
                    removePoints(selectedPlayer.getUniqueId(), pointsAmount);
                    player.sendMessage(ChatColor.GRAY + "Removed " + ChatColor.YELLOW + pointsAmount + ChatColor.GRAY + " points from " + ChatColor.YELLOW + selectedPlayer.getName());
                    updatePlayerTabName(selectedPlayer);
                    updateBossBar(selectedPlayer);
                } else {
                    player.sendMessage(ChatColor.RED + "No player selected.");
                }
            } else if (displayName.equals(ChatColor.RED + "Reset Points")) {
                if (selectedPlayer != null) {
                    resetPoints(selectedPlayer.getUniqueId());
                    player.sendMessage(ChatColor.GRAY + "Reset points for " + ChatColor.YELLOW + selectedPlayer.getName());
                    updatePlayerTabName(selectedPlayer);
                    updateBossBar(selectedPlayer);
                } else {
                    player.sendMessage(ChatColor.RED + "No player selected.");
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.YELLOW + "Only players can use this command.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        switch (label.toLowerCase()) {
            case "points" -> player.sendMessage(ChatColor.GRAY + "You have " + ChatColor.YELLOW + getPoints(uuid) + ChatColor.GRAY + " points.");
            case "pointsadmin" -> openPointsAdminGUI(player);
            case "mcpointshelp" -> {
                player.sendMessage(ChatColor.YELLOW + "--- 2week Points Help ---");
                player.sendMessage(ChatColor.GRAY + "/points - View your current points.");
                player.sendMessage(ChatColor.GRAY + "/pointsadmin - Open points admin GUI (Admin only).");
                player.sendMessage(ChatColor.GRAY + "/mcpointshelp - Display this help menu.");
            }
            case "reloadpoints" -> {
                reloadConfig();
                loadPoints();
                player.sendMessage(ChatColor.YELLOW + "Plugin reloaded.");
            }
        }
        return true;
    }
}
