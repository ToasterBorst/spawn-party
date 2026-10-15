package spawnparty.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import spawnparty.gui.GuiItems;
import spawnparty.gui.PartyChestMenu;
import spawnparty.spawn.HoldPhase;
import spawnparty.spawn.HoldingCamera;
import spawnparty.spawn.SpawnSetupController;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Hosts 9x6 chest pages for {@link SpawnSetupExtension}s. Click routing stays in
 * Spawn Party; page content is supplied by the extension.
 */
public final class SetupMenuHost {

	public static final int NAV_BACK = 45;
	public static final int NAV_PREV = 47;
	public static final int NAV_INFO = 49;
	public static final int NAV_NEXT = 51;
	public static final int NAV_CONFIRM = 53;
	public static final int CONTENT_SLOTS = 45;

	private static final ThreadLocal<Boolean> REPLACING = ThreadLocal.withInitial(() -> false);

	private SetupMenuHost() {
	}

	/** True while a chest is being replaced — skip Escape-reopen. */
	public static boolean replacingMenu() {
		return Boolean.TRUE.equals(REPLACING.get());
	}

	/** Run {@code action} without the hold mixin treating the close as Escape. */
	public static void runReplacing(Runnable action) {
		boolean previous = REPLACING.get();
		REPLACING.set(true);
		try {
			action.run();
		} finally {
			REPLACING.set(previous);
		}
	}

	public static void open(ServerPlayer player, Component title, Consumer<SetupPage> fill) {
		openFiller(player, title, fill::accept, false);
	}

	public static void open(ServerPlayer player, Component title, SetupPageFiller fill) {
		openFiller(player, title, fill, false);
	}

	/**
	 * @param statusOnly clicks are ignored (locate progress)
	 */
	public static void open(ServerPlayer player, Component title, Consumer<SetupPage> fill, boolean statusOnly) {
		openFiller(player, title, fill::accept, statusOnly);
	}

	public static void open(ServerPlayer player, Component title, SetupPageFiller fill, boolean statusOnly) {
		openFiller(player, title, fill, statusOnly);
	}

	private static void openFiller(ServerPlayer player, Component title, SetupPageFiller fill, boolean statusOnly) {
		HoldingCamera.setPhase(player.getUUID(), statusOnly ? HoldPhase.LOCATING : HoldPhase.SETUP);
		REPLACING.set(true);
		try {
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> createMenu(player, title, fill, statusOnly, id, inv), title));
		} finally {
			REPLACING.set(false);
		}
	}

	private static PartyChestMenu createMenu(ServerPlayer player, Component title, SetupPageFiller fill,
			boolean statusOnly, int id, net.minecraft.world.entity.player.Inventory inv) {
		Runnable[] clicks = new Runnable[PartyChestMenu.ROWS_DOUBLE * 9];
		PartyChestMenu menu = new PartyChestMenu(id, inv, slot -> {
			if (statusOnly && slot != NAV_BACK) return;
			if (slot >= 0 && slot < clicks.length && clicks[slot] != null) {
				clicks[slot].run();
			}
		}, false, PartyChestMenu.ROWS_DOUBLE);
		menu.fill(GuiItems.filler());
		SetupPage page = new SetupPage() {
			@Override
			public int size() {
				return menu.topSize();
			}

			@Override
			public void setSlot(int index, ItemStack stack, Runnable onClick) {
				menu.setSlot(index, stack);
				if (index >= 0 && index < clicks.length) {
					clicks[index] = onClick;
				}
			}

			@Override
			public void fillFiller() {
				menu.fill(GuiItems.filler());
				Arrays.fill(clicks, null);
			}
		};
		fill.fill(page);
		SpawnSetupController.stampNav(player, page, statusOnly);
		SpawnSetupController.rememberReopen(player.getUUID(), () -> open(player, title, fill, statusOnly));
		return menu;
	}
}
