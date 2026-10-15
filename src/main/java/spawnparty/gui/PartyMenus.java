package spawnparty.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import spawnparty.SpawnPartyConfig;
import spawnparty.SpawnPartyMod;
import spawnparty.api.SetupMenuHost;
import spawnparty.chat.PromptService;
import spawnparty.party.Party;
import spawnparty.party.PartyAccess;
import spawnparty.party.PartyManager;
import spawnparty.spawn.HoldPhase;
import spawnparty.spawn.HoldingCamera;
import spawnparty.spawn.PartySpawnService;
import spawnparty.spawn.SpawnSetupController;
import spawnparty.chat.PrefixService;
import spawnparty.util.Servers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
// ArrayList used for disband member snapshot

public final class PartyMenus {

	private static final Map<UUID, Draft> DRAFTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> JOIN_PAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> MANAGE_PAGE = new ConcurrentHashMap<>();

	private PartyMenus() {
	}

	public static void openMain(ServerPlayer player) {
		boolean holding = HoldingCamera.isHolding(player.getUUID());
		Party party = PartyManager.byMember(player.getUUID()).orElse(null);

		if (holding) {
			HoldingCamera.setPhase(player.getUUID(), HoldPhase.MENU);
		}
		SetupMenuHost.runReplacing(() -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> onMainClick(player, slot), !holding, PartyChestMenu.ROWS_DOUBLE);
			menu.fill(GuiItems.filler());
			MainSlots slots = mainSlots(party);
			menu.setSlot(slots.create, GuiItems.button(Items.BANNER.lime(), "Create Party", "Start a new party and claim an origin"));
			menu.setSlot(slots.join, GuiItems.button(Items.OAK_SIGN, "Join Party", "Browse open parties"));
			if (party != null && party.isLeader(player.getUUID())) {
				menu.setSlot(slots.extra, GuiItems.button(Items.WRITABLE_BOOK, "Manage Party", "Rename, access, kick, disband"));
			} else if (party != null) {
				menu.setSlot(slots.extra, GuiItems.button(Items.IRON_DOOR, "Leave Party", "Leave your current party"));
			}
			if (party != null && !party.setupExtras.isEmpty()) {
				menu.setSlot(22, GuiItems.button(Items.BOOK, "Party Options", "Locked when the party was created"));
			}
			if (!holding) {
				menu.setSlot(SetupMenuHost.NAV_CONFIRM, GuiItems.button(Items.BARRIER, "Close"));
			}
			return menu;
		}, Component.literal("Spawn Party"))));
	}

	private record MainSlots(int create, int join, int extra) {
	}

	private static MainSlots mainSlots(Party party) {
		if (party != null) {
			return new MainSlots(11, 13, 15);
		}
		return new MainSlots(12, 14, -1);
	}

	private static void onMainClick(ServerPlayer player, int slot) {
		Party party = PartyManager.byMember(player.getUUID()).orElse(null);
		MainSlots slots = mainSlots(party);
		if (slot == slots.create) {
			openCreate(player);
		} else if (slot == slots.join) {
			openJoinBrowser(player, 0);
		} else if (slots.extra >= 0 && slot == slots.extra) {
			if (party != null && party.isLeader(player.getUUID())) {
				openManage(player, party, 0);
			} else if (party != null) {
				leaveParty(player, party);
			}
		} else if (slot == 22 && party != null && !party.setupExtras.isEmpty()) {
			openLockedOptions(player, party, false);
		} else if (slot == SetupMenuHost.NAV_CONFIRM) {
			if (!HoldingCamera.isHolding(player.getUUID())) {
				player.closeContainer();
			}
		}
	}

	public static void openCreate(ServerPlayer player) {
		openCreate(player, false);
	}

	public static void openCreate(ServerPlayer player, boolean colorPalette) {
		Draft draft = DRAFTS.computeIfAbsent(player.getUUID(), id -> Draft.defaults(player));
		if (HoldingCamera.isHolding(player.getUUID())) {
			SpawnSetupController.rememberReopen(player.getUUID(), () -> openCreate(player, colorPalette));
			if (SpawnSetupController.hasWizard(player.getUUID())) {
				HoldingCamera.setPhase(player.getUUID(), HoldPhase.SETUP);
			}
		}
		boolean hasOptions = SpawnSetupController.anyWantsSelection(player);
		SetupMenuHost.runReplacing(() -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> onCreateClick(player, slot, colorPalette), false, PartyChestMenu.ROWS_DOUBLE);
			menu.fill(GuiItems.filler());
			if (colorPalette) {
				PartyColors.fillPalette(menu);
				menu.setSlot(SetupMenuHost.NAV_BACK, GuiItems.button(Items.ARROW, "Cancel Color"));
			} else {
				menu.setSlot(10, GuiItems.button(Items.NAME_TAG, "Name: " + draft.name, "Click, then type the name in chat"));
				menu.setSlot(11, GuiItems.button(Items.PAPER, "Tag: " + draft.tag, "Short prefix, click then type in chat"));
				menu.setSlot(12, GuiItems.button(Items.BRUSH, "Color: " + draft.color, "Click brush, then pick a dye"));
				menu.setSlot(14, GuiItems.button(Items.IRON_DOOR, "Access: " + draft.access.name(), "OPEN / CLOSED / PASSWORD"));
				if (draft.access == PartyAccess.PASSWORD) {
					menu.setSlot(15, GuiItems.button(Items.TRIPWIRE_HOOK, draft.password.isBlank() ? "Set Password" : "Password set", "Click, then type password in chat"));
				}
				if (hasOptions) {
					menu.setSlot(SetupMenuHost.NAV_CONFIRM, GuiItems.button(Items.ARROW, "Next",
							"Spawn options are chosen before the party is created."));
				} else {
					menu.setSlot(SetupMenuHost.NAV_CONFIRM, GuiItems.button(Items.EMERALD_BLOCK, "Create Party",
							"Find a spawn origin at least " + SpawnPartyConfig.INSTANCE.partySpawnDistance + " blocks from other parties.",
							"First spawn may wait a few seconds while chunks generate."));
				}
				menu.setSlot(SetupMenuHost.NAV_BACK, GuiItems.button(Items.ARROW, "Back"));
			}
			return menu;
		}, Component.literal(colorPalette ? "Pick Color" : "Create Party"))));
	}

	public static void discardDraft(UUID playerId) {
		if (playerId != null) {
			DRAFTS.remove(playerId);
		}
	}

	private static void onCreateClick(ServerPlayer player, int slot, boolean colorPalette) {
		Draft draft = DRAFTS.computeIfAbsent(player.getUUID(), id -> Draft.defaults(player));
		if (colorPalette) {
			String picked = PartyColors.fromPaletteSlot(slot);
			if (picked != null) {
				draft.color = picked;
				openCreate(player, false);
				return;
			}
			if (slot == SetupMenuHost.NAV_BACK) {
				openCreate(player, false);
			}
			return;
		}
		switch (slot) {
			case 10 -> PromptService.ask(player, PromptService.Kind.PARTY_NAME, text -> {
				draft.name = sanitizeName(text);
				if (draft.tag.equals(Draft.defaults(player).tag) || draft.tag.length() > 4) {
					draft.tag = tagFromName(draft.name);
				}
				openCreate(player);
			});
			case 11 -> PromptService.ask(player, PromptService.Kind.PARTY_TAG, text -> {
				draft.tag = sanitizeTag(text);
				openCreate(player);
			});
			case 12 -> openCreate(player, true);
			case 14 -> {
				draft.access = nextAccess(draft.access);
				openCreate(player);
			}
			case 15 -> {
				if (draft.access == PartyAccess.PASSWORD) {
					PromptService.ask(player, PromptService.Kind.PARTY_PASSWORD, text -> {
						draft.password = text;
						openCreate(player);
					});
				}
			}
			case SetupMenuHost.NAV_CONFIRM -> forwardCreate(player, draft);
			case SetupMenuHost.NAV_BACK -> {
				discardDraft(player.getUUID());
				SpawnSetupController.drop(player.getUUID());
				openMain(player);
			}
			default -> {
			}
		}
	}

	private static void forwardCreate(ServerPlayer player, Draft draft) {
		if (PartyManager.byMember(player.getUUID()).isPresent()) {
			player.sendSystemMessage(Component.literal("You are already in a party."));
			discardDraft(player.getUUID());
			SpawnSetupController.drop(player.getUUID());
			openMain(player);
			return;
		}
		if (draft.access == PartyAccess.PASSWORD && (draft.password == null || draft.password.isBlank())) {
			player.sendSystemMessage(Component.literal("Set a password first."));
			openCreate(player);
			return;
		}
		SpawnSetupController.advanceFromIdentity(player, new SpawnSetupController.PartyDraft(
				draft.name, draft.tag, draft.color, draft.access, draft.password));
	}

	public static void openJoinBrowser(ServerPlayer player, int page) {
		List<Party> visible = new ArrayList<>();
		for (Party party : PartyManager.all()) {
			if (party.access == PartyAccess.CLOSED && !party.isMember(player.getUUID())) continue;
			visible.add(party);
		}
		int safePage = Math.max(0, Math.min(page, Pagination.pageCount(visible.size()) - 1));
		JOIN_PAGE.put(player.getUUID(), safePage);
		List<Party> pageItems = Pagination.page(visible, safePage);

		SetupMenuHost.runReplacing(() -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> onJoinClick(player, slot, visible, safePage), false, PartyChestMenu.ROWS_DOUBLE);
			menu.fill(GuiItems.filler());
			for (int i = 0; i < pageItems.size(); i++) {
				Party party = pageItems.get(i);
				boolean full = SpawnPartyConfig.INSTANCE.isPartyFull(party.members.size());
				String membersLine = "Members: " + party.members.size()
						+ (SpawnPartyConfig.INSTANCE.maxPartySize > 0
						? "/" + SpawnPartyConfig.INSTANCE.maxPartySize
						: "");
				menu.setSlot(i, GuiItems.button(
						Items.PLAYER_HEAD,
						"[" + party.tag + "] " + party.name,
						"Access: " + party.access.name(),
						membersLine,
						full ? "FULL" : "Click to join"
				));
			}
			menu.setSlot(SetupMenuHost.NAV_BACK, GuiItems.button(Items.ARROW, "Back"));
			menu.setSlot(SetupMenuHost.NAV_PREV, GuiItems.button(Items.ARROW, "Prev Page"));
			menu.setSlot(SetupMenuHost.NAV_INFO, GuiItems.button(Items.BOOK, "Page " + (safePage + 1) + "/" + Pagination.pageCount(visible.size())));
			menu.setSlot(SetupMenuHost.NAV_CONFIRM, GuiItems.button(Items.ARROW, "Next Page"));
			return menu;
		}, Component.literal("Join Party"))));
	}

	private static void onJoinClick(ServerPlayer player, int slot, List<Party> visible, int page) {
		List<Party> pageItems = Pagination.page(visible, page);
		if (slot >= 0 && slot < pageItems.size()) {
			tryJoin(player, pageItems.get(slot));
			return;
		}
		switch (slot) {
			case SetupMenuHost.NAV_PREV -> openJoinBrowser(player, page - 1);
			case SetupMenuHost.NAV_CONFIRM -> openJoinBrowser(player, page + 1);
			case SetupMenuHost.NAV_BACK -> openMain(player);
			default -> {
			}
		}
	}

	private static void tryJoin(ServerPlayer player, Party party) {
		if (PartyManager.byMember(player.getUUID()).isPresent()) {
			player.sendSystemMessage(Component.literal("Leave your current party first."));
			return;
		}
		if (party.access == PartyAccess.CLOSED) {
			player.sendSystemMessage(Component.literal("That party is closed."));
			return;
		}
		if (!PartyManager.canAddMember(party)) {
			player.sendSystemMessage(Component.literal("That party is full."));
			return;
		}
		if (party.access == PartyAccess.PASSWORD) {
			PromptService.ask(player, PromptService.Kind.JOIN_PASSWORD, text -> {
				if (!PartyManager.checkPassword(party, text)) {
					player.sendSystemMessage(Component.literal("Wrong password."));
					openJoinBrowser(player, JOIN_PAGE.getOrDefault(player.getUUID(), 0));
					return;
				}
				finishJoin(player, party);
			});
			return;
		}
		finishJoin(player, party);
	}

	private static void finishJoin(ServerPlayer player, Party party) {
		if (!PartyManager.addMember(party, player.getUUID())) {
			player.sendSystemMessage(Component.literal("That party is full."));
			return;
		}
		boolean wasHolding = HoldingCamera.isHolding(player.getUUID());
		HoldingCamera.stop(player);
		PartyManager.markUnlocked(player.getUUID());
		if (wasHolding) {
			PartySpawnService.placeAtPartyOriginOnce(player, party);
		} else {
			PartySpawnService.setPartyOriginUnlessBed(player, party);
		}
		PrefixService.refresh(player);
		player.closeContainer();
		if (wasHolding) {
			player.sendSystemMessage(Component.literal("Joined \"" + party.name + "\". Welcome — placed at your party's origin."));
		} else {
			player.sendSystemMessage(Component.literal("Joined party \"" + party.name + "\". Origin updated (you were not teleported)."));
		}
	}

	public static void openManage(ServerPlayer player, Party party, int page) {
		openManage(player, party, page, false);
	}

	public static void openManage(ServerPlayer player, Party party, int page, boolean colorPalette) {
		MANAGE_PAGE.put(player.getUUID(), page);
		SetupMenuHost.runReplacing(() -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> onManageClick(player, party, slot, colorPalette), true, PartyChestMenu.ROWS_DOUBLE);
			menu.fill(GuiItems.filler());
			if (colorPalette) {
				PartyColors.fillPalette(menu);
				menu.setSlot(SetupMenuHost.NAV_BACK, GuiItems.button(Items.ARROW, "Cancel Color"));
			} else {
				menu.setSlot(10, GuiItems.button(Items.NAME_TAG, "Rename", party.name));
				menu.setSlot(11, GuiItems.button(Items.PAPER, "Retag", party.tag));
				menu.setSlot(12, GuiItems.button(Items.BRUSH, "Recolor: " + party.color, "Click brush, then pick a dye"));
				menu.setSlot(13, GuiItems.button(Items.IRON_DOOR, "Access: " + party.access.name()));
				menu.setSlot(14, GuiItems.button(Items.TNT, "Disband Party", "Deletes the party for everyone"));
				if (!party.setupExtras.isEmpty()) {
					menu.setSlot(15, GuiItems.button(Items.BOOK, "Party Options", "Locked when the party was created"));
				}
				menu.setSlot(16, GuiItems.button(Items.SKELETON_SKULL, "Kick Members", "Open kick list"));
				menu.setSlot(SetupMenuHost.NAV_BACK, GuiItems.button(Items.ARROW, "Back"));
			}
			return menu;
		}, Component.literal(colorPalette ? "Pick Color" : "Manage: " + party.name))));
	}

	private static void onManageClick(ServerPlayer player, Party party, int slot, boolean colorPalette) {
		if (colorPalette) {
			String picked = PartyColors.fromPaletteSlot(slot);
			if (picked != null) {
				PartyManager.setNameTagColor(party, null, null, picked);
				PrefixService.refreshParty(party, Servers.of(player));
				openManage(player, party, 0, false);
				return;
			}
			if (slot == SetupMenuHost.NAV_BACK) {
				openManage(player, party, 0, false);
			}
			return;
		}
		switch (slot) {
			case 10 -> PromptService.ask(player, PromptService.Kind.PARTY_NAME, text -> {
				PartyManager.setNameTagColor(party, sanitizeName(text), null, null);
				PrefixService.refreshParty(party, Servers.of(player));
				openManage(player, party, 0);
			});
			case 11 -> PromptService.ask(player, PromptService.Kind.PARTY_TAG, text -> {
				PartyManager.setNameTagColor(party, null, sanitizeTag(text), null);
				PrefixService.refreshParty(party, Servers.of(player));
				openManage(player, party, 0);
			});
			case 12 -> openManage(player, party, 0, true);
			case 13 -> {
				PartyAccess next = nextAccess(party.access);
				if (next == PartyAccess.PASSWORD) {
					PromptService.ask(player, PromptService.Kind.PARTY_PASSWORD, text -> {
						PartyManager.setPassword(party, text);
						openManage(player, party, 0);
					});
				} else {
					PartyManager.setAccess(party, next);
					openManage(player, party, 0);
				}
			}
			case 14 -> {
				String partyName = party.name;
				List<UUID> members = new ArrayList<>(party.members);
				PartyManager.delete(party);
				for (UUID memberId : members) {
					ServerPlayer member = Servers.of(player).getPlayerList().getPlayer(memberId);
					if (member != null) {
						PartySpawnService.clearPartyOrigin(member);
						PrefixService.refresh(member);
						member.sendSystemMessage(Component.literal("Party \"" + partyName + "\" was disbanded."));
					}
				}
				player.closeContainer();
			}
			case 15 -> {
				if (!party.setupExtras.isEmpty()) {
					openLockedOptions(player, party, true);
				}
			}
			case 16 -> openKickList(player, party, 0);
			case SetupMenuHost.NAV_BACK -> openMain(player);
			default -> {
			}
		}
	}

	private static void openKickList(ServerPlayer player, Party party, int page) {
		List<UUID> members = new ArrayList<>(party.members);
		members.remove(party.leader);
		int safePage = Math.max(0, Math.min(page, Pagination.pageCount(members.size()) - 1));
		List<UUID> pageItems = Pagination.page(members, safePage);
		SetupMenuHost.runReplacing(() -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> {
				if (slot >= 0 && slot < pageItems.size()) {
					UUID target = pageItems.get(slot);
					PartyManager.removeMember(party, target);
					ServerPlayer online = Servers.of(player).getPlayerList().getPlayer(target);
					if (online != null) {
						PartySpawnService.clearPartyOrigin(online);
						PrefixService.refresh(online);
						online.sendSystemMessage(Component.literal("You were kicked from \"" + party.name + "\"."));
					}
					openKickList(player, party, safePage);
				} else if (slot == SetupMenuHost.NAV_PREV) {
					openKickList(player, party, safePage - 1);
				} else if (slot == SetupMenuHost.NAV_CONFIRM) {
					openKickList(player, party, safePage + 1);
				} else if (slot == SetupMenuHost.NAV_BACK) {
					openManage(player, party, 0);
				}
			}, true, PartyChestMenu.ROWS_DOUBLE);
			menu.fill(GuiItems.filler());
			for (int i = 0; i < pageItems.size(); i++) {
				UUID idMember = pageItems.get(i);
				String name = resolveName(Servers.of(player), idMember);
				menu.setSlot(i, GuiItems.button(Items.PLAYER_HEAD, "Kick " + name));
			}
			menu.setSlot(SetupMenuHost.NAV_BACK, GuiItems.button(Items.ARROW, "Back"));
			menu.setSlot(SetupMenuHost.NAV_PREV, GuiItems.button(Items.ARROW, "Prev"));
			menu.setSlot(SetupMenuHost.NAV_INFO, GuiItems.button(Items.BOOK, "Page " + (safePage + 1) + "/" + Pagination.pageCount(members.size())));
			menu.setSlot(SetupMenuHost.NAV_CONFIRM, GuiItems.button(Items.ARROW, "Next"));
			return menu;
		}, Component.literal("Kick Members"))));
	}

	private static void openLockedOptions(ServerPlayer player, Party party, boolean fromManage) {
		List<Map.Entry<String, String>> extras = new ArrayList<>(party.setupExtras.entrySet());
		SetupMenuHost.runReplacing(() -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> {
				if (slot == SetupMenuHost.NAV_BACK) {
					if (fromManage && party.isLeader(player.getUUID())) {
						openManage(player, party, 0);
					} else {
						openMain(player);
					}
					return;
				}
				if (slot >= 0 && slot < extras.size()) {
					player.sendSystemMessage(Component.literal("This choice was locked when the party was created."));
				}
			}, true, PartyChestMenu.ROWS_DOUBLE);
			menu.fill(GuiItems.filler());
			int shown = Math.min(extras.size(), SetupMenuHost.CONTENT_SLOTS);
			for (int i = 0; i < shown; i++) {
				Map.Entry<String, String> extra = extras.get(i);
				menu.setSlot(i, lockedIcon(party, extra.getKey(), extra.getValue()));
			}
			menu.setSlot(SetupMenuHost.NAV_BACK, GuiItems.button(Items.ARROW, "Back"));
			return menu;
		}, Component.literal("Party Options"))));
	}

	private static ItemStack lockedIcon(Party party, String key, String value) {
		for (var extension : SpawnSetupController.extensions()) {
			try {
				ItemStack icon = extension.iconForLockedExtra(party, key, value);
				if (icon != null && !icon.isEmpty()) {
					return icon;
				}
			} catch (Throwable t) {
				SpawnPartyMod.LOGGER.warn("Setup extension {} iconForLockedExtra failed", extension.id(), t);
			}
		}
		return GuiItems.button(Items.PAPER, prettyExtra(key), value, "Locked");
	}

	private static String prettyExtra(String key) {
		if (key == null || key.isBlank()) return "Option";
		String path = key;
		int colon = path.lastIndexOf(':');
		if (colon >= 0 && colon < path.length() - 1) {
			path = path.substring(colon + 1);
		}
		int slash = path.lastIndexOf('/');
		if (slash >= 0 && slash < path.length() - 1) {
			path = path.substring(slash + 1);
		}
		path = path.replace('_', ' ').trim();
		if (path.isEmpty()) return key;
		return Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	private static void leaveParty(ServerPlayer player, Party party) {
		PartyManager.removeMember(party, player.getUUID());
		PartySpawnService.clearPartyOrigin(player);
		PrefixService.refresh(player);
		player.closeContainer();
		player.sendSystemMessage(Component.literal("Left party \"" + party.name + "\"."));
	}

	public static void reopenIfHolding(ServerPlayer player) {
		if (!HoldingCamera.isHolding(player.getUUID())) return;
		if (PromptService.hasPending(player.getUUID())) return;
		if (player.containerMenu != player.inventoryMenu) return;
		switch (HoldingCamera.phase(player.getUUID())) {
			case SETUP, LOCATING -> SpawnSetupController.reopenIfNeeded(player);
			default -> openMain(player);
		}
	}

	private static String sanitizeName(String raw) {
		String name = raw == null ? "Party" : raw.trim();
		if (name.length() > 24) name = name.substring(0, 24);
		if (name.isBlank()) name = "Party";
		return name;
	}

	private static String sanitizeTag(String raw) {
		String tag = raw == null ? "P" : raw.trim();
		if (tag.length() > 6) tag = tag.substring(0, 6);
		if (tag.isBlank()) tag = "P";
		return tag;
	}

	private static String tagFromName(String name) {
		String compact = name.replaceAll("[^A-Za-z0-9]", "");
		if (compact.length() >= 2) return compact.substring(0, Math.min(4, compact.length())).toUpperCase(Locale.ROOT);
		return "PTY";
	}

	private static PartyAccess nextAccess(PartyAccess current) {
		return switch (current) {
			case OPEN -> PartyAccess.CLOSED;
			case CLOSED -> PartyAccess.PASSWORD;
			case PASSWORD -> PartyAccess.OPEN;
		};
	}

	private static final class Draft {
		String name;
		String tag;
		String color = "gold";
		PartyAccess access = PartyAccess.OPEN;
		String password = "";

		static Draft defaults(ServerPlayer player) {
			Draft d = new Draft();
			String base = player.getGameProfile().name();
			d.name = base + "'s Party";
			d.tag = tagFromName(base);
			return d;
		}
	}

	private static String resolveName(net.minecraft.server.MinecraftServer server, UUID id) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			return online.getGameProfile().name();
		}
		return server.services().nameToIdCache().get(id).map(n -> n.name()).orElse(id.toString());
	}
}
