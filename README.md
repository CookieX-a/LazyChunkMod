# LazyChunk

**LazyChunk** is a Paper 1.21.x plugin that improves server performance by dynamically delaying chunk loading based on player speed and JVM memory usage. It unloads non?critical chunks under memory pressure and reloads them when conditions improve.

## Features

- **Speed?based unloading** – triggers when a world’s max player speed exceeds the configured threshold (default: 6 chunks/second).
- **Memory?aware logic** – three tiers control when unloading and reloading occur.
- **External configuration** – all settings are stored in `plugins/LazyChunk/LazyChunk.yml`; no recompilation needed.
- **Timeout protection** – chunks pending for more than the configured time are force?loaded.
- **Retry & cooldown** – failed async loads retry up to N times; unloaded chunks have a short cooldown to prevent thrashing.
- **Memory?safe** – pending queue size is limited; old entries are force?loaded when the limit is reached.

## How It Works

1. Every second, the plugin calculates:
   - The maximum player speed (in chunks/second) for each world.
   - Current JVM memory usage (used / max).

2. Based on memory usage, the plugin decides:
   - **Below `memory-low` (default 40%)** – lazy mode disabled. All pending chunks are force?loaded immediately; no new unloading occurs.
   - **Between `memory-low` and `memory-high-enable`** – no unloading, even if speed is high.
   - **Between `memory-high-enable` and `memory-critical`** – lazy mode active. Chunks that load while speed exceeds the threshold and contain no players or tile entities are unloaded.
   - **Above `memory-critical` (default 90%)** – loading of lazy chunks is blocked; they stay unloaded to save memory.

3. When speed drops below the threshold and memory is ? `memory-critical`, pending chunks are reloaded asynchronously.

4. Additional safeguards (timeout, queue size limit, retries) ensure chunks are eventually loaded.

## Installation

1. Download `LazyChunk.jar` from the releases page.
2. Place it in your server’s `plugins/` folder.
3. Restart the server.
4. The plugin will generate `plugins/LazyChunk/LazyChunk.yml` on first start.
5. Edit the config file if needed, then restart the server.

## Configuration

The configuration file is `plugins/LazyChunk/LazyChunk.yml`. All values can be changed without recompiling.

| Key | Description | Default |
|-----|-------------|---------|
| `speed-threshold` | Speed threshold (chunks/second) | 6.0 |
| `cooldown-millis` | Cooldown after unload (ms) | 5000 |
| `force-load-after` | Max pending time before force?load (ms) | 30000 |
| `max-speed` | Cap to prevent teleport spikes | 20.0 |
| `teleport-threshold` | Distance considered teleport (blocks) | 50.0 |
| `max-unloaded-size` | Max pending queue size | 10000 |
| `max-retry-attempts` | Async load retries | 3 |
| `keep-entities` | If true, chunks with any entities are kept | false |
| `memory-low` | Below this, lazy mode is disabled | 0.40 |
| `memory-high-enable` | Above this, lazy unloading is allowed | 0.70 |
| `memory-critical` | Above this, lazy chunk loading is blocked | 0.90 |

## Building from Source

1. Clone the repository.
2. Ensure JDK 21+ and Gradle are installed.
3. Run `./gradlew build` (Linux/macOS) or `gradlew build` (Windows).
4. The JAR will be in `build/libs/`.

## Compatibility

- Server: Paper 1.21.x (uses Paper?specific APIs such as `getChunkAtAsync`).
- Not guaranteed to work on Spigot or CraftBukkit.

## Permissions & Commands

None – the plugin runs fully automatically.

## Notes

- By default, `chunk.unload(false)` discards all entities (animals, monsters, items) inside unloaded chunks. Set `keep-entities: true` to preserve them, but this reduces optimisation effectiveness.
- Tile entities (chests, furnaces, hoppers, etc.) are never unloaded to avoid data loss.
- If unloading fails (e.g., another plugin holds a reference), a warning is logged and the chunk remains loaded.

## License

MIT – see LICENSE file.

## Author

CookieX-a