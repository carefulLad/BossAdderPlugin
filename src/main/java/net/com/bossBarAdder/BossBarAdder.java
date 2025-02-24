package net.com.bossBarAdder;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarAdder extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Double> bossBarRanges = new HashMap<>();
    private final Map<String, UUID> bossBarIDs = new HashMap<>(); // Stores boss bar ID strings
    private final Map<UUID, String> bossBarNames = new HashMap<>(); // Entity UUID -> Boss Bar Name
    private final Map<UUID, SetupStage> setupStages = new HashMap<>();
    private final Map<UUID, String[]> setupData = new HashMap<>();

    private enum SetupStage {
        NAME, ID, RANGE, WAITING_FOR_LINK
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("bossbaradder").setExecutor(this);

        // Background task to update visibility of boss bars based on range
        new BukkitRunnable() {
            @Override
            public void run() {
                updateBossBars();
            }
        }.runTaskTimer(this, 20L, 20L); // Runs every second
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        setupStages.put(player.getUniqueId(), SetupStage.NAME);
        setupData.put(player.getUniqueId(), new String[3]);
        player.sendMessage("§e[BossBar] §fType the boss bar name in chat.");
        return true;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!setupStages.containsKey(playerId)) {
            return;
        }

        event.setCancelled(true); // Hide the message from chat

        SetupStage stage = setupStages.get(playerId);
        String input = event.getMessage().trim();
        String[] data = setupData.get(playerId);

        switch (stage) {
            case NAME:
                data[0] = input;
                setupStages.put(playerId, SetupStage.ID);
                player.sendMessage("§e[BossBar] §fNow type the boss bar ID.");
                break;

            case ID:
                if (bossBarIDs.containsKey(input)) {
                    player.sendMessage("§c[BossBar] ID already in use. Please enter a different ID.");
                    return;
                }

                data[1] = input;
                setupStages.put(playerId, SetupStage.RANGE);
                player.sendMessage("§e[BossBar] §fNow type the range (number).");
                break;

            case RANGE:
                try {
                    double range = Double.parseDouble(input);
                    data[2] = input;
                    setupData.put(playerId, data);

                    String bossBarName = data[0];
                    String idString = data[1];
                    UUID id = UUID.nameUUIDFromBytes(idString.getBytes());

                    // Store setup info but don't remove it yet
                    bossBarNames.put(id, bossBarName);
                    bossBarRanges.put(id, range);
                    bossBarIDs.put(idString, id);

                    setupStages.put(playerId, SetupStage.WAITING_FOR_LINK);
                    player.sendMessage("§e[BossBar] §fHit or right-click a mob to link the boss bar.");
                } catch (NumberFormatException e) {
                    player.sendMessage("§c[BossBar] Invalid range. Please enter a valid number.");
                }
                break;
        }
    }

    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            linkBossBar((Player) event.getDamager(), event.getEntity());
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        linkBossBar(event.getPlayer(), event.getRightClicked());
    }

    private void linkBossBar(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!setupStages.containsKey(playerId) || setupStages.get(playerId) != SetupStage.WAITING_FOR_LINK) {
            return;
        }

        String[] data = setupData.get(playerId);
        if (data == null || data.length < 3) {
            player.sendMessage("§c[BossBar] Setup data missing. Restart the process.");
            return;
        }

        String idString = data[1];
        if (!bossBarIDs.containsKey(idString)) {
            player.sendMessage("§c[BossBar] No valid ID found. Please restart the setup.");
            return;
        }

        UUID id = bossBarIDs.get(idString);
        String name = bossBarNames.get(id);
        double range = bossBarRanges.get(id);

        // Create boss bar
        BossBar bossBar = Bukkit.createBossBar(name, BarColor.RED, BarStyle.SEGMENTED_10);
        bossBars.put(entity.getUniqueId(), bossBar);
        bossBar.setVisible(true);
        bossBar.addPlayer(player);
        bossBarRanges.put(entity.getUniqueId(), range);

        player.sendMessage("§e[BossBar] §fBoss bar linked to " + entity.getName() + " with a range of " + range + "!");

        // Cleanup after assigning the boss bar
        bossBarNames.remove(id);
        bossBarRanges.remove(id);
        bossBarIDs.remove(idString);
        setupStages.remove(playerId);
        setupData.remove(playerId);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (bossBars.containsKey(entity.getUniqueId()) && entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            BossBar bossBar = bossBars.get(entity.getUniqueId());
            double health = livingEntity.getHealth() - event.getFinalDamage();
            double maxHealth = livingEntity.getMaxHealth();
            bossBar.setProgress(Math.max(0, health / maxHealth));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (bossBars.containsKey(entity.getUniqueId())) {
            bossBars.get(entity.getUniqueId()).setVisible(false);
            bossBars.remove(entity.getUniqueId());
        }
    }

    private void updateBossBars() {
        for (UUID entityId : bossBars.keySet()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                BossBar bossBar = bossBars.get(entityId);
                double range = bossBarRanges.getOrDefault(entityId, 20.0);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().equals(entity.getWorld())) {
                        double distance = player.getLocation().distance(entity.getLocation());
                        if (distance <= range) {
                            if (!bossBar.getPlayers().contains(player)) {
                                bossBar.addPlayer(player);
                            }
                        } else {
                            bossBar.removePlayer(player);
                        }
                    }
                }
            }
        }
    }
}
