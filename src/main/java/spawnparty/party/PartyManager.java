package spawnparty.party;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import spawnparty.SpawnPartyConfig;
import spawnparty.SpawnPartyMod;
import spawnparty.data.PartyStore;
import spawnparty.util.PasswordUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PartyManager {

	private static Path configDir;
	private static final List<Party> PARTIES = new CopyOnWriteArrayList<>();
	private static final List<UUID> UNLOCKED = new CopyOnWriteArrayList<>();

	private PartyManager() {
	}

	public static void init(Path dir) {
		configDir = dir;
		PARTIES.clear();
		UNLOCKED.clear();
		PartyStore.StoreData data = PartyStore.load(dir);
		PARTIES.addAll(data.parties());
		UNLOCKED.addAll(data.unlockedPlayers());
		SpawnPartyMod.LOGGER.info("Loaded {} parties ({} unlocked players)", PARTIES.size(), UNLOCKED.size());
	}

	public static void save() {
		if (configDir != null) {
			PartyStore.save(configDir, new ArrayList<>(PARTIES), new ArrayList<>(UNLOCKED));
		}
	}

	public static boolean isUnlocked(UUID playerId) {
		return UNLOCKED.contains(playerId) || byMember(playerId).isPresent();
	}

	public static void markUnlocked(UUID playerId) {
		if (!UNLOCKED.contains(playerId)) {
			UNLOCKED.add(playerId);
			save();
		}
	}

	public static List<Party> all() {
		return List.copyOf(PARTIES);
	}

	public static Optional<Party> byId(UUID id) {
		return PARTIES.stream().filter(p -> p.id.equals(id)).findFirst();
	}

	public static Optional<Party> byMember(UUID playerId) {
		return PARTIES.stream().filter(p -> p.isMember(playerId)).findFirst();
	}

	public static Optional<Party> byNameIgnoreCase(String name) {
		return PARTIES.stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst();
	}

	public static Party create(String name, String tag, UUID leader, ResourceKey<Level> dim, BlockPos spawn, float yaw, float pitch) {
		Party party = new Party(UUID.randomUUID(), name, tag, leader);
		party.dimension = dim;
		party.spawn = spawn.immutable();
		party.yaw = yaw;
		party.pitch = pitch;
		PARTIES.add(party);
		save();
		return party;
	}

	public static void delete(Party party) {
		PARTIES.removeIf(p -> p.id.equals(party.id));
		save();
	}

	public static void addMember(Party party, UUID playerId) {
		if (!party.members.contains(playerId)) {
			party.members.add(playerId);
			save();
		}
	}

	public static void removeMember(Party party, UUID playerId) {
		party.members.remove(playerId);
		if (party.members.isEmpty()) {
			delete(party);
			return;
		}
		if (party.isLeader(playerId)) {
			party.leader = party.members.getFirst();
		}
		save();
	}

	public static void setAccess(Party party, PartyAccess access) {
		party.access = access;
		if (access != PartyAccess.PASSWORD) {
			party.passwordHash = "";
			party.passwordSalt = "";
		}
		save();
	}

	public static void setPassword(Party party, String rawPassword) {
		party.access = PartyAccess.PASSWORD;
		party.passwordSalt = PasswordUtil.newSalt();
		party.passwordHash = PasswordUtil.hash(rawPassword, party.passwordSalt);
		save();
	}

	public static boolean checkPassword(Party party, String rawPassword) {
		return PasswordUtil.matches(rawPassword, party.passwordSalt, party.passwordHash);
	}

	public static void setNameTagColor(Party party, String name, String tag, String color) {
		if (name != null && !name.isBlank()) party.name = name.trim();
		if (tag != null && !tag.isBlank()) party.tag = tag.trim();
		if (color != null && !color.isBlank()) party.color = color.trim().toLowerCase();
		save();
	}

	public static void setSpawn(Party party, ResourceKey<Level> dim, BlockPos spawn, float yaw, float pitch) {
		party.dimension = dim;
		party.spawn = spawn.immutable();
		party.yaw = yaw;
		party.pitch = pitch;
		save();
	}

	public static void transferLeader(Party party, UUID newLeader) {
		if (!party.isMember(newLeader)) {
			party.members.add(newLeader);
		}
		party.leader = newLeader;
		save();
	}

	public static boolean isFarEnoughFromOthers(BlockPos candidate, UUID ignorePartyId) {
		int min = SpawnPartyConfig.INSTANCE.partySpawnDistance;
		long minSq = (long) min * (long) min;
		for (Party party : PARTIES) {
			if (ignorePartyId != null && party.id.equals(ignorePartyId)) continue;
			if (party.spawn == null) continue;
			long dx = (long) party.spawn.getX() - candidate.getX();
			long dz = (long) party.spawn.getZ() - candidate.getZ();
			if (dx * dx + dz * dz < minSq) {
				return false;
			}
		}
		return true;
	}

	public static Collection<ServerPlayer> onlineMembers(Party party, net.minecraft.server.MinecraftServer server) {
		List<ServerPlayer> online = new ArrayList<>();
		for (UUID id : party.members) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				online.add(player);
			}
		}
		return online;
	}
}
