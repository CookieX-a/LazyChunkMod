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

    // Lazy chunk records: coordinate string -> {unload timestamp, retry attempts}
    private final Map<String, ChunkRecord> unloadedChunks = new ConcurrentHashMap<>();
    // Cooldown chunks: coordinate string -> expiry timestamp
    private final Map<String, Long> cooldownChunks = new ConcurrentHashMap<>();

    // Current memory usage (0.0 ~ 1.0)
    private volatile double currentMemoryUsage = 0.0;

    // ======================== Configuration Constants ========================
    private static final double SPEED_THRESHOLD = 6.0;           // chunks per second
    private static final long COOLDOWN_MILLIS = 5000L;           // cooldown in milliseconds
    private static final long FORCE_LOAD_AFTER = 30000L;         // force load after ms
    private static final double MAX_SPEED = 20.0;                // speed cap
    private static final double TELEPORT_THRESHOLD = 50.0;       // distance considered teleport
    private static final int MAX_UNLOADED_SIZE = 10000;          // max lazy records
    private static final int MAX_RETRY_ATTEMPTS = 3;             // async load max retries
    private static final boolean KEEP_ENTITIES = false;          // true = keep entities (don't unload)

    // Memory thresholds
    private static final double MEMORY_LOW = 0.40;    // below this: no lazy unloading, force load all
    private static final double MEMORY_HIGH_ENABLE = 0.70; // above this: lazy unloading allowed
    private static final double MEMORY_CRITICAL = 0.90; // above this: prevent loading lazy chunks

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
        getServer().getPluginManager().registerEvents(this, this);

        // Task 1: Calculate max speed & memory usage every second
        new BukkitRunnable() {
            @Override
            public void run() {
                // Update memory usage
                updateMemoryUsage();

                // Calculate world speeds
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

                // --- Memory-based decisions ---
                if (currentMemoryUsage < MEMORY_LOW) {
                    // Memory is abundant: force load all lazy chunks
                    loadAllPendingChunks();
                    // Also clear cooldowns to allow normal behaviour
                    cooldownChunks.clear();
                } else if (currentMemoryUsage > MEMORY_CRITICAL) {
                    // Memory critical: do NOT load any lazy chunks (they stay unloaded)
                    // Nothing to do here; loading will be blocked in forceLoadPendingChunks()
                } else {
                    // Normal range: speed recovery triggers loading
                    for (Map.Entry<World, Double> entry : worldSpeeds.entrySet()) {
                        if (entry.getValue() <= SPEED_THRESHOLD) {
                            forceLoadPendingChunks(entry.getKey());
                        }
                    }
                }

                // Clean expired cooldowns (always)
                long now = System.currentTimeMillis();
                cooldownChunks.entrySet().removeIf(e -> e.getValue() <= now);

                // Trim unloaded chunks if too large
                if (unloadedChunks.size() > MAX_UNLOADED_SIZE) {
                    trimUnloadedChunks();
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 20 ticks = 1 second

        // Task 2: Force load timeout chunks every 10 seconds (but only if memory allows)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Skip if memory is critical (>90%)
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
        }.runTaskTimer(this, 0L, 200L); // 200 ticks = 10 seconds
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
            // No limit, treat as 0 usage
            currentMemoryUsage = 0.0;
            return;
        }
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        currentMemoryUsage = (double) usedMemory / (double) maxMemory;
    }

    // ======================== Event Listeners ========================
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // If memory is low (<40%), never unload
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

        // Only unload if memory usage is above 70%
        if (currentMemoryUsage < MEMORY_HIGH_ENABLE) {
            return;
        }

        String chunkKey = world.getName() + "," + chunk.getX() + "," + chunk.getZ();

        Long cooldownExpire = cooldownChunks.get(chunkKey);
        if (cooldownExpire != null && System.currentTimeMillis() < cooldownExpire) {
            return;
        }

        // Schedule unload on next tick to avoid interfering with chunk loading
        Bukkit.getScheduler().runTask(this, () -> {
            // Re-check cooldown
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

    /** Check if chunk can be unloaded (must be called on main thread) */
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

    /** Load all pending chunks regardless of world (used when memory is low) */
    private void loadAllPendingChunks() {
        if (unloadedChunks.isEmpty()) {
            return;
        }
        // Make a copy to avoid concurrent modification
        List<String> keys = new ArrayList<>(unloadedChunks.keySet());
        for (String key : keys) {
            // Remove from map first
            unloadedChunks.remove(key);
            String[] parts = key.split(",");
            if (parts.length == 3) {
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    // Load asynchronously
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
        // Clear cooldowns as well
        cooldownChunks.clear();
    }

    /** Load pending chunks for a specific world when speed recovers (only if memory < 90%) */
    private void forceLoadPendingChunks(World world) {
        // Do NOT load if memory is critical (>90%)
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

    /** Asynchronously load a chunk with retry logic */
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

    /** Trim unloadedChunks by removing oldest 10% and force loading them */
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
                    // Only load if memory allows
                    if (currentMemoryUsage <= MEMORY_CRITICAL) {
                        world.getChunkAtAsync(x, z);
                    } else {
                        // If memory critical, just discard the record (chunk remains unloaded)
                        getLogger().info("Dropped lazy chunk record " + key + " due to memory critical");
                    }
                }
            }
        }
    }
}