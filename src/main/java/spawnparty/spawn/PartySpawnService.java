package spawnparty.spawn;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelData;
import spawnparty.SpawnPartyMod;
import spawnparty.api.SpawnPartyEvents;
import spawnparty.party.Party;
import spawnparty.party.PartyManager;
import spawnparty.util.Servers;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PartySpawnService {

	private PartySpawnService() {
	}

	/** Sets the player's origin to the party spawn. Does not teleport. */
	public static void setPartyOrigin(ServerPlayer player, Party party) {
		LevelData.RespawnData data = LevelData.RespawnData.of(
				party.dimension,
				party.spawn,
				party.yaw,
				party.pitch
		);
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(data, true), false);
	}

	/**
	 * Sets party origin only if the player has no bed/anchor (forced=false) spawn.
	 * Used for mid-game joins so beds are preserved.
	 */
	public static void setPartyOriginUnlessBed(ServerPlayer player, Party party) {
		ServerPlayer.RespawnConfig current = player.getRespawnConfig();
		if (current != null && isLikelyBedOrAnchor(current) && !isPartyOrigin(current, party)) {
			return;
		}
		setPartyOrigin(player, party);
	}

	public static void clearPartyOrigin(ServerPlayer player) {
		player.setRespawnPosition(null, false);
	}

	/**
	 * If the player is in a party and has no personal spawn (or it matches the party origin),
	 * ensure party origin is set. Leaves bed/anchor spawns alone.
	 */
	public static void restorePartyOriginIfNeeded(ServerPlayer player) {
		Party party = PartyManager.byMember(player.getUUID()).orElse(null);
		if (party == null) return;

		ServerPlayer.RespawnConfig current = player.getRespawnConfig();
		if (current == null) {
			setPartyOrigin(player, party);
			return;
		}
		if (isPartyOrigin(current, party)) {
			setPartyOrigin(player, party);
			return;
		}
		if (!isLikelyBedOrAnchor(current)) {
			if (current.forced() && !isPartyOrigin(current, party)) {
				setPartyOrigin(player, party);
			}
		}
	}

	public static boolean isPartyOrigin(ServerPlayer.RespawnConfig config, Party party) {
		if (config == null) return false;
		LevelData.RespawnData data = config.respawnData();
		return data.dimension().equals(party.dimension)
				&& data.pos().getX() == party.spawn.getX()
				&& data.pos().getY() == party.spawn.getY()
				&& data.pos().getZ() == party.spawn.getZ();
	}

	public static boolean isLikelyBedOrAnchor(ServerPlayer.RespawnConfig config) {
		return config != null && !config.forced();
	}

	/** Re-assert party origin for online members who are not on a bed/anchor. */
	public static void refreshOriginsForParty(Party party, net.minecraft.server.MinecraftServer server) {
		for (ServerPlayer member : PartyManager.onlineMembers(party, server)) {
			ServerPlayer.RespawnConfig current = member.getRespawnConfig();
			if (current != null && isLikelyBedOrAnchor(current) && !isPartyOrigin(current, party)) {
				continue;
			}
			setPartyOrigin(member, party);
		}
	}

	/**
	 * One-time placement when leaving the first-join hold into the world at party origin.
	 * Fires {@link SpawnPartyEvents#AFTER_FIRST_ORIGIN_PLACEMENT}, then soft-calls Waterworld.
	 */
	public static void placeAtPartyOriginOnce(ServerPlayer player, Party party) {
		setPartyOrigin(player, party);
		var server = Servers.of(player);
		ServerLevel level = server.getLevel(party.dimension);
		if (level == null) {
			level = server.overworld();
		}
		BlockPos pos = party.spawn;
		for (var extension : SpawnSetupController.extensions()) {
			try {
				extension.beforePlacement(player, party, level, pos);
			} catch (Throwable t) {
				SpawnPartyMod.LOGGER.warn("Setup extension {} beforePlacement failed", extension.id(), t);
			}
		}
		pos = party.spawn;
		PartyManager.save();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), party.yaw, party.pitch, false);
		SpawnPartyEvents.AFTER_FIRST_ORIGIN_PLACEMENT.invoker()
				.onFirstOriginPlacement(player, party, level, pos, party.yaw, party.pitch);
		notifyWaterworld(player, party.id, pos);
	}

	/** Soft bridge: Waterworld applies per-party island (once) + per-player gear. */
	private static void notifyWaterworld(ServerPlayer player, UUID partyId, BlockPos origin) {
		if (!FabricLoader.getInstance().isModLoaded("waterworld")) return;
		try {
			Class<?> hook = Class.forName("waterworld.compat.SpawnPartyOriginHook");
			Method method = hook.getMethod("onFirstOriginPlacement", ServerPlayer.class, UUID.class, BlockPos.class);
			method.invoke(null, player, partyId, origin);
		} catch (ClassNotFoundException e) {
			// Waterworld jar without hook — ignore
		} catch (Throwable t) {
			SpawnPartyMod.LOGGER.warn("Waterworld first-origin hook failed", t);
		}
	}
}
