package spawnparty.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import spawnparty.SpawnPartyConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Mutable locate constraints. Every registered extension mutates the same instance.
 *
 * <p>Merge: biome = first non-empty wins; {@code preferVillage} becomes false if any
 * extension sets false; {@code sampleY} = last set; {@code prepareChunkRadius} = max;
 * standing predicates AND.
 */
public final class SpawnLocateRequest {

	private Identifier biome;
	private Integer sampleY;
	private Boolean preferVillage;
	private int prepareChunkRadius = 1;
	private final List<Predicate<BlockPos>> standingPredicates = new ArrayList<>();

	public void setBiome(Identifier biome) {
		if (this.biome == null && biome != null) {
			this.biome = biome;
		}
	}

	public void clearBiome() {
		this.biome = null;
	}

	public Optional<Identifier> biome() {
		return Optional.ofNullable(biome);
	}

	public void setSampleY(int blockY) {
		this.sampleY = blockY;
	}

	public Optional<Integer> sampleY() {
		return Optional.ofNullable(sampleY);
	}

	public void setPreferVillage(boolean preferVillage) {
		if (this.preferVillage == null) {
			this.preferVillage = preferVillage;
			return;
		}
		if (!preferVillage) {
			this.preferVillage = false;
		}
	}

	public boolean preferVillage() {
		return preferVillage != null ? preferVillage : SpawnPartyConfig.INSTANCE.preferVillage;
	}

	public void setPrepareChunkRadius(int radius) {
		this.prepareChunkRadius = Math.max(this.prepareChunkRadius, Math.max(1, Math.min(8, radius)));
	}

	public int prepareChunkRadius() {
		return prepareChunkRadius;
	}

	public void addStandingPredicate(Predicate<BlockPos> predicate) {
		if (predicate != null) {
			standingPredicates.add(predicate);
		}
	}

	public boolean standingOk(BlockPos pos) {
		for (Predicate<BlockPos> predicate : standingPredicates) {
			if (!predicate.test(pos)) return false;
		}
		return true;
	}
}
