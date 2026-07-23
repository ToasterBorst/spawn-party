package spawnparty.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Servers {

	private Servers() {
	}

	public static MinecraftServer of(ServerPlayer player) {
		return player.level().getServer();
	}
}
