# LazyChunk

**LazyChunk** is a Paper 1.21.x plugin that dynamically delays chunk loading when players move at high speeds (e.g., flying with elytra). It temporarily unloads non?critical chunks to reduce server load, then automatically reloads them when speed returns to normal.

---

## Features

- ?? **Speed?based unloading** 每 Monitors player movement; if a world exceeds 6 chunks/second, newly loaded chunks that contain no players or tile entities are immediately unloaded.
- ?? **Automatic recovery** 每 When speed drops below the threshold, all delayed chunks are reloaded asynchronously.
- ? **Timeout protection** 每 Chunks waiting longer than 30 seconds are forced to load to prevent getting stuck.
- ?? **Retry & cooldown** 每 Failed async loads retry up to 3 times; unloaded chunks have a 5?second cooldown to avoid thrashing.
- ??? **Memory?safe** 每 Uses `WeakHashMap`?style logic (via coordinate keys) and limits the pending chunk queue to 10,000 entries.

---

## How It Works

1. The plugin tracks each player＊s movement speed every second.
2. When a world＊s maximum speed exceeds the threshold (default: 6 chunks/s), the `ChunkLoadEvent` is intercepted.
3. If the loading chunk:
   - Contains **no players**,
   - Contains **no tile entities** (chests, furnaces, etc.),
   - And optionally contains **no other entities** (configurable),
   - ＃it is immediately unloaded (`chunk.unload(false)`).
4. The chunk is recorded and placed in a cooldown list to prevent repeated load/unload cycles.
5. Once the world speed drops back to normal, all pending chunks are reloaded asynchronously.
6. If a chunk remains pending for more than 30 seconds, it is force?loaded.
7. If the pending queue grows too large (10,000+), the oldest 10% are forcibly loaded.

---

## Installation

1. Download the latest `LazyChunk.jar` from the releases page (or build it yourself 每 see below).
2. Place the `.jar` file into your server＊s `plugins/` folder.
3. Restart the server or use `/reload` (though a restart is recommended).
4. No configuration file is needed 每 all settings are hardcoded (see [Configuration](#configuration) for constants you can change before building).

---

## Configuration

Currently, all parameters are defined as `private static final` constants in the main class. You can modify them before compiling:

| Constant | Description | Default |
|----------|-------------|---------|
| `SPEED_THRESHOLD` | Speed threshold in chunks/second | `6.0` |
| `COOLDOWN_MILLIS` | Cooldown after unloading (ms) | `5000` |
| `FORCE_LOAD_AFTER` | Max time a chunk stays pending (ms) | `30000` |
| `MAX_SPEED` | Cap to prevent teleport spikes | `20.0` |
| `TELEPORT_THRESHOLD` | Distance considered a teleport (blocks) | `50.0` |
| `MAX_UNLOADED_SIZE` | Maximum pending chunk queue size | `10000` |
| `MAX_RETRY_ATTEMPTS` | Async load retry attempts | `3` |
| `KEEP_ENTITIES` | If `true`, chunks with any entities are not unloaded | `false` |

*Future versions may introduce a `config.yml` file for runtime tuning.*

---

## Building from Source

If you want to build the plugin yourself:

1. Clone the repository or download the source code.
2. Ensure you have JDK 21+ and Gradle installed.
3. Run `./gradlew build` (Linux/macOS) or `gradlew build` (Windows).
4. The compiled JAR will be located in `build/libs/`.

---

## Compatibility

- **Server software**: Paper 1.21.x (uses Paper?specific APIs like `getChunkAtAsync`).
- **Not guaranteed** to work on Spigot or CraftBukkit (though they may run with limited functionality).
- No other plugins required.

---

## Permissions & Commands

This plugin has **no permissions** and **no commands** 每 it runs entirely automatically.

---

## Notes & Warnings

?? **Entity loss**: By default, chunks are unloaded with `unload(false)`, meaning **animals, monsters, items, and other entities inside them will be discarded**. If you want to preserve entities, set `KEEP_ENTITIES = true` (but this will reduce the effectiveness of the plugin).

?? **Tile entities** (chests, furnaces, hoppers, etc.) are **never** unloaded 每 they are considered valuable data and are always kept.

?? **Chunk unloading may fail** if other plugins hold references to the chunk. The plugin will log a warning and skip unloading.

?? The plugin logs its actions at `INFO` level 每 you can adjust your server＊s log configuration to reduce noise.

---

## License

This plugin is provided as?is under the MIT License (or specify your own license).  
**Author**: Cookie  
**Contact**: [your email or link] (optional)

---

## Support

For issues or suggestions, please open an issue on the project＊s issue tracker.  
Pull requests are welcome!

---

## Acknowledgements

Built for PaperMC 每 the high?performance Minecraft server software.  
Special thanks to the Paper community for feedback and testing.