package spawnparty.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
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

	public static void beginCreate(ServerPlayer player, PartyDraft draft) {
		Job job = new Job();
		job.kind = Kind.CREATE;
		job.draft = draft;
		job.session = new SpawnSetupSession();
		job.wasHolding = HoldingCamera.isHolding(player.getUUID());
		JOBS.put(player.getUUID(), job);
		startPagesOrLocate(player, job);
	}

	public static void beginSolo(ServerPlayer player) {
		Job job = new Job();
		job.kind = Kind.SOLO;
		job.session = new SpawnSetupSession();
		job.wasHolding = HoldingCamera.isHolding(player.getUUID());
		if (!job.wasHolding) {
			HoldingCamera.start(player);
			job.wasHolding = true;
		}
		JOBS.put(player.getUUID(), job);
		startPagesOrLocate(player, job);
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
		job.pendingPages = new ArrayList<>();
		for (SpawnSetupExtension extension : EXTENSIONS) {
			if (extension.wantsSelection(player)) {
				job.pendingPages.add(extension);
			}
		}
		job.pageIndex = 0;
		if (job.pendingPages.isEmpty()) {
			startLocate(player, job);
			return;
		}
		HoldingCamera.setPhase(player.getUUID(), HoldPhase.SETUP);
		openNextPage(player, job);
	}

	private static void openNextPage(ServerPlayer player, Job job) {
		if (job.pageIndex >= job.pendingPages.size()) {
			startLocate(player, job);
			return;
		}
		job.state = State.PAGES;
		SpawnSetupExtension extension = job.pendingPages.get(job.pageIndex);
		job.pageIndex++;
		int beforeId = player.containerMenu.containerId;
		extension.openSelection(player, job.session, () -> {
			if (!JOBS.containsKey(player.getUUID())) return;
			openNextPage(player, job);
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
				job.probe = candidate;
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
	}

	public record PartyDraft(String name, String tag, String color, PartyAccess access, String password) {
	}
}
