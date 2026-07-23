package spawnparty.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import spawnparty.SpawnPartyConfig;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HoldingCamera {

	private static final Set<UUID> HOLDING = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, BlockPos> HOLD_POS = new ConcurrentHashMap<>();

	private HoldingCamera() {
	}

	public static boolean isHolding(UUID id) {
		return HOLDING.contains(id);
	}

	public static void start(ServerPlayer player) {
		HOLDING.add(player.getUUID());
		ServerLevel level = player.level();
		BlockPos spawn = level.getRespawnData().pos();
		HOLD_POS.put(player.getUUID(), spawn);
		player.setGameMode(GameType.SPECTATOR);
		float yaw = SpawnPartyConfig.INSTANCE.holdingYawTowardSunrise ? -90.0F : player.getYRot();
		float pitch = 20.0F;
		player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 8.0, spawn.getZ() + 0.5, Set.of(), yaw, pitch, false);
	}

	public static void tick(ServerPlayer player) {
		if (!isHolding(player.getUUID())) return;
		BlockPos hold = HOLD_POS.get(player.getUUID());
		if (hold == null) return;
		if (player.gameMode() != GameType.SPECTATOR) {
			player.setGameMode(GameType.SPECTATOR);
		}
		double dx = player.getX() - (hold.getX() + 0.5);
		double dy = player.getY() - (hold.getY() + 8.0);
		double dz = player.getZ() - (hold.getZ() + 0.5);
		if (dx * dx + dy * dy + dz * dz > 2.25) {
			float yaw = SpawnPartyConfig.INSTANCE.holdingYawTowardSunrise ? -90.0F : player.getYRot();
			player.teleportTo(player.level(), hold.getX() + 0.5, hold.getY() + 8.0, hold.getZ() + 0.5, Set.of(), yaw, 20.0F, false);
		}
	}

	public static void stop(ServerPlayer player) {
		HOLDING.remove(player.getUUID());
		HOLD_POS.remove(player.getUUID());
	}
}
