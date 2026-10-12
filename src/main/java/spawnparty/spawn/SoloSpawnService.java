package spawnparty.spawn;

import spawnparty.SpawnPartyConfig;

import java.util.Locale;

/**
 * Silent auto-create helpers for {@code max_party_size=1} (dispersed solo spawning).
 * First-join placement is driven by {@link SpawnSetupController}.
 */
public final class SoloSpawnService {

	private SoloSpawnService() {
	}

	public static boolean isSoloMode() {
		return SpawnPartyConfig.INSTANCE.maxPartySize == 1;
	}

	public static String compactTag(String name) {
		String compact = name.replaceAll("[^A-Za-z0-9]", "");
		if (compact.length() >= 2) {
			return compact.substring(0, Math.min(4, compact.length())).toUpperCase(Locale.ROOT);
		}
		return "SOLO";
	}
}
