package spawnparty.api;

import net.minecraft.world.item.ItemStack;

/**
 * One hosted 9x6 chest page. Bottom row (slots 45–53) is reserved for navigation
 * unless the caller writes those slots itself.
 */
public interface SetupPage {

	int size();

	void setSlot(int index, ItemStack stack, Runnable onClick);

	void fillFiller();
}
