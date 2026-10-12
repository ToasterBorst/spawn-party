package spawnparty.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import spawnparty.SpawnPartyMod;
import spawnparty.party.Party;
import spawnparty.party.PartyAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PartyStore {

	public static final String FILE_NAME = "spawnparty-parties.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private PartyStore() {
	}

	public record StoreData(List<Party> parties, List<UUID> unlockedPlayers) {
	}

	public static StoreData load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		if (!Files.exists(file)) {
			StoreData empty = new StoreData(new ArrayList<>(), new ArrayList<>());
			save(configDir, empty.parties(), empty.unlockedPlayers());
			return empty;
		}
		try {
			String json = Files.readString(file);
			JsonObject root = GSON.fromJson(json, JsonObject.class);
			List<Party> parties = new ArrayList<>();
			List<UUID> unlocked = new ArrayList<>();
			if (root == null) {
				return new StoreData(parties, unlocked);
			}
			if (root.has("parties")) {
				for (JsonElement element : root.getAsJsonArray("parties")) {
					Party party = fromJson(element.getAsJsonObject());
					if (party != null) {
						parties.add(party);
					}
				}
			}
			if (root.has("unlockedPlayers")) {
				for (JsonElement element : root.getAsJsonArray("unlockedPlayers")) {
					unlocked.add(UUID.fromString(element.getAsString()));
				}
			}
			return new StoreData(parties, unlocked);
		} catch (Exception e) {
			SpawnPartyMod.LOGGER.error("Failed to read {}, starting empty", FILE_NAME, e);
			return new StoreData(new ArrayList<>(), new ArrayList<>());
		}
	}

	public static void save(Path configDir, List<Party> parties, List<UUID> unlockedPlayers) {
		Path file = configDir.resolve(FILE_NAME);
		Path temp = configDir.resolve(FILE_NAME + ".tmp");
		try {
			Files.createDirectories(configDir);
			JsonObject root = new JsonObject();
			JsonArray array = new JsonArray();
			for (Party party : parties) {
				array.add(toJson(party));
			}
			root.add("parties", array);
			JsonArray unlocked = new JsonArray();
			for (UUID id : unlockedPlayers) {
				unlocked.add(id.toString());
			}
			root.add("unlockedPlayers", unlocked);
			Files.writeString(temp, GSON.toJson(root));
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			SpawnPartyMod.LOGGER.error("Failed to write {}", FILE_NAME, e);
			try {
				Files.deleteIfExists(temp);
			} catch (IOException ignored) {
			}
		}
	}

	private static JsonObject toJson(Party party) {
		JsonObject obj = new JsonObject();
		obj.addProperty("id", party.id.toString());
		obj.addProperty("name", party.name);
		obj.addProperty("tag", party.tag);
		obj.addProperty("color", party.color);
		obj.addProperty("access", party.access.name());
		obj.addProperty("passwordHash", party.passwordHash == null ? "" : party.passwordHash);
		obj.addProperty("passwordSalt", party.passwordSalt == null ? "" : party.passwordSalt);
		obj.addProperty("leader", party.leader.toString());
		JsonArray members = new JsonArray();
		for (UUID member : party.members) {
			members.add(member.toString());
		}
		obj.add("members", members);
		JsonObject spawn = new JsonObject();
		spawn.addProperty("dim", party.dimension.identifier().toString());
		spawn.addProperty("x", party.spawn.getX());
		spawn.addProperty("y", party.spawn.getY());
		spawn.addProperty("z", party.spawn.getZ());
		spawn.addProperty("yaw", party.yaw);
		spawn.addProperty("pitch", party.pitch);
		obj.add("spawn", spawn);
		JsonObject extras = new JsonObject();
		if (party.setupExtras != null) {
			for (var entry : party.setupExtras.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null) {
					extras.addProperty(entry.getKey(), entry.getValue());
				}
			}
		}
		obj.add("setupExtras", extras);
		obj.addProperty("setupLocked", party.setupLocked);
		return obj;
	}

	private static Party fromJson(JsonObject obj) {
		try {
			Party party = new Party();
			party.id = UUID.fromString(obj.get("id").getAsString());
			party.name = obj.get("name").getAsString();
			party.tag = obj.has("tag") ? obj.get("tag").getAsString() : party.name;
			party.color = obj.has("color") ? obj.get("color").getAsString() : "gold";
			party.access = PartyAccess.fromString(obj.has("access") ? obj.get("access").getAsString() : "OPEN");
			party.passwordHash = obj.has("passwordHash") ? obj.get("passwordHash").getAsString() : "";
			party.passwordSalt = obj.has("passwordSalt") ? obj.get("passwordSalt").getAsString() : "";
			party.leader = UUID.fromString(obj.get("leader").getAsString());
			party.members.clear();
			if (obj.has("members")) {
				for (JsonElement member : obj.getAsJsonArray("members")) {
					party.members.add(UUID.fromString(member.getAsString()));
				}
			}
			if (!party.members.contains(party.leader)) {
				party.members.add(0, party.leader);
			}
			JsonObject spawn = obj.getAsJsonObject("spawn");
			Identifier dimId = Identifier.parse(spawn.get("dim").getAsString());
			party.dimension = ResourceKey.create(Registries.DIMENSION, dimId);
			party.spawn = new BlockPos(spawn.get("x").getAsInt(), spawn.get("y").getAsInt(), spawn.get("z").getAsInt());
			party.yaw = spawn.has("yaw") ? spawn.get("yaw").getAsFloat() : 0.0F;
			party.pitch = spawn.has("pitch") ? spawn.get("pitch").getAsFloat() : 0.0F;
			if (obj.has("setupExtras") && obj.get("setupExtras").isJsonObject()) {
				JsonObject extras = obj.getAsJsonObject("setupExtras");
				for (var entry : extras.entrySet()) {
					if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
						party.setupExtras.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
			}
			if (obj.has("setupLocked")) {
				party.setupLocked = obj.get("setupLocked").getAsBoolean();
			} else if (party.spawn != null && !party.spawn.equals(net.minecraft.core.BlockPos.ZERO)) {
				party.setupLocked = true;
			}
			return party;
		} catch (Exception e) {
			SpawnPartyMod.LOGGER.warn("Skipping malformed party entry", e);
			return null;
		}
	}
}
