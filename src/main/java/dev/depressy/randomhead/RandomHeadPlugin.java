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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RandomHeadPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastActive = new HashMap<>();
    private File cooldownFile;
    private FileConfiguration cooldownConfig;

    private final long COOLDOWN_MILLIS = 2 * 60 * 60 * 1000; // 2 hours
    private final long AFK_TIMEOUT_MILLIS = 20 * 60 * 1000;  // 20 minutes

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("rhd")).setExecutor(this);

        setupCooldownFile();
        loadCooldowns();

        // Check players every minute
        Bukkit.getScheduler().runTaskTimer(this, this::processHeadRewards, 20L * 60, 20L * 60);
    }

    @Override
    public void onDisable() {
        saveCooldowns();
    }

    private void setupCooldownFile() {
        cooldownFile = new File(getDataFolder(), "cooldowns.yml");
        if (!cooldownFile.exists()) {
            cooldownFile.getParentFile().mkdirs();
            try {
                cooldownFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create cooldowns.yml: " + e.getMessage());
            }
        }
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    private void saveCooldowns() {
        for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
            cooldownConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            getLogger().warning("Could not save cooldowns.yml: " + e.getMessage());
        }
    }

    private void loadCooldowns() {
        for (String key : cooldownConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long time = cooldownConfig.getLong(key);
                cooldowns.put(uuid, time);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid UUID in cooldowns.yml: " + key);
            }
        }
    }

    private void processHeadRewards() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            long lastMove = lastActive.getOrDefault(uuid, now);
            if (now - lastMove > AFK_TIMEOUT_MILLIS) continue;

            long lastReward = cooldowns.getOrDefault(uuid, 0L);
            if (now - lastReward < COOLDOWN_MILLIS) continue;

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hdb random " + player.getName());
            player.sendMessage("🎁 You received a random head! \uD83D\uDC41");
            cooldowns.put(uuid, now);
            saveCooldowns(); // Save immediately after giving
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.getFrom().toVector().equals(event.getTo().toVector())) {
            lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.isOp()) {
            player.sendMessage("You must be an operator to use this command.");
            return true;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hdb random " + player.getName());
        player.sendMessage("Manual test: You got a random head!");
        return true;
    }
}
