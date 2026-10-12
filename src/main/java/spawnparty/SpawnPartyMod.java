package spawnparty;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spawnparty.chat.PartyChatService;
import spawnparty.chat.PrefixService;
import spawnparty.command.SpawnPartyCommands;
import spawnparty.party.PartyManager;
import spawnparty.spawn.FirstJoinHandler;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SpawnPartyMod implements ModInitializer {

	public static final String MOD_ID = "spawnparty";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Global settings only (not party membership). */
	public static Path configDir;

	@Override
	public void onInitialize() {
		configDir = FabricLoader.getInstance().getConfigDir();
		SpawnPartyConfig.INSTANCE = SpawnPartyConfig.load(configDir);
		SpawnPartyConfig.INSTANCE.save(configDir);

		FirstJoinHandler.register();
		PartyChatService.register();
		PrefixService.register();
		SpawnPartyCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Path worldData = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("spawnparty");
			Path legacyGlobal = configDir.resolve("spawnparty-parties.json");
			PartyManager.initForWorld(worldData, legacyGlobal);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PartyManager.save());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PartyManager.clear());

		LOGGER.info("Spawn Party ready — parties are stored per world; clients need not install the mod.");
	}
}
