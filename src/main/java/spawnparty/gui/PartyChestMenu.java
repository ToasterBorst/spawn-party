package spawnparty.gui;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;

import java.util.function.Consumer;

/**
 * Virtual chest (9x3 or 9x6) + the player's real inventory/hotbar (read-only).
 * Clicks on top slots invoke {@code onClick}; takes are cancelled.
 */
public final class PartyChestMenu extends AbstractContainerMenu {

	public static final int ROWS_SINGLE = 3;
	public static final int ROWS_DOUBLE = 6;

	private final int topSize;
	private final SimpleContainer top;
	private final Consumer<Integer> onClick;
	private final boolean allowClose;

	public PartyChestMenu(int syncId, Inventory playerInv, Consumer<Integer> onClick, boolean allowClose) {
		this(syncId, playerInv, onClick, allowClose, ROWS_SINGLE);
	}

	public PartyChestMenu(int syncId, Inventory playerInv, Consumer<Integer> onClick, boolean allowClose, int rows) {
		super(rows >= ROWS_DOUBLE ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3, syncId);
		this.topSize = Math.max(ROWS_SINGLE, Math.min(ROWS_DOUBLE, rows)) * 9;
		this.top = new SimpleContainer(this.topSize);
		this.onClick = onClick;
		this.allowClose = allowClose;

		int chestRows = this.topSize / 9;
		for (int row = 0; row < chestRows; row++) {
			for (int col = 0; col < 9; col++) {
				int index = col + row * 9;
				addLockedSlot(top, index, 8 + col * 18, 18 + row * 18);
			}
		}

		int playerInvY = chestRows >= ROWS_DOUBLE ? 140 : 84;
		int hotbarY = chestRows >= ROWS_DOUBLE ? 198 : 142;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addLockedSlot(playerInv, col + (row + 1) * 9, 8 + col * 18, playerInvY + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			addLockedSlot(playerInv, col, 8 + col * 18, hotbarY);
		}
	}

	private void addLockedSlot(net.minecraft.world.Container container, int index, int x, int y) {
		this.addSlot(new Slot(container, index, x, y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public boolean mayPickup(Player player) {
				return false;
			}
		});
	}

	public int topSize() {
		return topSize;
	}

	public void setSlot(int index, ItemStack stack) {
		if (index >= 0 && index < topSize) {
			top.setItem(index, stack);
		}
	}

	public void fill(ItemStack filler) {
		for (int i = 0; i < topSize; i++) {
			top.setItem(i, filler.copy());
		}
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < topSize) {
			onClick.accept(slotId);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return false;
	}

	public boolean allowClose() {
		return allowClose;
	}
}
