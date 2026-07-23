package spawnparty.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import spawnparty.chat.PartyChatService;
import spawnparty.gui.OpAdminMenu;
import spawnparty.gui.PartyMenus;
import spawnparty.spawn.HoldingCamera;

public final class SpawnPartyCommands {

	private SpawnPartyCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.literal("spawnparty")
				.executes(ctx -> openMenu(ctx.getSource()))
				.then(Commands.literal("admin")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> openAdmin(ctx.getSource())))
				.then(Commands.literal("chat")
						.executes(ctx -> toggleChat(ctx.getSource())));

		dispatcher.register(root);
		dispatcher.register(Commands.literal("sp").executes(ctx -> openMenu(ctx.getSource())));
		dispatcher.register(Commands.literal("pchat").executes(ctx -> toggleChat(ctx.getSource())));
	}

	private static int openMenu(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Players only."));
			return 0;
		}
		PartyMenus.openMain(player);
		return 1;
	}

	private static int openAdmin(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Players only."));
			return 0;
		}
		OpAdminMenu.open(player);
		return 1;
	}

	private static int toggleChat(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Players only."));
			return 0;
		}
		if (HoldingCamera.isHolding(player.getUUID())) {
			player.sendSystemMessage(Component.literal("Finish joining a party first."));
			return 0;
		}
		PartyChatService.toggle(player);
		return 1;
	}
}
