package spawnparty;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spawnparty.chat.PartyChatService;
import spawnparty.chat.PrefixService;
import spawnparty.command.SpawnPartyCommands;
import spawnparty.party.PartyManager;
import spawnparty.spawn.FirstJoinHandler;

public final class SpawnPartyMod implements ModInitializer {

	public static final String MOD_ID = "spawnparty";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		var configDir = FabricLoader.getInstance().getConfigDir();
		SpawnPartyConfig.INSTANCE = SpawnPartyConfig.load(configDir);
		SpawnPartyConfig.INSTANCE.save(configDir);

		PartyManager.init(configDir);
		FirstJoinHandler.register();
		PartyChatService.register();
		PrefixService.register();
		SpawnPartyCommands.register();

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PartyManager.save());

		LOGGER.info("Spawn Party ready — parties share an origin, clients need not install the mod.");
	}
}
