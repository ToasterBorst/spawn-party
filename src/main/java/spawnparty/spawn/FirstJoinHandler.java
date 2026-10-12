package spawnparty.spawn;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import spawnparty.chat.PrefixService;
import spawnparty.gui.PartyMenus;
import spawnparty.party.Party;
import spawnparty.party.PartyManager;

public final class FirstJoinHandler {

	private FirstJoinHandler() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			server.execute(() -> onJoin(player));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SpawnSetupController.drop(handler.getPlayer().getUUID());
			HoldingCamera.stop(handler.getPlayer());
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SpawnSetupController.tick(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (HoldingCamera.isHolding(player.getUUID())) {
					HoldingCamera.tick(player);
					PartyMenus.reopenIfHolding(player);
				}
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PartySpawnService.restorePartyOriginIfNeeded(newPlayer);
			PrefixService.refresh(newPlayer);
		});
	}

	private static void onJoin(ServerPlayer player) {
		Party party = PartyManager.byMember(player.getUUID()).orElse(null);
		if (party != null) {
			PartySpawnService.restorePartyOriginIfNeeded(player);
			PrefixService.refresh(player);
			return;
		}
		if (PartyManager.isUnlocked(player.getUUID())) {
			PrefixService.refresh(player);
			return;
		}
		if (SoloSpawnService.isSoloMode()) {
			HoldingCamera.start(player);
			SpawnSetupController.beginSolo(player);
			return;
		}
		HoldingCamera.start(player);
		PartyMenus.openMain(player);
	}
}
