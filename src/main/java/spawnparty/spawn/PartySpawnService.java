package spawnparty.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelData;
import spawnparty.party.Party;
import spawnparty.party.PartyManager;
import spawnparty.util.Servers;

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
		// Beds/anchors typically use forced=false; /spawnpoint and party origin use forced=true
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
	 * One-time placement when leaving the spectator holding lobby into the world at party origin.
	 * Not used for mid-game party membership changes.
	 */
	public static void placeAtPartyOriginOnce(ServerPlayer player, Party party) {
		setPartyOrigin(player, party);
		var server = Servers.of(player);
		ServerLevel level = server.getLevel(party.dimension);
		if (level == null) {
			level = server.overworld();
		}
		BlockPos pos = party.spawn;
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), party.yaw, party.pitch, false);
	}
}
