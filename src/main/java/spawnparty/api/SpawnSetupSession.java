package spawnparty.api;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-player bag of namespaced extras collected during setup pages.
 * Copied onto the party and frozen after first origin placement.
 */
public final class SpawnSetupSession {

	private final Map<Identifier, String> data = new LinkedHashMap<>();

	public void put(Identifier key, String value) {
		if (key == null) return;
		if (value == null || value.isBlank()) {
			data.remove(key);
			return;
		}
		data.put(key, value);
	}

	public Optional<String> get(Identifier key) {
		if (key == null) return Optional.empty();
		return Optional.ofNullable(data.get(key));
	}

	public Map<String, String> toStringMap() {
		Map<String, String> out = new LinkedHashMap<>();
		data.forEach((id, value) -> out.put(id.toString(), value));
		return Collections.unmodifiableMap(out);
	}
}
