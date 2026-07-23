package spawnparty.chat;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import spawnparty.SpawnPartyConfig;
import spawnparty.party.Party;
import spawnparty.party.PartyManager;
import spawnparty.util.Servers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyChatService {

	private static final Map<UUID, ChatChannel> CHANNELS = new ConcurrentHashMap<>();

	private PartyChatService() {
	}

	public static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			String raw = message.signedContent();
			if (PromptService.handleChat(sender, raw)) {
				return false;
			}

			var server = Servers.of(sender);

			if (SpawnPartyConfig.INSTANCE.partyChatEnabled && getChannel(sender) == ChatChannel.PARTY) {
				Party party = PartyManager.byMember(sender.getUUID()).orElse(null);
				if (party == null) {
					sender.sendSystemMessage(Component.literal("You are not in a party. Switched to Global."));
					setChannel(sender, ChatChannel.GLOBAL);
				} else {
					Component outgoing = Component.empty()
							.append(Component.literal("[Party] ").withStyle(ChatFormatting.AQUA))
							.append(PrefixService.prefixedName(sender))
							.append(Component.literal(": ").append(Component.literal(raw)));
					for (ServerPlayer member : PartyManager.onlineMembers(party, server)) {
						member.sendSystemMessage(outgoing);
					}
					return false;
				}
			}

			if (SpawnPartyConfig.INSTANCE.globalChatPrefix && PartyManager.byMember(sender.getUUID()).isPresent()) {
				Component outgoing = Component.empty()
						.append(Component.literal("<"))
						.append(PrefixService.prefixedName(sender))
						.append(Component.literal("> " + raw));
				for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
					viewer.sendSystemMessage(outgoing);
				}
				return false;
			}

			return true;
		});
	}

	public static ChatChannel getChannel(ServerPlayer player) {
		return CHANNELS.getOrDefault(player.getUUID(), ChatChannel.GLOBAL);
	}

	public static void setChannel(ServerPlayer player, ChatChannel channel) {
		CHANNELS.put(player.getUUID(), channel);
	}

	public static ChatChannel toggle(ServerPlayer player) {
		if (!SpawnPartyConfig.INSTANCE.partyChatEnabled) {
			player.sendSystemMessage(Component.literal("Party chat is disabled in config."));
			return ChatChannel.GLOBAL;
		}
		ChatChannel next = getChannel(player) == ChatChannel.PARTY ? ChatChannel.GLOBAL : ChatChannel.PARTY;
		if (next == ChatChannel.PARTY && PartyManager.byMember(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Component.literal("Join a party to use party chat."));
			return ChatChannel.GLOBAL;
		}
		setChannel(player, next);
		String label = next == ChatChannel.PARTY ? "Party" : "Global";
		player.sendOverlayMessage(Component.literal("Chat: " + label));
		player.sendSystemMessage(Component.literal("Chat channel: " + label));
		return next;
	}
}
