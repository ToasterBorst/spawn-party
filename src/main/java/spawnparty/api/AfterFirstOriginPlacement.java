package spawnparty.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import spawnparty.party.Party;

/**
 * Fired after a player is placed at their party origin for the first time
 * (leaving the first-join hold). Other mods should apply location-sensitive
 * starter kits here instead of on JOIN + PLAY_TIME.
 */
@FunctionalInterface
public interface AfterFirstOriginPlacement {
	void onFirstOriginPlacement(ServerPlayer player, Party party, ServerLevel level, BlockPos origin, float yaw, float pitch);
}
