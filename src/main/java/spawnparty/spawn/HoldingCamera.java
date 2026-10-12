package spawnparty.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import spawnparty.SpawnPartyConfig;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First-join hold: adventure + flight above build height (looking straight down)
 * until the player creates or joins a party. Spectator is intentionally avoided —
 * vanilla blocks container slot clicks in spectator.
 */
public final class HoldingCamera {

	private static final int HOLD_Y_MARGIN = 16;
	private static final float HOLD_PITCH = 90.0F;
	private static final double LEASH_SQ = 2.25;

	private static final Set<UUID> HOLDING = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, BlockPos> HOLD_POS = new ConcurrentHashMap<>();
	private static final Map<UUID, HoldPhase> PHASE = new ConcurrentHashMap<>();

	private HoldingCamera() {
	}

	public static boolean isHolding(UUID id) {
		return HOLDING.contains(id);
	}

	public static HoldPhase phase(UUID id) {
		return PHASE.getOrDefault(id, HoldPhase.MENU);
	}

	public static void setPhase(UUID id, HoldPhase phase) {
		if (id == null || phase == null) return;
		if (HOLDING.contains(id)) {
			PHASE.put(id, phase);
		}
	}

	public static void start(ServerPlayer player) {
		HOLDING.add(player.getUUID());
		PHASE.put(player.getUUID(), HoldPhase.MENU);
		ServerLevel level = player.level();
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos hold = new BlockPos(spawn.getX(), level.getMaxY() + HOLD_Y_MARGIN, spawn.getZ());
		HOLD_POS.put(player.getUUID(), hold);
		applyHoldMode(player);
		teleportToHold(player, hold);
	}

	public static void tick(ServerPlayer player) {
		if (!isHolding(player.getUUID())) return;
		BlockPos hold = HOLD_POS.get(player.getUUID());
		if (hold == null) return;
		applyHoldMode(player);
		double dx = player.getX() - (hold.getX() + 0.5);
		double dy = player.getY() - hold.getY();
		double dz = player.getZ() - (hold.getZ() + 0.5);
		if (dx * dx + dy * dy + dz * dz > LEASH_SQ) {
			teleportToHold(player, hold);
		}
	}

	public static void stop(ServerPlayer player) {
		HOLDING.remove(player.getUUID());
		HOLD_POS.remove(player.getUUID());
		PHASE.remove(player.getUUID());
		clearHoldAbilities(player);
	}

	private static void applyHoldMode(ServerPlayer player) {
		if (player.gameMode() != GameType.ADVENTURE) {
			player.setGameMode(GameType.ADVENTURE);
		}
		Abilities abilities = player.getAbilities();
		abilities.mayfly = true;
		abilities.flying = true;
		abilities.invulnerable = true;
		player.onUpdateAbilities();
	}

	private static void clearHoldAbilities(ServerPlayer player) {
		Abilities abilities = player.getAbilities();
		// Match adventure defaults until placement/survival takes over.
		abilities.mayfly = false;
		abilities.flying = false;
		abilities.invulnerable = false;
		player.onUpdateAbilities();
	}

	private static void teleportToHold(ServerPlayer player, BlockPos hold) {
		float yaw = SpawnPartyConfig.INSTANCE.holdingYawTowardSunrise ? -90.0F : player.getYRot();
		player.teleportTo(
				player.level(),
				hold.getX() + 0.5,
				hold.getY(),
				hold.getZ() + 0.5,
				Set.of(),
				yaw,
				HOLD_PITCH,
				false
		);
	}
}
