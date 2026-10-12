package spawnparty.api;

import spawnparty.party.PartyManager;
import spawnparty.spawn.HoldingCamera;
import spawnparty.spawn.SpawnSetupController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public surface for other mods (soft-depend). Spawn Party does not require them.
 */
public final class SpawnPartyAPI {

	private SpawnPartyAPI() {
	}

	public static boolean isHolding(UUID playerId) {
		return HoldingCamera.isHolding(playerId);
	}

	/** True after the player has left first-join hold (or rejoined an existing party). */
	public static boolean hasCompletedFirstPlacement(UUID playerId) {
		return PartyManager.isUnlocked(playerId);
	}

	public static void registerSetupExtension(SpawnSetupExtension extension) {
		SpawnSetupController.register(extension);
	}

	public static void openSetupPage(net.minecraft.server.level.ServerPlayer player,
			net.minecraft.network.chat.Component title, SetupPageFiller fill) {
		SetupMenuHost.open(player, title, fill);
	}

	public static Optional<Map<String, String>> getPartyExtras(UUID partyId) {
		if (partyId == null) return Optional.empty();
		return PartyManager.byId(partyId).map(party -> Map.copyOf(party.setupExtras));
	}

	public static Optional<Map<String, String>> extrasForMember(UUID playerId) {
		return PartyManager.byMember(playerId).map(party -> Map.copyOf(party.setupExtras));
	}
}
