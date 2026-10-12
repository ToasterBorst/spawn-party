package spawnparty.chat;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import spawnparty.SpawnPartyConfig;
import spawnparty.party.Party;
import spawnparty.party.PartyManager;
import spawnparty.spawn.HoldingCamera;
import spawnparty.util.Servers;

public final class PartyChatService {

	private PartyChatService() {
	}

	public static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			String raw = message.signedContent();
			if (PromptService.handleChat(sender, raw)) {
				return false;
			}

			if (SpawnPartyConfig.INSTANCE.partyChatEnabled) {
				String partyBody = extractPartyChatBody(raw);
				if (partyBody != null) {
					sendPartyMessage(sender, partyBody);
					return false;
				}
			}

			if (SpawnPartyConfig.INSTANCE.globalChatPrefix && PartyManager.byMember(sender.getUUID()).isPresent()) {
				Component outgoing = Component.empty()
						.append(Component.literal("<"))
						.append(PrefixService.prefixedName(sender))
						.append(Component.literal("> " + raw));
				for (ServerPlayer viewer : Servers.of(sender).getPlayerList().getPlayers()) {
					viewer.sendSystemMessage(outgoing);
				}
				return false;
			}

			return true;
		});
	}

	/**
	 * If {@code raw} starts with a configured party-chat prefix (e.g. {@code !p hello}),
	 * returns the message body (may be empty). Otherwise returns {@code null}.
	 */
	static String extractPartyChatBody(String raw) {
		String text = raw == null ? "" : raw.trim();
		if (text.isEmpty()) return null;
		for (String prefix : SpawnPartyConfig.INSTANCE.partyChatPrefixes) {
			if (text.equals(prefix)) {
				return "";
			}
			String withSpace = prefix + " ";
			if (text.startsWith(withSpace)) {
				return text.substring(withSpace.length());
			}
		}
		return null;
	}

	private static void sendPartyMessage(ServerPlayer sender, String body) {
		if (SpawnPartyConfig.INSTANCE.maxPartySize == 1) {
			sender.sendSystemMessage(Component.literal("Party chat is unused in solo spawn mode."));
			return;
		}
		if (HoldingCamera.isHolding(sender.getUUID())) {
			sender.sendSystemMessage(Component.literal("Finish joining a party first."));
			return;
		}
		Party party = PartyManager.byMember(sender.getUUID()).orElse(null);
		if (party == null) {
			sender.sendSystemMessage(Component.literal("Join a party to use party chat."));
			return;
		}
		if (body == null || body.isBlank()) {
			String example = SpawnPartyConfig.INSTANCE.partyChatPrefixes.isEmpty()
					? "!p hello"
					: SpawnPartyConfig.INSTANCE.partyChatPrefixes.getFirst() + " hello";
			sender.sendSystemMessage(Component.literal("Usage: " + example));
			return;
		}

		Component outgoing = Component.empty()
				.append(Component.literal("[Party] ").withStyle(ChatFormatting.AQUA))
				.append(PrefixService.prefixedName(sender))
				.append(Component.literal(": ").append(Component.literal(body)));
		for (ServerPlayer member : PartyManager.onlineMembers(party, Servers.of(sender))) {
			member.sendSystemMessage(outgoing);
		}
	}
}
