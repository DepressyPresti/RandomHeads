package dev.depressy.randomhead;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RandomHeadPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, Long> lastActive = new HashMap<>();
    private final Map<UUID, Long> sessionStart = new HashMap<>();
    private final Map<UUID, Long> playtime = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private File cooldownFile;
    private File playtimeFile;
    private FileConfiguration cooldownConfig;
    private FileConfiguration playtimeConfig;

    private final long REWARD_TIME = 2 * 60 * 60 * 1000; // 2 hours
    private final long AFK_TIMEOUT = 20 * 60 * 1000; // 20 minutes

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("rhd")).setExecutor(this);
        Objects.requireNonNull(getCommand("cooldown")).setExecutor(this);

        setupDataFiles();
        loadCooldowns();
        loadPlaytime();

        Bukkit.getScheduler().runTaskTimer(this, this::checkRewards, 20L * 60, 20L * 60); // Every 60 seconds
    }

    @Override
    public void onDisable() {
        for (UUID uuid : sessionStart.keySet()) {
            updatePlaytime(uuid);
        }
        saveCooldowns();
        savePlaytime();
    }

    private void setupDataFiles() {
        cooldownFile = new File(getDataFolder(), "cooldowns.yml");
        playtimeFile = new File(getDataFolder(), "playtime.yml");

        if (!cooldownFile.exists()) {
            cooldownFile.getParentFile().mkdirs();
            try {
                cooldownFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create cooldowns.yml: " + e.getMessage());
            }
        }

        if (!playtimeFile.exists()) {
            playtimeFile.getParentFile().mkdirs();
            try {
                playtimeFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create playtime.yml: " + e.getMessage());
            }
        }

        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
        playtimeConfig = YamlConfiguration.loadConfiguration(playtimeFile);
    }

    private void loadCooldowns() {
        for (String key : cooldownConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                cooldowns.put(uuid, cooldownConfig.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveCooldowns() {
        for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
            cooldownConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save cooldowns.yml");
        }
    }

    private void loadPlaytime() {
        for (String key : playtimeConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                playtime.put(uuid, playtimeConfig.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void savePlaytime() {
        for (Map.Entry<UUID, Long> entry : playtime.entrySet()) {
            playtimeConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            playtimeConfig.save(playtimeFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save playtime.yml");
        }
    }

    private void updatePlaytime(UUID uuid) {
        if (!sessionStart.containsKey(uuid)) return;

        long sessionDuration = System.currentTimeMillis() - sessionStart.get(uuid);
        long updatedTotal = playtime.getOrDefault(uuid, 0L) + sessionDuration;
        playtime.put(uuid, updatedTotal);
        sessionStart.remove(uuid);
    }

    private void checkRewards() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            long lastMove = lastActive.getOrDefault(uuid, now);
            if (now - lastMove > AFK_TIMEOUT) continue;

            long currentSessionTime = now - sessionStart.getOrDefault(uuid, now);
            long total = playtime.getOrDefault(uuid, 0L) + currentSessionTime;
            long lastReward = cooldowns.getOrDefault(uuid, 0L);

            if (now - lastReward >= REWARD_TIME) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hdb random " + player.getName());
                cooldowns.put(uuid, now); // Reset cooldown based on time
                playtime.put(uuid, total); // Update tracked playtime
                saveCooldowns();
                savePlaytime();
                player.sendMessage("You received a random head!");
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.getFrom().toVector().equals(event.getTo().toVector())) {
            lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean isFirstJoin = !playtime.containsKey(uuid);
        sessionStart.put(uuid, System.currentTimeMillis());
        lastActive.put(uuid, System.currentTimeMillis());

        if (isFirstJoin) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hdb random " + player.getName());
            player.sendMessage("Welcome! You received a free head for joining the server for the first time!");
            cooldowns.put(uuid, System.currentTimeMillis()); // Prevent instant second head
            playtime.put(uuid, 0L); // Start tracking playtime
            saveCooldowns();
            savePlaytime();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        updatePlaytime(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }

        String name = cmd.getName().toLowerCase();

        if (name.equals("rhd")) {
            if (!player.isOp()) {
                player.sendMessage("You must be an operator to use this command.");
                return true;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hdb random " + player.getName());
            player.sendMessage("Manual test: You got a random head.");
            return true;
        }

        if (name.equals("cooldown")) {
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            long lastMove = lastActive.getOrDefault(uuid, now);
            long lastReward = cooldowns.getOrDefault(uuid, 0L);

            if (now - lastMove > AFK_TIMEOUT) {
                player.sendMessage("You are AFK. Move around to gain time toward your next head.");
                return true;
            }

            long timeLeft = REWARD_TIME - (now - lastReward); // Fixed cooldown logic
            if (timeLeft <= 0) {
                player.sendMessage("You can get a head right now!");
            } else {
                player.sendMessage("Time left until your next head: " + formatTime(timeLeft));
            }
            return true;
        }

        return false;
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}
