package x.cookie.lazychunk;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Loads the configuration file and provides getter methods for each setting.
 */
public class LoadConfig {
    private static FileConfiguration config;

    public static void load(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "LazyChunk.yml");
        if (!configFile.exists()) {
            CreateConfig.create(plugin);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        // Apply defaults in case keys are missing
        config.options().copyDefaults(true);
        config.addDefault("speed-threshold", 6.0);
        config.addDefault("cooldown-millis", 5000L);
        config.addDefault("force-load-after", 30000L);
        config.addDefault("max-speed", 20.0);
        config.addDefault("teleport-threshold", 50.0);
        config.addDefault("max-unloaded-size", 10000);
        config.addDefault("max-retry-attempts", 3);
        config.addDefault("keep-entities", false);
        config.addDefault("memory-low", 0.40);
        config.addDefault("memory-high-enable", 0.70);
        config.addDefault("memory-critical", 0.90);
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save config defaults: " + e.getMessage());
        }
    }

    public static double getSpeedThreshold() {
        return config.getDouble("speed-threshold", 6.0);
    }

    public static long getCooldownMillis() {
        return config.getLong("cooldown-millis", 5000L);
    }

    public static long getForceLoadAfter() {
        return config.getLong("force-load-after", 30000L);
    }

    public static double getMaxSpeed() {
        return config.getDouble("max-speed", 20.0);
    }

    public static double getTeleportThreshold() {
        return config.getDouble("teleport-threshold", 50.0);
    }

    public static int getMaxUnloadedSize() {
        return config.getInt("max-unloaded-size", 10000);
    }

    public static int getMaxRetryAttempts() {
        return config.getInt("max-retry-attempts", 3);
    }

    public static boolean getKeepEntities() {
        return config.getBoolean("keep-entities", false);
    }

    public static double getMemoryLow() {
        return config.getDouble("memory-low", 0.40);
    }

    public static double getMemoryHighEnable() {
        return config.getDouble("memory-high-enable", 0.70);
    }

    public static double getMemoryCritical() {
        return config.getDouble("memory-critical", 0.90);
    }
}