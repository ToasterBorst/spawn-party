package spawnparty.gui;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;

import java.util.function.Consumer;

/**
 * Virtual 9x3 chest menu. Clicks on top inventory slots invoke {@code onClick}; takes are cancelled.
 */
public final class PartyChestMenu extends AbstractContainerMenu {

	public static final int SIZE = 27;

	private final SimpleContainer container;
	private final Consumer<Integer> onClick;
	private final boolean allowClose;

	public PartyChestMenu(int syncId, net.minecraft.world.entity.player.Inventory playerInv, Consumer<Integer> onClick, boolean allowClose) {
		super(MenuType.GENERIC_9x3, syncId);
		this.container = new SimpleContainer(SIZE);
		this.onClick = onClick;
		this.allowClose = allowClose;

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int index = col + row * 9;
				this.addSlot(new Slot(container, index, 8 + col * 18, 18 + row * 18) {
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
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18) {
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
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142) {
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
	}

	public SimpleContainer container() {
		return container;
	}

	public void setSlot(int index, ItemStack stack) {
		container.setItem(index, stack);
	}

	public void fill(ItemStack filler) {
		for (int i = 0; i < SIZE; i++) {
			container.setItem(i, filler.copy());
		}
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			onClick.accept(slotId);
			return;
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
