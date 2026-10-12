package spawnparty.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import spawnparty.SpawnPartyConfig;
import spawnparty.party.Party;
import spawnparty.party.PartyManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrefixService {

	public static final int BROWN_RGB = 0x825432;

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
		if (SpawnPartyConfig.INSTANCE.maxPartySize == 1) {
			return Component.literal(player.getGameProfile().name());
		}
		Party party = PartyManager.byMember(player.getUUID()).orElse(null);
		MutableComponent name = Component.literal(player.getGameProfile().name());
		if (party == null || !SpawnPartyConfig.INSTANCE.globalChatPrefix) {
			return name;
		}
		String label = SpawnPartyConfig.INSTANCE.useTagPrefix() ? party.tag : party.name;
		return Component.empty()
				.append(Component.literal("[" + label + "] ").withStyle(colorStyle(party.colorName())))
				.append(name);
	}

	public static void refresh(ServerPlayer player) {
		if (SpawnPartyConfig.INSTANCE.maxPartySize == 1 || !SpawnPartyConfig.INSTANCE.tabListPrefix) {
			TAB_NAMES.remove(player.getUUID());
			return;
		}
		Party party = PartyManager.byMember(player.getUUID()).orElse(null);
		if (party == null) {
			TAB_NAMES.remove(player.getUUID());
			return;
		}
		String label = SpawnPartyConfig.INSTANCE.useTagPrefix() ? party.tag : party.name;
		TAB_NAMES.put(player.getUUID(), Component.empty()
				.append(Component.literal("[" + label + "] ").withStyle(colorStyle(party.colorName())))
				.append(Component.literal(player.getGameProfile().name())));
	}

	public static void refreshParty(Party party, MinecraftServer server) {
		for (ServerPlayer member : PartyManager.onlineMembers(party, server)) {
			refresh(member);
		}
	}

	public static ChatFormatting colorOf(String color) {
		return switch (color == null ? "gold" : color.toLowerCase()) {
			case "black" -> ChatFormatting.BLACK;
			case "dark_blue" -> ChatFormatting.DARK_BLUE;
			case "dark_green" -> ChatFormatting.DARK_GREEN;
			case "dark_aqua", "cyan" -> ChatFormatting.DARK_AQUA;
			case "dark_red" -> ChatFormatting.DARK_RED;
			case "dark_purple", "purple" -> ChatFormatting.DARK_PURPLE;
			case "gold", "orange" -> ChatFormatting.GOLD;
			case "gray" -> ChatFormatting.GRAY;
			case "dark_gray" -> ChatFormatting.DARK_GRAY;
			case "blue" -> ChatFormatting.BLUE;
			case "green", "lime" -> ChatFormatting.GREEN;
			case "aqua" -> ChatFormatting.AQUA;
			case "red" -> ChatFormatting.RED;
			case "light_purple", "pink", "magenta" -> ChatFormatting.LIGHT_PURPLE;
			case "yellow" -> ChatFormatting.YELLOW;
			case "white" -> ChatFormatting.WHITE;
			case "brown" -> ChatFormatting.GOLD;
			default -> ChatFormatting.GOLD;
		};
	}

	public static Style colorStyle(String color) {
		if (color != null && "brown".equalsIgnoreCase(color.trim())) {
			return Style.EMPTY.withColor(TextColor.fromRgb(BROWN_RGB));
		}
		return Component.empty().withStyle(colorOf(color)).getStyle();
	}
}
