# LazyChunk

**LazyChunk** is a Paper 1.21.x plugin that improves server performance by dynamically delaying chunk loading based on both player speed **and server memory usage**.

When memory is abundant, the plugin does nothing. As memory pressure increases, it starts unloading non?critical chunks when players move fast; when memory is critically low, it stops re?loading them until memory recovers.

## Features

- **Speed?based unloading** – triggers when a world’s max speed exceeds 6 chunks/second.
- **Three memory thresholds**:
  - `< 40%` – lazy mode disabled; all pending chunks are forced to load.
  - `? 70%` – lazy mode enabled; unloading occurs when speed is high.
  - `> 90%` – loading of lazy chunks is blocked; they stay unloaded to save memory.
- **Automatic recovery** – when memory drops below 90% and speed normalises, chunks reload.
- **Timeout protection** – chunks pending more than 30 seconds are force?loaded (if memory allows).
- **Retry logic** – failed async loads retry up to 3 times.
- **Cooldown** – unloaded chunks have a 5?second cooldown to prevent thrashing.
- **Memory?safe** – pending queue limited to 10,000 entries.

## How It Works

1. Every second, the plugin tracks player speed and current memory usage (used / max).
2. Based on memory usage:
   - **Below 40%**: all lazy chunks are loaded immediately; no new unloading occurs.
   - **Between 70% and 90%**: lazy unloading is active – chunks that load while speed is high and contain no players/tile entities are unloaded.
   - **Above 90%**: loading of lazy chunks is blocked; they stay unloaded until memory drops.
3. When speed drops below the threshold (and memory is ? 90%), pending chunks are reloaded asynchronously.
4. Timeout and queue size limits provide fallback safety.

## Installation

1. Download `LazyChunk.jar` from the releases page.
2. Place it in your server’s `plugins/` folder.
3. Restart the server.
4. No configuration file is needed – constants can be changed before building (see below).

## Configuration

All settings are `private static final` constants in the main class. Defaults:

| Constant | Description | Default |
|----------|-------------|---------|
| SPEED_THRESHOLD | Speed threshold (chunks/s) | 6.0 |
| COOLDOWN_MILLIS | Cooldown after unload (ms) | 5000 |
| FORCE_LOAD_AFTER | Max pending time (ms) | 30000 |
| MAX_SPEED | Cap to prevent teleport spikes | 20.0 |
| TELEPORT_THRESHOLD | Distance considered teleport (blocks) | 50.0 |
| MAX_UNLOADED_SIZE | Max pending queue size | 10000 |
| MAX_RETRY_ATTEMPTS | Async load retries | 3 |
| KEEP_ENTITIES | If true, chunks with any entities are kept | false |
| MEMORY_LOW | Below this, no lazy unloading & all load | 0.40 (40%) |
| MEMORY_HIGH_ENABLE | Above this, lazy unloading allowed | 0.70 (70%) |
| MEMORY_CRITICAL | Above this, loading lazy chunks blocked | 0.90 (90%) |

## Building from Source

1. Clone the repository.
2. Ensure JDK 21+ and Gradle are installed.
3. Run `./gradlew build` (Linux/macOS) or `gradlew build` (Windows).
4. The JAR will be in `build/libs/`.

## Compatibility

- Server: Paper 1.21.x (uses Paper?specific APIs).
- Not guaranteed to work on Spigot or CraftBukkit.

## Permissions & Commands

None – runs automatically.

## Notes

- By default, `chunk.unload(false)` discards entities (animals, monsters, items). Set `KEEP_ENTITIES = true` to preserve them (less effective).
- Tile entities are never unloaded to avoid data loss.
- Unload may fail if other plugins hold references – warnings are logged.

## License

MIT License – see LICENSE file.

## Author

Cookie