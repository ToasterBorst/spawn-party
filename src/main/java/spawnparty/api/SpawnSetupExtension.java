package spawnparty.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import spawnparty.party.Party;

/**
 * Hook for other mods to add first-origin setup pages and locate constraints.
 * Spawn Party does not know about any specific integrator.
 *
 * <p>{@link #wantsSelection} only controls pages. {@link #contributeLocate} always
 * runs before locate, even when the player is not shown a page (config-only filters).
 */
public interface SpawnSetupExtension {

	Identifier id();

	boolean wantsSelection(ServerPlayer player);

	/**
	 * Show zero or more chest pages, then call {@code done} exactly once.
	 * Do not call {@code done} on disconnect or cancel.
	 */
	void openSelection(ServerPlayer player, SpawnSetupSession session, Runnable done);

	void contributeLocate(SpawnSetupSession session, SpawnLocateRequest request);

	/** After spawn chunks are ready, before the player is teleported. */
	default void beforePlacement(ServerPlayer player, Party party, ServerLevel level, BlockPos origin) {
	}

	/** Extras have been copied onto the party and frozen. */
	default void onPartyLocked(ServerPlayer player, Party party, SpawnSetupSession session) {
	}
}
