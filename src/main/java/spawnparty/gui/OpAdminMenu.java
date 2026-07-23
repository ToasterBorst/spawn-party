package spawnparty.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Items;
import spawnparty.chat.PrefixService;
import spawnparty.chat.PromptService;
import spawnparty.party.Party;
import spawnparty.party.PartyAccess;
import spawnparty.party.PartyManager;
import spawnparty.spawn.PartySpawnLocator;
import spawnparty.spawn.PartySpawnService;
import spawnparty.util.Servers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpAdminMenu {

	private static final Map<UUID, Integer> PAGE = new ConcurrentHashMap<>();

	private OpAdminMenu() {
	}

	public static void open(ServerPlayer op) {
		open(op, PAGE.getOrDefault(op.getUUID(), 0));
	}

	public static void open(ServerPlayer op, int page) {
		List<Party> parties = new ArrayList<>(PartyManager.all());
		int safePage = Math.max(0, Math.min(page, Pagination.pageCount(parties.size()) - 1));
		PAGE.put(op.getUUID(), safePage);
		List<Party> pageItems = Pagination.page(parties, safePage);

		op.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> onListClick(op, slot, parties, safePage), true);
			menu.fill(GuiItems.filler());
			for (int i = 0; i < pageItems.size(); i++) {
				Party party = pageItems.get(i);
				menu.setSlot(i, GuiItems.button(
						Items.BOOK,
						"[" + party.tag + "] " + party.name,
						"Access: " + party.access.name(),
						"Members: " + party.members.size(),
						"Spawn: " + party.spawn.getX() + " " + party.spawn.getY() + " " + party.spawn.getZ(),
						"Click to manage"
				));
			}
			menu.setSlot(21, GuiItems.button(Items.ARROW, "Prev Page"));
			menu.setSlot(22, GuiItems.button(Items.PAPER, "Page " + (safePage + 1) + "/" + Pagination.pageCount(parties.size())));
			menu.setSlot(23, GuiItems.button(Items.ARROW, "Next Page"));
			menu.setSlot(26, GuiItems.button(Items.BARRIER, "Close"));
			return menu;
		}, Component.literal("Spawn Party Admin")));
	}

	private static void onListClick(ServerPlayer op, int slot, List<Party> parties, int page) {
		List<Party> pageItems = Pagination.page(parties, page);
		if (slot >= 0 && slot < pageItems.size()) {
			openParty(op, pageItems.get(slot));
			return;
		}
		switch (slot) {
			case 21 -> open(op, page - 1);
			case 23 -> open(op, page + 1);
			case 26 -> op.closeContainer();
			default -> {
			}
		}
	}

	private static void openParty(ServerPlayer op, Party party) {
		op.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> onPartyClick(op, party, slot), true);
			menu.fill(GuiItems.filler());
			menu.setSlot(10, GuiItems.button(Items.NAME_TAG, "Rename", party.name));
			menu.setSlot(11, GuiItems.button(Items.PAPER, "Retag", party.tag));
			menu.setSlot(12, GuiItems.button(Items.IRON_DOOR, "Access: " + party.access.name()));
			menu.setSlot(13, GuiItems.button(Items.COMPASS, "Relocate Spawn", "Find a new origin (no player teleport)"));
			menu.setSlot(14, GuiItems.button(Items.GOLDEN_HELMET, "Transfer Leader"));
			menu.setSlot(16, GuiItems.button(Items.TNT, "Delete Party", "Force delete"));
			menu.setSlot(22, GuiItems.button(Items.ARROW, "Back"));
			return menu;
		}, Component.literal("Admin: " + party.name)));
	}

	private static void onPartyClick(ServerPlayer op, Party party, int slot) {
		switch (slot) {
			case 10 -> PromptService.ask(op, PromptService.Kind.PARTY_NAME, text -> {
				PartyManager.setNameTagColor(party, text.trim(), null, null);
				PrefixService.refreshParty(party, Servers.of(op));
				openParty(op, party);
			});
			case 11 -> PromptService.ask(op, PromptService.Kind.PARTY_TAG, text -> {
				PartyManager.setNameTagColor(party, null, text.trim(), null);
				PrefixService.refreshParty(party, Servers.of(op));
				openParty(op, party);
			});
			case 12 -> {
				PartyAccess next = switch (party.access) {
					case OPEN -> PartyAccess.CLOSED;
					case CLOSED -> PartyAccess.PASSWORD;
					case PASSWORD -> PartyAccess.OPEN;
				};
				if (next == PartyAccess.PASSWORD) {
					PromptService.ask(op, PromptService.Kind.PARTY_PASSWORD, text -> {
						PartyManager.setPassword(party, text);
						openParty(op, party);
					});
				} else {
					PartyManager.setAccess(party, next);
					openParty(op, party);
				}
			}
			case 13 -> {
				op.closeContainer();
				op.sendSystemMessage(Component.literal("Finding new spawn for " + party.name + "…"));
				Servers.of(op).execute(() -> {
					var located = PartySpawnLocator.locate(Servers.of(op).overworld(), op);
					if (located.isEmpty()) {
						op.sendSystemMessage(Component.literal("Locate failed."));
						return;
					}
					var spawn = located.get();
					// Temporarily ignore this party for distance checks by relocating after locate;
					// locate already excludes none — force distance by checking others only.
					BlockPos pos = spawn.pos();
					if (!PartyManager.isFarEnoughFromOthers(pos, party.id)) {
						op.sendSystemMessage(Component.literal("Candidate too close; try again."));
						openParty(op, party);
						return;
					}
					PartyManager.setSpawn(party, spawn.dimension(), pos, spawn.yaw(), spawn.pitch());
					PartySpawnService.refreshOriginsForParty(party, Servers.of(op));
					op.sendSystemMessage(Component.literal("Party origin relocated to " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
					openParty(op, party);
				});
			}
			case 14 -> openTransfer(op, party, 0);
			case 16 -> {
				String partyName = party.name;
				List<UUID> members = new ArrayList<>(party.members);
				PartyManager.delete(party);
				for (UUID memberId : members) {
					ServerPlayer member = Servers.of(op).getPlayerList().getPlayer(memberId);
					if (member != null) {
						PartySpawnService.clearPartyOrigin(member);
						PrefixService.refresh(member);
						member.sendSystemMessage(Component.literal("An operator deleted party \"" + partyName + "\"."));
					}
				}
				op.sendSystemMessage(Component.literal("Deleted party."));
				open(op);
			}
			case 22 -> open(op);
			default -> {
			}
		}
	}

	private static void openTransfer(ServerPlayer op, Party party, int page) {
		List<UUID> members = new ArrayList<>(party.members);
		int safePage = Math.max(0, Math.min(page, Pagination.pageCount(members.size()) - 1));
		List<UUID> pageItems = Pagination.page(members, safePage);
		op.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> {
				if (slot >= 0 && slot < pageItems.size()) {
					UUID target = pageItems.get(slot);
					PartyManager.transferLeader(party, target);
					op.sendSystemMessage(Component.literal("Leader transferred."));
					openParty(op, party);
				} else if (slot == 21) {
					openTransfer(op, party, safePage - 1);
				} else if (slot == 23) {
					openTransfer(op, party, safePage + 1);
				} else if (slot == 26) {
					openParty(op, party);
				}
			}, true);
			menu.fill(GuiItems.filler());
			for (int i = 0; i < pageItems.size(); i++) {
				UUID idMember = pageItems.get(i);
				String name = resolveName(Servers.of(op), idMember);
				boolean leader = party.isLeader(idMember);
				menu.setSlot(i, GuiItems.button(Items.GOLDEN_HELMET, (leader ? "* " : "") + name));
			}
			menu.setSlot(21, GuiItems.button(Items.ARROW, "Prev"));
			menu.setSlot(23, GuiItems.button(Items.ARROW, "Next"));
			menu.setSlot(26, GuiItems.button(Items.BARRIER, "Back"));
			return menu;
		}, Component.literal("Transfer Leader")));
	}

	private static String resolveName(net.minecraft.server.MinecraftServer server, UUID id) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			return online.getGameProfile().name();
		}
		return server.services().nameToIdCache().get(id).map(n -> n.name()).orElse(id.toString());
	}
}
