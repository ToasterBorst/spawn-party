package spawnparty.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import spawnparty.SpawnPartyConfig;
import spawnparty.party.Party;
import spawnparty.party.PartyManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrefixService {

	private static final Map<UUID, Component> TAB_NAMES = new ConcurrentHashMap<>();

	private PrefixService() {
	}

	public static void register() {
		// Tab names applied via mixin on getTabListDisplayName
	}

	public static Component tabName(ServerPlayer player) {
		return TAB_NAMES.get(player.getUUID());
	}

	public static Component prefixedName(ServerPlayer player) {
		Party party = PartyManager.byMember(player.getUUID()).orElse(null);
		MutableComponent name = Component.literal(player.getGameProfile().name());
		if (party == null || !SpawnPartyConfig.INSTANCE.globalChatPrefix) {
			return name;
		}
		String label = SpawnPartyConfig.INSTANCE.useTagPrefix() ? party.tag : party.name;
		ChatFormatting color = colorOf(party.colorName());
		return Component.empty()
				.append(Component.literal("[" + label + "] ").withStyle(color))
				.append(name);
	}

	public static void refresh(ServerPlayer player) {
		if (!SpawnPartyConfig.INSTANCE.tabListPrefix) {
			TAB_NAMES.remove(player.getUUID());
			return;
		}
		Party party = PartyManager.byMember(player.getUUID()).orElse(null);
		if (party == null) {
			TAB_NAMES.remove(player.getUUID());
			return;
		}
		String label = SpawnPartyConfig.INSTANCE.useTagPrefix() ? party.tag : party.name;
		ChatFormatting color = colorOf(party.colorName());
		TAB_NAMES.put(player.getUUID(), Component.empty()
				.append(Component.literal("[" + label + "] ").withStyle(color))
				.append(Component.literal(player.getGameProfile().name())));
	}

	public static void refreshParty(Party party, MinecraftServer server) {
		for (ServerPlayer member : PartyManager.onlineMembers(party, server)) {
			refresh(member);
		}
	}

	public static ChatFormatting colorOf(String color) {
		return switch (color == null ? "gold" : color.toLowerCase()) {
			case "red" -> ChatFormatting.RED;
			case "dark_red" -> ChatFormatting.DARK_RED;
			case "blue" -> ChatFormatting.BLUE;
			case "dark_blue" -> ChatFormatting.DARK_BLUE;
			case "green" -> ChatFormatting.GREEN;
			case "dark_green" -> ChatFormatting.DARK_GREEN;
			case "aqua", "cyan" -> ChatFormatting.AQUA;
			case "yellow" -> ChatFormatting.YELLOW;
			case "light_purple", "pink", "magenta" -> ChatFormatting.LIGHT_PURPLE;
			case "white" -> ChatFormatting.WHITE;
			case "gray" -> ChatFormatting.GRAY;
			case "gold", "orange" -> ChatFormatting.GOLD;
			default -> ChatFormatting.GOLD;
		};
	}
}
