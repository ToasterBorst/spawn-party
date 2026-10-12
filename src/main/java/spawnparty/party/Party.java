package spawnparty.party;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Party {

	public UUID id;
	public String name;
	public String tag;
	public String color = "gold";
	public PartyAccess access = PartyAccess.OPEN;
	public String passwordHash = "";
	public String passwordSalt = "";
	public UUID leader;
	public final List<UUID> members = new ArrayList<>();
	public ResourceKey<Level> dimension = Level.OVERWORLD;
	public BlockPos spawn = BlockPos.ZERO;
	public float yaw = 0.0F;
	public float pitch = 0.0F;
	public final Map<String, String> setupExtras = new LinkedHashMap<>();
	public boolean setupLocked = false;

	public Party() {
	}

	public Party(UUID id, String name, String tag, UUID leader) {
		this.id = id;
		this.name = name;
		this.tag = tag;
		this.leader = leader;
		this.members.add(leader);
	}

	public boolean isMember(UUID playerId) {
		return members.contains(playerId);
	}

	public boolean isLeader(UUID playerId) {
		return leader != null && leader.equals(playerId);
	}

	public String displayPrefix() {
		return tag != null && !tag.isBlank() ? tag : name;
	}

	public String colorName() {
		return color == null || color.isBlank() ? "gold" : color.toLowerCase(Locale.ROOT);
	}

	public Identifier dimensionId() {
		return dimension.identifier();
	}
}
