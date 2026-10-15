package spawnparty.api;

import net.minecraft.world.item.ItemStack;

/**
 * One hosted 9x6 chest page. Slots 45 and 53 are Back and Forward while a wizard
 * is active; the host overwrites those slots after the page is filled.
 */
public interface SetupPage {

	int size();

	void setSlot(int index, ItemStack stack, Runnable onClick);

	void fillFiller();
}
