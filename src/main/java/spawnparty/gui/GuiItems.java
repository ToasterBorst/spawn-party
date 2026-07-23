package spawnparty.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public final class GuiItems {

	private GuiItems() {
	}

	public static ItemStack button(Item item, String name, String... loreLines) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		if (loreLines.length > 0) {
			List<Component> lore = new ArrayList<>();
			for (String line : loreLines) {
				lore.add(Component.literal(line));
			}
			stack.set(DataComponents.LORE, new ItemLore(lore));
		}
		return stack;
	}

	public static ItemStack filler() {
		return button(Items.STAINED_GLASS_PANE.gray(), " ");
	}

	public static Item dyeForColor(String color) {
		return switch (color == null ? "gold" : color.toLowerCase()) {
			case "red", "dark_red" -> Items.DYE.red();
			case "blue", "dark_blue" -> Items.DYE.blue();
			case "green", "dark_green" -> Items.DYE.green();
			case "yellow" -> Items.DYE.yellow();
			case "aqua", "cyan" -> Items.DYE.cyan();
			case "light_purple", "pink", "magenta" -> Items.DYE.magenta();
			case "white" -> Items.DYE.white();
			case "gray" -> Items.DYE.gray();
			case "gold", "orange" -> Items.DYE.orange();
			default -> Items.DYE.orange();
		};
	}
}
