package spawnparty.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PromptService {

	public enum Kind {
		PARTY_NAME,
		PARTY_TAG,
		PARTY_PASSWORD,
		JOIN_PASSWORD
	}

	private record Pending(Kind kind, Consumer<String> callback) {
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private PromptService() {
	}

	public static void ask(ServerPlayer player, Kind kind, Consumer<String> callback) {
		PENDING.put(player.getUUID(), new Pending(kind, callback));
		player.closeContainer();
		String hint = switch (kind) {
			case PARTY_NAME -> "Type the party name in chat (private).";
			case PARTY_TAG -> "Type the party tag in chat (private).";
			case PARTY_PASSWORD, JOIN_PASSWORD -> "Type the password in chat (private).";
		};
		player.sendSystemMessage(Component.literal(hint));
		player.sendSystemMessage(Component.literal("Type \"cancel\" to abort."));
	}

	public static boolean handleChat(ServerPlayer player, String rawMessage) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null) return false;

		String text = rawMessage.trim();
		if (text.equalsIgnoreCase("cancel")) {
			PENDING.remove(player.getUUID());
			player.sendSystemMessage(Component.literal("Cancelled."));
			return true;
		}
		PENDING.remove(player.getUUID());
		pending.callback().accept(text);
		return true;
	}

	public static boolean hasPending(UUID id) {
		return PENDING.containsKey(id);
	}
}
