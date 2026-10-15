package spawnparty.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import spawnparty.api.SetupPage;
import spawnparty.SpawnPartyConfig;
import spawnparty.SpawnPartyMod;
import spawnparty.api.SetupMenuHost;
import spawnparty.api.SetupPageFiller;
import spawnparty.api.SpawnLocateRequest;
import spawnparty.api.SpawnSetupExtension;
import spawnparty.api.SpawnSetupSession;
import spawnparty.chat.PrefixService;
import spawnparty.gui.GuiItems;
import spawnparty.gui.PartyMenus;
import spawnparty.party.Party;
import spawnparty.party.PartyAccess;
import spawnparty.party.PartyManager;
import spawnparty.util.Servers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chains setup extensions, then locates an origin across ticks.
 */
public final class SpawnSetupController {

	private static final List<SpawnSetupExtension> EXTENSIONS = new CopyOnWriteArrayList<>();
	private static final Map<UUID, Job> JOBS = new ConcurrentHashMap<>();
	private static final Map<UUID, Runnable> REOPEN = new ConcurrentHashMap<>();

	private static final int SEARCH_PER_TICK = 16;
	private static final int MAX_LOCATE_TICKS = 20 * 30;

	private SpawnSetupController() {
	}

	public static void register(SpawnSetupExtension extension) {
		if (extension == null) return;
		for (SpawnSetupExtension existing : EXTENSIONS) {
			if (existing.id().equals(extension.id())) {
				SpawnPartyMod.LOGGER.warn("Replacing setup extension {}", extension.id());
				EXTENSIONS.remove(existing);
				break;
			}
		}
		EXTENSIONS.add(extension);
		SpawnPartyMod.LOGGER.info("Registered setup extension {}", extension.id());
	}

	public static List<SpawnSetupExtension> extensions() {
		return List.copyOf(EXTENSIONS);
	}

	public static boolean anyWantsSelection(ServerPlayer player) {
		for (SpawnSetupExtension extension : EXTENSIONS) {
			if (extension.wantsSelection(player)) return true;
		}
		return false;
	}

	public static void rememberReopen(UUID playerId, Runnable reopen) {
		if (playerId != null && reopen != null) {
			REOPEN.put(playerId, reopen);
		}
	}

	public static void drop(UUID playerId) {
		JOBS.remove(playerId);
		REOPEN.remove(playerId);
	}

	public static void reopenIfNeeded(ServerPlayer player) {
		Job job = JOBS.get(player.getUUID());
		if (job == null) return;
		if (job.state == State.PAGES) {
			Runnable reopen = REOPEN.get(player.getUUID());
			if (reopen != null) {
				reopen.run();
			}
			return;
		}
		if (job.state == State.SEARCH || job.state == State.PREPARE || job.state == State.VALIDATE) {
			openStatus(player, job);
		}
	}

	/**
	 * Identity page Forward. Reuses the session if the player already stepped
	 * into extension pages, then either opens those pages or starts locate.
	 * The party is created only after locate finishes.
	 */
	public static void advanceFromIdentity(ServerPlayer player, PartyDraft draft) {
		Job existing = JOBS.get(player.getUUID());
		Job job;
		if (existing != null && existing.kind == Kind.CREATE && existing.state == State.PAGES) {
			job = existing;
			job.draft = draft;
		} else {
			if (existing != null) {
				JOBS.remove(player.getUUID());
				REOPEN.remove(player.getUUID());
			}
			job = new Job();
			job.kind = Kind.CREATE;
			job.draft = draft;
			job.session = new SpawnSetupSession();
			job.wasHolding = HoldingCamera.isHolding(player.getUUID());
			collectPages(player, job);
			JOBS.put(player.getUUID(), job);
		}
		if (job.pendingPages.isEmpty()) {
			startLocate(player, job);
		} else {
			openPage(player, job, 0);
		}
	}

	/** True while the player is on the identity page or an extension page. */
	public static boolean hasWizard(UUID playerId) {
		Job job = JOBS.get(playerId);
		return job != null && job.state == State.PAGES;
	}

	/**
	 * Paint corner Back and Forward for the active wizard. Status pages keep Back
	 * so locate can be cancelled before the party exists.
	 */
	public static void stampNav(ServerPlayer player, SetupPage page, boolean statusOnly) {
		Job job = JOBS.get(player.getUUID());
		if (job == null || page == null) return;
		if (job.navBack != null) {
			page.setSlot(SetupMenuHost.NAV_BACK, GuiItems.button(Items.ARROW, "Back"), job.navBack);
		} else {
			page.setSlot(SetupMenuHost.NAV_BACK, GuiItems.filler(), null);
		}
		if (!statusOnly && job.navForward != null) {
			Item icon = "Create Party".equals(job.forwardLabel) ? Items.EMERALD_BLOCK : Items.ARROW;
			String label = job.forwardLabel == null ? "Next" : job.forwardLabel;
			page.setSlot(SetupMenuHost.NAV_CONFIRM, GuiItems.button(icon, label), job.navForward);
		} else {
			page.setSlot(SetupMenuHost.NAV_CONFIRM, GuiItems.filler(), null);
		}
	}

	public static void beginSolo(ServerPlayer player) {
		Job job = new Job();
		job.kind = Kind.SOLO;
		job.session = new SpawnSetupSession();
		if (!HoldingCamera.isHolding(player.getUUID())) {
			HoldingCamera.start(player);
		}
		job.wasHolding = true;
		collectPages(player, job);
		JOBS.put(player.getUUID(), job);
		if (job.pendingPages.isEmpty()) {
			startLocate(player, job);
		} else {
			openPage(player, job, 0);
		}
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Job job = JOBS.get(player.getUUID());
			if (job == null) continue;
			if (job.state == State.SEARCH || job.state == State.PREPARE || job.state == State.VALIDATE) {
				advanceLocate(player, job);
			}
		}
	}

	private static void startPagesOrLocate(ServerPlayer player, Job job) {
		collectPages(player, job);
		job.pageIndex = 0;
		if (job.pendingPages.isEmpty()) {
			startLocate(player, job);
			return;
		}
		HoldingCamera.setPhase(player.getUUID(), HoldPhase.SETUP);
		openPage(player, job, 0);
	}

	private static void collectPages(ServerPlayer player, Job job) {
		job.pendingPages = new ArrayList<>();
		for (SpawnSetupExtension extension : EXTENSIONS) {
			if (extension.wantsSelection(player)) {
				job.pendingPages.add(extension);
			}
		}
	}

	private static void openPage(ServerPlayer player, Job job, int index) {
		if (!JOBS.containsKey(player.getUUID())) return;
		if (index < 0) {
			job.state = State.PAGES;
			if (job.kind == Kind.CREATE) {
				PartyMenus.openCreate(player);
			}
			return;
		}
		if (index >= job.pendingPages.size()) {
			startLocate(player, job);
			return;
		}
		job.pageIndex = index;
		job.state = State.PAGES;
		SpawnSetupExtension extension = job.pendingPages.get(index);
		boolean last = index == job.pendingPages.size() - 1;
		boolean showBack = job.kind == Kind.CREATE || index > 0;
		job.navBack = showBack ? () -> openPage(player, job, index - 1) : null;
		job.forwardLabel = last ? (job.kind == Kind.SOLO ? "Continue" : "Create Party") : "Next";
		job.navForward = () -> {
			if (!JOBS.containsKey(player.getUUID())) return;
			Component blocked = null;
			try {
				blocked = extension.forwardBlocked(player, job.session);
			} catch (Throwable t) {
				SpawnPartyMod.LOGGER.warn("Setup extension {} forwardBlocked failed", extension.id(), t);
			}
			if (blocked != null) {
				player.sendSystemMessage(blocked);
				openPage(player, job, index);
				return;
			}
			openPage(player, job, index + 1);
		};
		int beforeId = player.containerMenu.containerId;
		extension.openSelection(player, job.session, () -> {
			// Choice clicks may call done. Only the corner Forward button advances.
		});
		if (player.containerMenu.containerId == beforeId) {
			failSelection(player, job, extension.id());
		}
	}

	private static void failSelection(ServerPlayer player, Job job, Identifier extensionId) {
		SpawnPartyMod.LOGGER.error("Setup extension {} did not open a page", extensionId);
		JOBS.remove(player.getUUID());
		REOPEN.remove(player.getUUID());
		if (job.wasHolding) {
			HoldingCamera.setPhase(player.getUUID(), HoldPhase.MENU);
		}
		if (job.kind == Kind.CREATE) {
			player.sendSystemMessage(Component.literal("Could not open setup. Returning to Create Party."));
			PartyMenus.openCreate(player);
		} else {
			player.sendSystemMessage(Component.literal("Could not open setup."));
		}
	}

	private static void startLocate(ServerPlayer player, Job job) {
		job.request = new SpawnLocateRequest();
		for (SpawnSetupExtension extension : EXTENSIONS) {
			extension.contributeLocate(job.session, job.request);
		}
		if (job.request.biome().isEmpty()) {
			PartySpawnLocator.parseBiome(SpawnPartyConfig.INSTANCE.spawnBiome)
					.ifPresent(job.request::setBiome);
		}
		ServerLevel level = Servers.of(player).overworld();
		job.candidates = PartySpawnLocator.candidates(level.getRespawnData().pos());
		job.candidateIndex = 0;
		job.ticks = 0;
		job.chunkCursor = new int[] {0};
		job.state = State.SEARCH;
		job.status = "Finding spawn…";
		job.navForward = null;
		job.forwardLabel = null;
		job.navBack = job.kind == Kind.CREATE || !job.pendingPages.isEmpty()
				? () -> abortLocate(player, job)
				: null;
		HoldingCamera.setPhase(player.getUUID(), HoldPhase.LOCATING);
		if (job.wasHolding) {
			openStatus(player, job);
		} else {
			player.sendSystemMessage(Component.literal("Finding a party spawn…"));
		}
	}

	private static void openStatus(ServerPlayer player, Job job) {
		SetupMenuHost.open(player, Component.literal("Spawn Party"), (SetupPageFiller) page -> {
			page.fillFiller();
			page.setSlot(22, GuiItems.button(Items.CLOCK, job.status,
					"Stay here — this may take a few seconds.",
					"Origins stay " + SpawnPartyConfig.INSTANCE.partySpawnDistance + " blocks apart."), null);
		}, true);
	}

	private static void advanceLocate(ServerPlayer player, Job job) {
		job.ticks++;
		if (job.ticks > MAX_LOCATE_TICKS) {
			finishFallback(player, job);
			return;
		}
		ServerLevel level = Servers.of(player).overworld();
		int sampleY = job.request.sampleY().orElseGet(() -> PartySpawnLocator.defaultSampleY(level));
		Identifier biomeId = job.request.biome().orElse(null);

		if (job.state == State.SEARCH) {
			int examined = 0;
			while (job.candidateIndex < job.candidates.size() && examined < SEARCH_PER_TICK) {
				BlockPos candidate = job.candidates.get(job.candidateIndex++);
				examined++;
				if (!PartyManager.isFarEnoughFromOthers(candidate, null)) continue;
				if (biomeId != null && !PartySpawnLocator.biomeMatches(
						PartySpawnLocator.sampleBiome(level, candidate.getX(), candidate.getZ(), sampleY), biomeId)) {
					continue;
				}
				job.probe = PartySpawnLocator.villageOrCandidate(level, candidate, job.request, null);
				job.chunkCursor[0] = 0;
				job.state = State.PREPARE;
				job.status = "Generating chunks…";
				if (job.wasHolding) openStatus(player, job);
				return;
			}
			if (job.candidateIndex >= job.candidates.size()) {
				finishFallback(player, job);
			}
			return;
		}

		if (job.state == State.PREPARE) {
			boolean ready = SpawnChunkPreparer.tick(level, job.probe, job.request.prepareChunkRadius(), job.chunkCursor);
			if (!ready) return;
			job.state = State.VALIDATE;
			return;
		}

		BlockPos standing = PartySpawnLocator.findSafeStanding(level, job.probe);
		if (standing == null
				|| !PartyManager.isFarEnoughFromOthers(standing, null)
				|| !job.request.standingOk(standing)) {
			job.state = State.SEARCH;
			return;
		}
		float yaw = SpawnPartyConfig.INSTANCE.holdingYawTowardSunrise ? -90.0F : 0.0F;
		finish(player, job, new PartySpawnLocator.LocatedSpawn(level.dimension(), standing, yaw, 0.0F));
	}

	private static void abortLocate(ServerPlayer player, Job job) {
		if (JOBS.get(player.getUUID()) != job) return;
		job.state = State.PAGES;
		job.ticks = 0;
		job.candidateIndex = 0;
		job.probe = null;
		job.chunkCursor[0] = 0;
		if (job.pendingPages.isEmpty()) {
			PartyMenus.openCreate(player);
			return;
		}
		openPage(player, job, job.pendingPages.size() - 1);
	}

	private static void finishFallback(ServerPlayer player, Job job) {
		ServerLevel level = Servers.of(player).overworld();
		var located = PartySpawnLocator.locate(level, player, job.request, null);
		located.ifPresentOrElse(
				spawn -> finish(player, job, spawn),
				() -> {
					player.sendSystemMessage(Component.literal("Could not find a spawn. Try again."));
					JOBS.remove(player.getUUID());
					REOPEN.remove(player.getUUID());
					if (job.kind == Kind.CREATE && job.wasHolding) {
						HoldingCamera.setPhase(player.getUUID(), HoldPhase.MENU);
						PartyMenus.openCreate(player);
					} else if (job.wasHolding) {
						JOBS.put(player.getUUID(), job);
						HoldingCamera.setPhase(player.getUUID(), HoldPhase.SETUP);
						startPagesOrLocate(player, job);
					}
				});
	}

	private static void finish(ServerPlayer player, Job job, PartySpawnLocator.LocatedSpawn spawn) {
		JOBS.remove(player.getUUID());
		REOPEN.remove(player.getUUID());

		PartyDraft draft = job.draft;
		if (job.kind == Kind.SOLO) {
			String base = player.getGameProfile().name();
			String name = base.length() > 20 ? base.substring(0, 20) : base;
			draft = new PartyDraft(name, SoloSpawnService.compactTag(base), "gold", PartyAccess.CLOSED, "");
		}

		Party party = PartyManager.create(draft.name(), draft.tag(), player.getUUID(),
				spawn.dimension(), spawn.pos(), spawn.yaw(), spawn.pitch());
		party.color = draft.color();
		party.access = draft.access();
		if (draft.access() == PartyAccess.PASSWORD) {
			PartyManager.setPassword(party, draft.password());
		}
		party.setupExtras.clear();
		party.setupExtras.putAll(job.session.toStringMap());
		party.setupLocked = true;
		PartyManager.save();
		PartyMenus.discardDraft(player.getUUID());

		for (SpawnSetupExtension extension : EXTENSIONS) {
			try {
				extension.onPartyLocked(player, party, job.session);
			} catch (Throwable t) {
				SpawnPartyMod.LOGGER.warn("Setup extension {} onPartyLocked failed", extension.id(), t);
			}
		}

		boolean wasHolding = job.wasHolding;
		if (wasHolding) {
			HoldingCamera.stop(player);
			player.closeContainer();
		}
		PartyManager.markUnlocked(player.getUUID());
		if (wasHolding) {
			PartySpawnService.placeAtPartyOriginOnce(player, party);
			player.sendSystemMessage(Component.literal(
					job.kind == Kind.SOLO
							? "This is your origin spawn."
							: "Party \"" + party.name + "\" created. This is your origin spawn."));
		} else {
			PartySpawnService.setPartyOrigin(player, party);
			player.sendSystemMessage(Component.literal("Party \"" + party.name + "\" created. Origin set (you were not teleported)."));
		}
		PrefixService.refresh(player);
	}

	private enum Kind { CREATE, SOLO }

	private enum State { PAGES, SEARCH, PREPARE, VALIDATE }

	private static final class Job {
		Kind kind;
		PartyDraft draft;
		SpawnSetupSession session;
		boolean wasHolding;
		List<SpawnSetupExtension> pendingPages = List.of();
		int pageIndex;
		SpawnLocateRequest request;
		List<BlockPos> candidates = List.of();
		int candidateIndex;
		BlockPos probe;
		int[] chunkCursor = new int[] {0};
		int ticks;
		State state = State.PAGES;
		String status = "Finding spawn…";
		Runnable navBack;
		Runnable navForward;
		String forwardLabel;
	}

	public record PartyDraft(String name, String tag, String color, PartyAccess access, String password) {
	}
}
