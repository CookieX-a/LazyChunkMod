package x.cookie.lazychunk;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LazychunkMain extends JavaPlugin implements Listener {

    // ======================== Data Structures ========================
    private final Map<Player, Location> lastLocations = new ConcurrentHashMap<>();
    private volatile Map<World, Double> worldMaxSpeed = new ConcurrentHashMap<>();

    private final Map<String, ChunkRecord> unloadedChunks = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldownChunks = new ConcurrentHashMap<>();

    private volatile double currentMemoryUsage = 0.0;

    // ======================== Configurable Variables ========================
    private static double SPEED_THRESHOLD;
    private static long COOLDOWN_MILLIS;
    private static long FORCE_LOAD_AFTER;
    private static double MAX_SPEED;
    private static double TELEPORT_THRESHOLD;
    private static int MAX_UNLOADED_SIZE;
    private static int MAX_RETRY_ATTEMPTS;
    private static boolean KEEP_ENTITIES;
    private static double MEMORY_LOW;
    private static double MEMORY_HIGH_ENABLE;
    private static double MEMORY_CRITICAL;

    private static class ChunkRecord {
        final long timestamp;
        int attempts;
        ChunkRecord(long timestamp, int attempts) {
            this.timestamp = timestamp;
            this.attempts = attempts;
        }
    }

    // ======================== Lifecycle ========================
    @Override
    public void onEnable() {
        // Load configuration
        LoadConfig.load(this);
        SPEED_THRESHOLD = LoadConfig.getSpeedThreshold();
        COOLDOWN_MILLIS = LoadConfig.getCooldownMillis();
        FORCE_LOAD_AFTER = LoadConfig.getForceLoadAfter();
        MAX_SPEED = LoadConfig.getMaxSpeed();
        TELEPORT_THRESHOLD = LoadConfig.getTeleportThreshold();
        MAX_UNLOADED_SIZE = LoadConfig.getMaxUnloadedSize();
        MAX_RETRY_ATTEMPTS = LoadConfig.getMaxRetryAttempts();
        KEEP_ENTITIES = LoadConfig.getKeepEntities();
        MEMORY_LOW = LoadConfig.getMemoryLow();
        MEMORY_HIGH_ENABLE = LoadConfig.getMemoryHighEnable();
        MEMORY_CRITICAL = LoadConfig.getMemoryCritical();

        getServer().getPluginManager().registerEvents(this, this);

        // Task 1: Calculate max speed & memory usage every second
        new BukkitRunnable() {
            @Override
            public void run() {
                updateMemoryUsage();

                Map<World, Double> worldSpeeds = new HashMap<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location current = player.getLocation();
                    Location last = lastLocations.put(player, current.clone());
                    if (last != null) {
                        double distance = current.distance(last);
                        double chunksPerSecond;
                        if (distance > TELEPORT_THRESHOLD) {
                            chunksPerSecond = 0.0;
                        } else {
                            chunksPerSecond = Math.min(distance / 16.0, MAX_SPEED);
                        }
                        worldSpeeds.merge(current.getWorld(), chunksPerSecond, Math::max);
                    }
                }
                worldMaxSpeed = new ConcurrentHashMap<>(worldSpeeds);

                if (currentMemoryUsage < MEMORY_LOW) {
                    loadAllPendingChunks();
                    cooldownChunks.clear();
                } else if (currentMemoryUsage > MEMORY_CRITICAL) {
                    // Critical: do not load
                } else {
                    for (Map.Entry<World, Double> entry : worldSpeeds.entrySet()) {
                        if (entry.getValue() <= SPEED_THRESHOLD) {
                            forceLoadPendingChunks(entry.getKey());
                        }
                    }
                }

                long now = System.currentTimeMillis();
                cooldownChunks.entrySet().removeIf(e -> e.getValue() <= now);

                if (unloadedChunks.size() > MAX_UNLOADED_SIZE) {
                    trimUnloadedChunks();
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        // Task 2: Force load timeout chunks every 10 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentMemoryUsage > MEMORY_CRITICAL) {
                    return;
                }

                long now = System.currentTimeMillis();
                for (Map.Entry<String, ChunkRecord> entry : unloadedChunks.entrySet()) {
                    ChunkRecord rec = entry.getValue();
                    if (now - rec.timestamp > FORCE_LOAD_AFTER) {
                        String key = entry.getKey();
                        unloadedChunks.remove(key);
                        String[] parts = key.split(",");
                        if (parts.length == 3) {
                            World world = Bukkit.getWorld(parts[0]);
                            if (world != null) {
                                int x = Integer.parseInt(parts[1]);
                                int z = Integer.parseInt(parts[2]);
                                loadChunkAsync(world, x, z, key, true);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 200L);
    }

    @Override
    public void onDisable() {
        if (!unloadedChunks.isEmpty()) {
            getLogger().info("Clearing " + unloadedChunks.size() + " lazy chunks on disable.");
        }
        unloadedChunks.clear();
        cooldownChunks.clear();
        lastLocations.clear();
        worldMaxSpeed.clear();
        getLogger().info("LazyChunk disabled.");
    }

    // ======================== Memory Update ========================
    private void updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        if (maxMemory == Long.MAX_VALUE) {
            currentMemoryUsage = 0.0;
            return;
        }
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        currentMemoryUsage = (double) usedMemory / (double) maxMemory;
    }

    // ======================== Event Listeners ========================
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (currentMemoryUsage < MEMORY_LOW) {
            return;
        }

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        Map<World, Double> currentSpeeds = worldMaxSpeed;
        Double maxSpeed = currentSpeeds.get(world);
        if (maxSpeed == null || maxSpeed <= SPEED_THRESHOLD) {
            return;
        }

        if (currentMemoryUsage < MEMORY_HIGH_ENABLE) {
            return;
        }

        String chunkKey = world.getName() + "," + chunk.getX() + "," + chunk.getZ();

        Long cooldownExpire = cooldownChunks.get(chunkKey);
        if (cooldownExpire != null && System.currentTimeMillis() < cooldownExpire) {
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            Long expire = cooldownChunks.get(chunkKey);
            if (expire != null && System.currentTimeMillis() < expire) {
                return;
            }
            cooldownChunks.remove(chunkKey);

            if (shouldUnload(chunk)) {
                boolean unloaded = chunk.unload(false);
                if (unloaded) {
                    unloadedChunks.put(chunkKey, new ChunkRecord(System.currentTimeMillis(), 0));
                    cooldownChunks.put(chunkKey, System.currentTimeMillis() + COOLDOWN_MILLIS);
                    getLogger().info("Unloaded lazy chunk " + chunk.getX() + "," + chunk.getZ() +
                            " (speed " + String.format("%.2f", maxSpeed) + " chunks/s, mem " +
                            String.format("%.0f%%", currentMemoryUsage * 100) + ")");
                } else {
                    getLogger().fine("Failed to unload chunk " + chunkKey);
                }
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastLocations.remove(event.getPlayer());
    }

    // ======================== Core Methods ========================

    private boolean shouldUnload(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                return false;
            }
        }
        if (chunk.getTileEntities().length > 0) {
            return false;
        }
        if (KEEP_ENTITIES && chunk.getEntities().length > 0) {
            return false;
        }
        return true;
    }

    private void loadAllPendingChunks() {
        if (unloadedChunks.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(unloadedChunks.keySet());
        for (String key : keys) {
            unloadedChunks.remove(key);
            String[] parts = key.split(",");
            if (parts.length == 3) {
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    world.getChunkAtAsync(x, z).thenAccept(chunk -> {
                        getLogger().info("Loaded lazy chunk " + x + "," + z + " (memory low)");
                    }).exceptionally(throwable -> {
                        getLogger().warning("Failed to load chunk " + x + "," + z + " during memory low: " +
                                throwable.getMessage());
                        return null;
                    });
                }
            }
        }
        cooldownChunks.clear();
    }

    private void forceLoadPendingChunks(World world) {
        if (currentMemoryUsage > MEMORY_CRITICAL) {
            return;
        }

        Iterator<Map.Entry<String, ChunkRecord>> iterator = unloadedChunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ChunkRecord> entry = iterator.next();
            String key = entry.getKey();
            if (key.startsWith(world.getName() + ",")) {
                iterator.remove();
                String[] parts = key.split(",");
                if (parts.length == 3) {
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    loadChunkAsync(world, x, z, key, false);
                }
            }
        }
    }

    private void loadChunkAsync(World world, int x, int z, String key, boolean isTimeout) {
        CompletableFuture<Chunk> future = world.getChunkAtAsync(x, z);
        future.thenAccept(chunk -> {
            getLogger().info("Loaded pending chunk " + x + "," + z +
                    (isTimeout ? " (timeout)" : " (speed recovered)"));
        }).exceptionally(throwable -> {
            ChunkRecord rec = unloadedChunks.get(key);
            int attempts = (rec == null) ? 0 : rec.attempts;
            if (attempts < MAX_RETRY_ATTEMPTS) {
                unloadedChunks.put(key, new ChunkRecord(System.currentTimeMillis(), attempts + 1));
                getLogger().warning("Failed to load chunk " + x + "," + z + ", retry " + (attempts + 1) +
                        "/" + MAX_RETRY_ATTEMPTS + ": " + throwable.getMessage());
            } else {
                getLogger().severe("Failed to load chunk " + x + "," + z + " after " + MAX_RETRY_ATTEMPTS +
                        " attempts. Chunk may be permanently unloaded. Error: " + throwable.getMessage());
            }
            return null;
        });
    }

    private void trimUnloadedChunks() {
        List<Map.Entry<String, ChunkRecord>> entries = new ArrayList<>(unloadedChunks.entrySet());
        entries.sort(Comparator.comparingLong(e -> e.getValue().timestamp));
        int toRemove = entries.size() / 10;
        if (toRemove < 1) toRemove = 1;
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            Map.Entry<String, ChunkRecord> entry = entries.get(i);
            String key = entry.getKey();
            unloadedChunks.remove(key);
            String[] parts = key.split(",");
            if (parts.length == 3) {
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    if (currentMemoryUsage <= MEMORY_CRITICAL) {
                        world.getChunkAtAsync(x, z);
                    } else {
                        getLogger().info("Dropped lazy chunk record " + key + " due to memory critical");
                    }
                }
            }
        }
    }
}