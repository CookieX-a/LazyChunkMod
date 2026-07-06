package x.cookie.lazychunk;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Creates the default configuration file if it does not exist.
 */
public class CreateConfig {
    public static void create(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "LazyChunk.yml");
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                configFile.createNewFile();
                FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                // Write default values
                config.set("speed-threshold", 6.0);
                config.set("cooldown-millis", 5000L);
                config.set("force-load-after", 30000L);
                config.set("max-speed", 20.0);
                config.set("teleport-threshold", 50.0);
                config.set("max-unloaded-size", 10000);
                config.set("max-retry-attempts", 3);
                config.set("keep-entities", false);
                config.set("memory-low", 0.40);
                config.set("memory-high-enable", 0.70);
                config.set("memory-critical", 0.90);
                config.save(configFile);
                plugin.getLogger().info("Created default LazyChunk.yml");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create config file: " + e.getMessage());
            }
        }
    }
}