package www.legendarycommunity.com.br.legendary_boss_bar;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Legendary_boss_bar extends JavaPlugin implements Listener {

    private final Map<LivingEntity, BossBar> bossBars = new HashMap<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Verificação periódica de proximidade
        Bukkit.getScheduler().runTaskTimer(this, this::updateBossBarsVisibility, 0L, 20L);
    }

    @Override
    public void onDisable() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("legendarybossbar")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("legendarybossbar.reload")) {
                    reloadConfig();
                    config = getConfig();
                    reloadBossBars();
                    sender.sendMessage(ChatColor.GREEN + "Configurações recarregadas com sucesso!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Você não tem permissão para recarregar a configuração.");
                }
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        assignBossBar(entity); // Verifica e atribui a boss bar no spawn
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            if (bossBars.containsKey(livingEntity)) {
                BossBar bossBar = bossBars.get(livingEntity);
                double health = Math.max(0, livingEntity.getHealth() - event.getFinalDamage());
                double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)).getValue();
                bossBar.setProgress(health / maxHealth);
            } else {
                assignBossBar(livingEntity);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (bossBars.containsKey(entity)) {
            BossBar bossBar = bossBars.get(entity);
            bossBar.removeAll();
            bossBars.remove(entity);
        }
    }

    private void assignBossBar(LivingEntity entity) {
        for (String bossType : Objects.requireNonNull(config.getConfigurationSection("Bosses")).getKeys(false)) {
            List<Map<?, ?>> bossList = config.getMapList("Bosses." + bossType);
            for (Map<?, ?> bossConfig : bossList) {
                String expectedEntityType = (String) bossConfig.get("EntityType");
                String expectedNametag = bossConfig.containsKey("Nametag")
                        ? ChatColor.translateAlternateColorCodes('&', (String) bossConfig.get("Nametag"))
                        : null;

                boolean matchesType = entity.getType().name().equalsIgnoreCase(expectedEntityType);
                boolean matchesName = expectedNametag == null || expectedNametag.equals(entity.getCustomName());

                if (matchesType && matchesName && !bossBars.containsKey(entity)) {
                    String bossBarTexture = (String) bossConfig.get("PrintBossBar");
                    String barColorString = (String) bossConfig.get("BarColor");
                    createBossBar(entity, bossBarTexture, barColorString);
                    getLogger().info("BossBar atribuída para: " + entity.getType() + " (" + entity.getCustomName() + ")");
                    return;
                }
            }
        }
    }

    private void createBossBar(LivingEntity entity, String bossBarTexture, String barColorString) {
        BarColor barColor = BarColor.valueOf(barColorString.toUpperCase());
        BossBar bossBar = Bukkit.createBossBar(bossBarTexture, barColor, BarStyle.SOLID);
        bossBar.setProgress(entity.getHealth() / Objects.requireNonNull(entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)).getValue());
        bossBar.setVisible(false);
        bossBars.put(entity, bossBar);
    }

    private void updateBossBarsVisibility() {
        for (Map.Entry<LivingEntity, BossBar> entry : bossBars.entrySet()) {
            LivingEntity boss = entry.getKey();
            BossBar bossBar = entry.getValue();

            if (boss.isDead()) {
                bossBar.removeAll();
                bossBars.remove(boss);
                continue;
            }

            boolean playerInRange = false;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(boss.getWorld()) && player.getLocation().distance(boss.getLocation()) <= 30) {
                    bossBar.addPlayer(player);
                    playerInRange = true;
                } else {
                    bossBar.removePlayer(player);
                }
            }
            bossBar.setVisible(playerInRange);
        }
    }

    private void reloadBossBars() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(LivingEntity.class).forEach(this::assignBossBar));
    }
}
