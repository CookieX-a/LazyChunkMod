# LazyChunk

**LazyChunk** is a Paper 1.21.x plugin that improves server performance by delaying chunk loading when players move at high speeds (e.g., elytra flight). It unloads non-critical chunks and reloads them when speed returns to normal.

## Features

- Speed-based unloading: triggers when a world's max speed exceeds 6 chunks/second.
- Automatically reloads chunks when speed drops.
- Timeout protection: chunks stuck for more than 30 seconds are force-loaded.
- Retry logic: failed async loads retry up to 3 times.
- Cooldown: unloaded chunks have a 5-second cooldown to prevent thrashing.
- Memory-safe: limits pending chunk queue to 10,000 entries.

## How It Works

1. The plugin tracks player movement speed every second.
2. When a world exceeds the speed threshold, `ChunkLoadEvent` is intercepted.
3. If the loaded chunk contains:
   - No players,
   - No tile entities (chests, furnaces, etc.),
   - Optionally no other entities (configurable),
   - It is unloaded immediately (`chunk.unload(false)`).
4. The chunk is recorded and placed in cooldown.
5. When speed drops, all pending chunks are reloaded asynchronously.
6. If pending for more than 30 seconds, it is force-loaded.
7. If queue exceeds 10,000, oldest 10% are force-loaded.

## Installation

1. Download `LazyChunk.jar`.
2. Place it in your server's `plugins/` folder.
3. Restart the server.
4. No configuration file needed (constants are hardcoded; see Configuration).

## Configuration

All settings are `private static final` constants in the main class:

| Constant | Description | Default |
|----------|-------------|---------|
| SPEED_THRESHOLD | Speed threshold (chunks/s) | 6.0 |
| COOLDOWN_MILLIS | Cooldown after unload (ms) | 5000 |
| FORCE_LOAD_AFTER | Max pending time (ms) | 30000 |
| MAX_SPEED | Cap to prevent teleport spikes | 20.0 |
| TELEPORT_THRESHOLD | Teleport distance (blocks) | 50.0 |
| MAX_UNLOADED_SIZE | Max pending queue size | 10000 |
| MAX_RETRY_ATTEMPTS | Async load retries | 3 |
| KEEP_ENTITIES | If true, chunks with any entities are kept | false |

## Building from Source

1. Clone the repository.
2. Ensure JDK 21+ and Gradle are installed.
3. Run `./gradlew build` (Linux/macOS) or `gradlew build` (Windows).
4. The JAR will be in `build/libs/`.

## Compatibility

- Server: Paper 1.21.x (uses Paper-specific APIs).
- Not guaranteed to work on Spigot or CraftBukkit.

## Permissions & Commands

None ¨C runs automatically.

## Notes

- By default, `chunk.unload(false)` discards all entities (animals, monsters, items). Set `KEEP_ENTITIES = true` to preserve them (less effective).
- Tile entities are never unloaded to avoid data loss.
- Unload may fail if other plugins hold references ¨C warnings are logged.

## License

MIT License ¨C see LICENSE file.

## Author

CookieX-a

## Support

Open an issue on the project's issue tracker.