package spawnparty.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import spawnparty.SpawnPartyConfig;
import spawnparty.SpawnPartyMod;
import spawnparty.api.SpawnLocateRequest;
import spawnparty.party.PartyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PartySpawnLocator {

	private static final int MAX_RINGS = 16;
	private static final int POINTS_PER_RING = 8;
	private static final int STRUCTURE_RADIUS = 200;

	private PartySpawnLocator() {
	}

	public static List<BlockPos> candidates(BlockPos worldSpawn) {
		List<BlockPos> out = new ArrayList<>();
		out.add(worldSpawn);
		int dist = SpawnPartyConfig.INSTANCE.partySpawnDistance;
		for (int ring = 1; ring <= MAX_RINGS; ring++) {
			int r = ring * dist;
			for (int i = 0; i < POINTS_PER_RING; i++) {
				double angle = (Math.PI * 2 * i) / POINTS_PER_RING + ring * 0.17;
				int jitter = ((ring * 31 + i * 17) % 129) - 64;
				int x = worldSpawn.getX() + (int) Math.round(Math.cos(angle) * r) + jitter;
				int z = worldSpawn.getZ() + (int) Math.round(Math.sin(angle) * r) + jitter;
				out.add(new BlockPos(x, worldSpawn.getY(), z));
			}
		}
		return out;
	}

	public static int defaultSampleY(ServerLevel level) {
		return level.getRespawnData().pos().getY();
	}

	public static Holder<Biome> sampleBiome(ServerLevel level, int x, int z, int sampleY) {
		var sampler = level.getChunkSource().randomState().sampler();
		var source = level.getChunkSource().getGenerator().getBiomeSource();
		return source.getNoiseBiome(
				QuartPos.fromBlock(x),
				QuartPos.fromBlock(sampleY),
				QuartPos.fromBlock(z),
				sampler);
	}

	public static boolean biomeMatches(Holder<Biome> biome, Identifier want) {
		if (want == null) return true;
		return biome.unwrapKey().map(key -> key.identifier().equals(want)).orElse(false);
	}

	/**
	 * Cheap noise-only search used by admin relocate and as a last-resort sync path.
	 * Does not generate the whole neighborhood; standing check may load one column.
	 */
	public static Optional<LocatedSpawn> locate(ServerLevel level, net.minecraft.server.level.ServerPlayer feedback) {
		return locate(level, feedback, new SpawnLocateRequest(), null);
	}

	public static Optional<LocatedSpawn> locate(ServerLevel level, net.minecraft.server.level.ServerPlayer feedback,
			SpawnLocateRequest request, UUID ignorePartyId) {
		BlockPos worldSpawn = level.getRespawnData().pos();
		int sampleY = request.sampleY().orElseGet(() -> defaultSampleY(level));
		Identifier biomeId = request.biome().orElse(null);
		if (biomeId == null) {
			biomeId = parseBiome(SpawnPartyConfig.INSTANCE.spawnBiome).orElse(null);
		}

		for (BlockPos candidate : candidates(worldSpawn)) {
			if (!PartyManager.isFarEnoughFromOthers(candidate, ignorePartyId)) continue;
			if (biomeId != null && !biomeMatches(sampleBiome(level, candidate.getX(), candidate.getZ(), sampleY), biomeId)) {
				continue;
			}
			BlockPos probe = candidate;
			if (request.preferVillage()) {
				BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, candidate, STRUCTURE_RADIUS, false);
				if (village != null && PartyManager.isFarEnoughFromOthers(village, ignorePartyId)) {
					probe = village;
				}
			}
			BlockPos safe = findSafeStanding(level, probe);
			if (safe == null) continue;
			if (!PartyManager.isFarEnoughFromOthers(safe, ignorePartyId)) continue;
			if (!request.standingOk(safe)) continue;
			float yaw = SpawnPartyConfig.INSTANCE.holdingYawTowardSunrise ? -90.0F : 0.0F;
			return Optional.of(new LocatedSpawn(level.dimension(), safe, yaw, 0.0F));
		}

		SpawnPartyMod.LOGGER.warn("Party spawn locate exhausted candidates; falling back near world spawn");
		BlockPos fallback = findSafeStanding(level, worldSpawn);
		if (fallback == null) {
			fallback = waterSurfaceAt(level, worldSpawn.getX(), worldSpawn.getZ());
			if (fallback == null) fallback = worldSpawn;
		}
		if (!PartyManager.isFarEnoughFromOthers(fallback, ignorePartyId)) {
			fallback = spiralAway(level, worldSpawn, SpawnPartyConfig.INSTANCE.partySpawnDistance, ignorePartyId);
		}
		return Optional.of(new LocatedSpawn(level.dimension(), fallback, -90.0F, 0.0F));
	}

	private static BlockPos spiralAway(ServerLevel level, BlockPos origin, int distance, UUID ignorePartyId) {
		for (int i = 1; i <= 16; i++) {
			BlockPos tryPos = origin.offset(distance * i, 0, 0);
			BlockPos safe = findSafeStanding(level, tryPos);
			if (safe != null && PartyManager.isFarEnoughFromOthers(safe, ignorePartyId)) {
				return safe;
			}
		}
		BlockPos water = waterSurfaceAt(level, origin.getX() + distance, origin.getZ());
		return water != null ? water : origin.offset(distance, 0, 0);
	}

	public static BlockPos findSafeStanding(ServerLevel level, BlockPos around) {
		int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around.getX(), around.getZ());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(around.getX(), surfaceY, around.getZ());
		for (int dy = 0; dy < 16; dy++) {
			cursor.setY(surfaceY - dy);
			if (isDryLandSafe(level, cursor)) {
				return cursor.immutable();
			}
		}
		for (int r = 1; r <= 8; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					int x = around.getX() + dx;
					int z = around.getZ() + dz;
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					BlockPos pos = new BlockPos(x, y, z);
					if (isDryLandSafe(level, pos)) {
						return pos;
					}
				}
			}
		}
		return waterSurfaceAt(level, around.getX(), around.getZ());
	}

	private static boolean isDryLandSafe(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		if (!level.getBlockState(below).isSolid()) return false;
		BlockState feetState = level.getBlockState(feet);
		BlockState headState = level.getBlockState(feet.above());
		if (feetState.liquid() || headState.liquid()) return false;
		return feetState.getCollisionShape(level, feet).isEmpty()
				&& headState.getCollisionShape(level, feet.above()).isEmpty();
	}

	public static BlockPos waterSurfaceAt(ServerLevel level, int x, int z) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		for (int y = top; y >= level.getMinY() + 1; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER)) {
				BlockPos above = pos.above();
				BlockState aboveState = level.getBlockState(above);
				if (aboveState.getCollisionShape(level, above).isEmpty()) {
					return above.immutable();
				}
				return pos.immutable();
			}
		}
		int seaGuess = Math.max(level.getMinY() + 1, Math.min(level.getMaxY() - 2, top));
		return new BlockPos(x, seaGuess, z);
	}

	public static Optional<Identifier> parseBiome(String raw) {
		if (raw == null || raw.isBlank()) return Optional.empty();
		try {
			return Optional.of(Identifier.parse(raw.contains(":") ? raw : "minecraft:" + raw));
		} catch (Exception e) {
			SpawnPartyMod.LOGGER.warn("Invalid spawn_biome '{}'", raw);
			return Optional.empty();
		}
	}

	public record LocatedSpawn(ResourceKey<Level> dimension, BlockPos pos, float yaw, float pitch) {
	}
}
