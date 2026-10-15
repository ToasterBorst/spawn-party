package spawnparty.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import spawnparty.party.Party;

/**
 * Hook for other mods to add first-origin setup pages and locate constraints.
 * Spawn Party does not know about any specific integrator.
 *
 * <p>{@link #wantsSelection} only controls pages. {@link #contributeLocate} always
 * runs before locate, even when the player is not shown a page (config-only filters).
 *
 * <p>Choice clicks record a session value. They do not advance the wizard.
 * Spawn Party paints Back and Forward in the lower corners and creates the party
 * only after Forward on the last page.
 */
public interface SpawnSetupExtension {

	Identifier id();

	boolean wantsSelection(ServerPlayer player);

	/**
	 * Fill the current setup chest. Write choices with {@link SpawnSetupSession#put}
	 * and reopen the page to show the selection. Calling {@code done} does not
	 * change pages — Back and Forward belong to Spawn Party. Do not call {@code done}
	 * on disconnect or cancel.
	 */
	void openSelection(ServerPlayer player, SpawnSetupSession session, Runnable done);

	/**
	 * Return a message to keep the player on this page, or null to allow Forward.
	 */
	default Component forwardBlocked(ServerPlayer player, SpawnSetupSession session) {
		return null;
	}

	/**
	 * Icon for one locked extra in the party menu, or null for the default paper.
	 * {@code key} is the stored extra id ({@code namespace:path}).
	 */
	default ItemStack iconForLockedExtra(Party party, String key, String value) {
		return null;
	}

	void contributeLocate(SpawnSetupSession session, SpawnLocateRequest request);

	/** After spawn chunks are ready, before the player is teleported. */
	default void beforePlacement(ServerPlayer player, Party party, ServerLevel level, BlockPos origin) {
	}

	/** Extras have been copied onto the party and frozen. */
	default void onPartyLocked(ServerPlayer player, Party party, SpawnSetupSession session) {
	}
}
