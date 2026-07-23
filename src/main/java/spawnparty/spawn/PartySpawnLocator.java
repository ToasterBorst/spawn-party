package spawnparty.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import spawnparty.SpawnPartyConfig;
import spawnparty.SpawnPartyMod;
import spawnparty.party.PartyManager;

import java.util.Optional;
import java.util.function.Predicate;

public final class PartySpawnLocator {

	private static final int STRUCTURE_RADIUS = 200;
	private static final int BIOME_RADIUS = 6400;
	private static final int MAX_ATTEMPTS = 48;

	private PartySpawnLocator() {
	}

	public static Optional<LocatedSpawn> locate(ServerLevel level, ServerPlayer feedback) {
		SpawnPartyConfig config = SpawnPartyConfig.INSTANCE;
		BlockPos worldSpawn = level.getRespawnData().pos();
		Optional<Identifier> biomeId = parseBiome(config.spawnBiome);

		if (feedback != null) {
			feedback.sendSystemMessage(Component.literal("Finding a party spawn…"));
		}

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			BlockPos searchOrigin = offsetSearchOrigin(worldSpawn, attempt);
			BlockPos candidate = pickCandidate(level, searchOrigin, biomeId.orElse(null), config.preferVillage);
			if (candidate == null) continue;

			BlockPos safe = findSafeStanding(level, candidate);
			if (safe == null) continue;
			if (!PartyManager.isFarEnoughFromOthers(safe, null)) continue;

			float yaw = config.holdingYawTowardSunrise ? -90.0F : 0.0F;
			return Optional.of(new LocatedSpawn(level.dimension(), safe, yaw, 0.0F));
		}

		SpawnPartyMod.LOGGER.warn("Party spawn locate failed after {} attempts; falling back near world spawn", MAX_ATTEMPTS);
		BlockPos fallback = findSafeStanding(level, worldSpawn);
		if (fallback == null) {
			fallback = worldSpawn;
		}
		if (!PartyManager.isFarEnoughFromOthers(fallback, null)) {
			fallback = spiralAway(level, worldSpawn, SpawnPartyConfig.INSTANCE.partySpawnDistance);
		}
		return Optional.of(new LocatedSpawn(level.dimension(), fallback, -90.0F, 0.0F));
	}

	private static BlockPos pickCandidate(ServerLevel level, BlockPos origin, Identifier biomeId, boolean preferVillage) {
		if (preferVillage) {
			if (biomeId != null) {
				BlockPos inBiomeVillage = findVillageInBiome(level, origin, biomeId);
				if (inBiomeVillage != null) return inBiomeVillage;
				BlockPos biomePos = findBiome(level, origin, biomeId);
				if (biomePos != null) {
					BlockPos nearBiome = level.findNearestMapStructure(StructureTags.VILLAGE, biomePos, STRUCTURE_RADIUS, false);
					if (nearBiome != null) return nearBiome;
				}
			} else {
				BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, origin, STRUCTURE_RADIUS, false);
				if (village != null) return village;
			}
		}

		if (biomeId != null) {
			BlockPos biomePos = findBiome(level, origin, biomeId);
			if (biomePos != null) return biomePos;
		}
		return origin;
	}

	private static BlockPos findVillageInBiome(ServerLevel level, BlockPos origin, Identifier biomeId) {
		BlockPos cursor = origin;
		for (int i = 0; i < 12; i++) {
			BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, cursor, STRUCTURE_RADIUS, false);
			if (village == null) return null;
			if (biomeMatches(level, village, biomeId)) {
				return village;
			}
			cursor = village.offset(SpawnPartyConfig.INSTANCE.partySpawnDistance, 0, 0);
		}
		return null;
	}

	private static BlockPos findBiome(ServerLevel level, BlockPos origin, Identifier biomeId) {
		Predicate<Holder<Biome>> predicate = holder ->
				holder.unwrapKey().map(key -> key.identifier().equals(biomeId)).orElse(Boolean.FALSE);
		var pair = level.findClosestBiome3d(predicate, origin, BIOME_RADIUS, 64, 64);
		return pair == null ? null : pair.getFirst();
	}

	private static boolean biomeMatches(ServerLevel level, BlockPos pos, Identifier biomeId) {
		return level.getBiome(pos).unwrapKey().map(key -> key.identifier().equals(biomeId)).orElse(Boolean.FALSE);
	}

	private static Optional<Identifier> parseBiome(String raw) {
		if (raw == null || raw.isBlank()) return Optional.empty();
		try {
			return Optional.of(Identifier.parse(raw.contains(":") ? raw : "minecraft:" + raw));
		} catch (Exception e) {
			SpawnPartyMod.LOGGER.warn("Invalid spawn_biome '{}'", raw);
			return Optional.empty();
		}
	}

	private static BlockPos offsetSearchOrigin(BlockPos worldSpawn, int attempt) {
		if (attempt == 0) return worldSpawn;
		int ring = (attempt + 3) / 4;
		int step = ring * Math.max(512, SpawnPartyConfig.INSTANCE.partySpawnDistance / 2);
		return switch (attempt % 4) {
			case 1 -> worldSpawn.offset(step, 0, 0);
			case 2 -> worldSpawn.offset(-step, 0, 0);
			case 3 -> worldSpawn.offset(0, 0, step);
			default -> worldSpawn.offset(0, 0, -step);
		};
	}

	private static BlockPos spiralAway(ServerLevel level, BlockPos origin, int distance) {
		for (int i = 1; i <= 16; i++) {
			BlockPos tryPos = origin.offset(distance * i, 0, 0);
			BlockPos safe = findSafeStanding(level, tryPos);
			if (safe != null && PartyManager.isFarEnoughFromOthers(safe, null)) {
				return safe;
			}
		}
		return origin.offset(distance, 0, 0);
	}

	public static BlockPos findSafeStanding(ServerLevel level, BlockPos around) {
		int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around.getX(), around.getZ());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(around.getX(), surfaceY, around.getZ());
		for (int dy = 0; dy < 16; dy++) {
			cursor.setY(surfaceY - dy);
			if (isRespawnSafe(level, cursor)) {
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
					if (isRespawnSafe(level, pos)) {
						return pos;
					}
				}
			}
		}
		return null;
	}

	private static boolean isRespawnSafe(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		if (!level.getBlockState(below).isSolid()) return false;
		BlockState feetState = level.getBlockState(feet);
		BlockState headState = level.getBlockState(feet.above());
		if (feetState.liquid() || headState.liquid()) return false;
		return feetState.getCollisionShape(level, feet).isEmpty()
				&& headState.getCollisionShape(level, feet.above()).isEmpty();
	}

	public record LocatedSpawn(ResourceKey<Level> dimension, BlockPos pos, float yaw, float pitch) {
	}
}
