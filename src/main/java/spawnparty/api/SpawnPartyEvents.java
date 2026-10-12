package spawnparty.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class SpawnPartyEvents {

	private SpawnPartyEvents() {
	}

	/**
	 * Fired at the end of first-origin placement (first-join hold exit).
	 * Waterworld and other kit mods should listen here.
	 */
	public static final Event<AfterFirstOriginPlacement> AFTER_FIRST_ORIGIN_PLACEMENT =
			EventFactory.createArrayBacked(AfterFirstOriginPlacement.class, listeners ->
					(player, party, level, origin, yaw, pitch) -> {
						for (AfterFirstOriginPlacement listener : listeners) {
							listener.onFirstOriginPlacement(player, party, level, origin, yaw, pitch);
						}
					});
}
