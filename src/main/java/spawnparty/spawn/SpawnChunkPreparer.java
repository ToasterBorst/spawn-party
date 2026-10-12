package spawnparty.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Loads a square of chunks around an origin across ticks (a few per tick).
 */
public final class SpawnChunkPreparer {

	private static final int TOUCH_PER_TICK = 4;

	private SpawnChunkPreparer() {
	}

	public static boolean tick(ServerLevel level, BlockPos origin, int radius, int[] cursor) {
		ChunkPos center = ChunkPos.containing(origin);
		int size = radius * 2 + 1;
		int total = size * size;
		int done = cursor[0];
		int touched = 0;
		while (done < total && touched < TOUCH_PER_TICK) {
			int i = done++;
			int dx = (i % size) - radius;
			int dz = (i / size) - radius;
			int cx = center.x() + dx;
			int cz = center.z() + dz;
			if (!level.hasChunk(cx, cz)) {
				level.getChunk(cx, cz);
				touched++;
			}
		}
		cursor[0] = done;
		if (done < total) return false;
		return isReady(level, origin, radius);
	}

	public static boolean isReady(ServerLevel level, BlockPos origin, int radius) {
		ChunkPos center = ChunkPos.containing(origin);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (!level.hasChunk(center.x() + dx, center.z() + dz)) {
					return false;
				}
			}
		}
		return true;
	}
}
