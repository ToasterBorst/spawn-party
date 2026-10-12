package spawnparty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class SpawnPartyConfig {

	private static final String FILE_NAME = "spawnparty.properties";
	private static final Set<String> PREFIX_MODES = Set.of("tag", "name");

	public static SpawnPartyConfig INSTANCE = new SpawnPartyConfig();

	/** Minimum blocks between different parties' spawn origins. */
	public int partySpawnDistance = 3000;
	/** Optional biome id (e.g. minecraft:plains). Empty = no biome filter. */
	public String spawnBiome = "";
	/**
	 * Prefer villages per spawn matrix:
	 * biome set + true  = village in that biome, else closest village to that biome;
	 * biome set + false = safe surface in that biome;
	 * biome empty + true  = closest village to world spawn;
	 * biome empty + false = safe spot searching outward from world spawn.
	 */
	public boolean preferVillage = true;
	/** 0 = unlimited; >=1 = hard cap (1 = distributed solo spawning). */
	public int maxPartySize = 0;
	public boolean partyChatEnabled = true;
	public boolean globalChatPrefix = true;
	/** tag or name */
	public String chatPrefixMode = "tag";
	public boolean tabListPrefix = true;
	/** Spectator look toward +X (sunrise) while held at world spawn. */
	public boolean holdingYawTowardSunrise = true;
	/** Chat prefixes that send the rest of the line to party chat (e.g. !p hello). */
	public List<String> partyChatPrefixes = new ArrayList<>(List.of("!p"));

	public static SpawnPartyConfig load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		if (Files.exists(file)) {
			SpawnPartyConfig config = loadPropertiesFile(file);
			if (config != null) {
				return config;
			}
		}
		SpawnPartyConfig config = new SpawnPartyConfig();
		config.save(configDir);
		return config;
	}

	private static SpawnPartyConfig loadPropertiesFile(Path file) {
		try {
			Properties props = new Properties();
			try (var reader = Files.newBufferedReader(file)) {
				props.load(reader);
			}
			SpawnPartyConfig config = new SpawnPartyConfig();
			config.readProperties(props);
			return config;
		} catch (IOException e) {
			SpawnPartyMod.LOGGER.error("Failed to read config {}, using defaults", file.getFileName(), e);
			return null;
		}
	}

	private void readProperties(Properties props) {
		partySpawnDistance = getInt(props, "party_spawn_distance", partySpawnDistance);
		spawnBiome = getString(props, "spawn_biome", spawnBiome).trim();
		preferVillage = getBool(props, "prefer_village", preferVillage);
		maxPartySize = getInt(props, "max_party_size", maxPartySize);
		partyChatEnabled = getBool(props, "party_chat_enabled", partyChatEnabled);
		globalChatPrefix = getBool(props, "global_chat_prefix", globalChatPrefix);
		chatPrefixMode = normalizePrefixMode(getString(props, "chat_prefix_mode", chatPrefixMode));
		tabListPrefix = getBool(props, "tab_list_prefix", tabListPrefix);
		holdingYawTowardSunrise = getBool(props, "holding_yaw_toward_sunrise", holdingYawTowardSunrise);
		partyChatPrefixes = parsePrefixList(resolvePartyChatPrefixes(props));
		sanitize();
	}

	public void sanitize() {
		partySpawnDistance = Math.max(16, Math.min(100_000, partySpawnDistance));
		maxPartySize = Math.max(0, Math.min(1000, maxPartySize));
		chatPrefixMode = normalizePrefixMode(chatPrefixMode);
		if (spawnBiome == null) {
			spawnBiome = "";
		} else {
			spawnBiome = spawnBiome.trim();
		}
		partyChatPrefixes = sanitizePrefixes(partyChatPrefixes);
	}

	public boolean isPartyFull(int memberCount) {
		return maxPartySize > 0 && memberCount >= maxPartySize;
	}

	public static String normalizePrefixMode(String mode) {
		if (mode == null) return "tag";
		String normalized = mode.trim().toLowerCase(Locale.ROOT);
		return PREFIX_MODES.contains(normalized) ? normalized : "tag";
	}

	public boolean useTagPrefix() {
		return "tag".equals(chatPrefixMode);
	}

	public void save(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		sanitize();
		try {
			Files.createDirectories(configDir);
			String content = """
					# Spawn Party Configuration
					#
					# Live file: config/spawnparty.properties
					# Party membership and spawn origins live per-world in:
					#   saves/<world>/data/spawnparty/spawnparty-parties.json
					# Clients do not need this mod installed.

					# --- Party Spawn Distance ---

					# Minimum blocks between DIFFERENT parties' spawn origins.
					# Default 3000 keeps parties spread out on multiplayer maps; lower to 1000 for denser worlds.
					party_spawn_distance=%s

					# --- Party Spawn Location ---
					#
					# Spawn matrix (prefer_village + spawn_biome):
					#   biome set  + prefer_village=true  -> village IN that biome; else closest village TO that biome
					#   biome set  + prefer_village=false -> safe surface in that biome (no village required)
					#   biome empty + prefer_village=true  -> closest village to world spawn / search origin
					#   biome empty + prefer_village=false -> safe spot searching outward from world spawn
					# Always enforce party_spawn_distance against existing party origins.
					# Ocean/water-surface origins are allowed (e.g. Waterworld raft starts).

					# Optional biome id filter, e.g. minecraft:plains. Leave empty for any biome.
					spawn_biome=%s

					# Prefer villages according to the matrix above.
					prefer_village=%s

					# Max members per party. 0 = unlimited. 1 = dispersed solo spawning (no menu/prefixes;
					# each player auto-gets a spaced origin on first join).
					max_party_size=%s

					# --- Chat ---

					# Allow party chat via party_chat_prefix (e.g. !p hello).
					party_chat_enabled=%s

					# Prefix party tag or name on global chat messages.
					global_chat_prefix=%s

					# Which label to use for chat/tab prefixes: tag or name
					chat_prefix_mode=%s

					# Prefix party tag or name in the tab player list.
					tab_list_prefix=%s

					# Shorthand to speak in party chat. Example: !p this goes to your party only.
					# Comma-separated if you want more than one (e.g. !p,!party). Empty = off.
					party_chat_prefix=%s

					# --- First-join Holding ---

					# While held in adventure above build height (looking straight down), yaw toward +X (sunrise).
					holding_yaw_toward_sunrise=%s
					""".formatted(
					partySpawnDistance,
					spawnBiome,
					preferVillage,
					maxPartySize,
					partyChatEnabled,
					globalChatPrefix,
					chatPrefixMode,
					tabListPrefix,
					String.join(",", partyChatPrefixes),
					holdingYawTowardSunrise
			);			
			Files.writeString(file, content);
		} catch (IOException e) {
			SpawnPartyMod.LOGGER.error("Failed to write config {}", file.getFileName(), e);
		}
	}

	/**
	 * Prefer {@code party_chat_prefix}. Fall back to legacy {@code party_chat_triggers}
	 * so existing configs keep working after the channel-toggle removal.
	 */
	private static String resolvePartyChatPrefixes(Properties props) {
		if (props.containsKey("party_chat_prefix")) {
			return getString(props, "party_chat_prefix", "!p");
		}
		String legacyTriggers = props.getProperty("party_chat_triggers");
		if (legacyTriggers != null && !legacyTriggers.isBlank()) {
			return legacyTriggers;
		}
		return "!p";
	}

	private static List<String> parsePrefixList(String raw) {
		List<String> out = new ArrayList<>();
		if (raw == null || raw.isBlank()) return out;
		for (String part : raw.split(",")) {
			String s = part.trim();
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	private static List<String> sanitizePrefixes(List<String> input) {
		List<String> out = new ArrayList<>();
		for (String s : input) {
			if (s == null) continue;
			String t = s.trim();
			if (t.isEmpty() || out.contains(t)) continue;
			// Avoid swallowing normal chat: require a non-letter lead-in (e.g. !p, #p).
			char first = t.charAt(0);
			if (Character.isLetterOrDigit(first)) continue;
			out.add(t);
		}
		return out;
	}
	private static String getString(Properties props, String key, String def) {
		String value = props.getProperty(key);
		return value != null ? value : def;
	}

	private static boolean getBool(Properties props, String key, boolean def) {
		String value = props.getProperty(key);
		if (value == null) return def;
		return Boolean.parseBoolean(value.trim());
	}

	private static int getInt(Properties props, String key, int def) {
		String value = props.getProperty(key);
		if (value == null) return def;
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
