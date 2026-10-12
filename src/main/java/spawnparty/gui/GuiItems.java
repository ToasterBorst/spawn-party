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
			case "black" -> Items.DYE.black();
			case "dark_blue" -> Items.DYE.blue();
			case "dark_green" -> Items.DYE.green();
			case "dark_aqua", "cyan" -> Items.DYE.cyan();
			case "dark_red" -> Items.DYE.red();
			case "dark_purple", "purple" -> Items.DYE.purple();
			case "gold", "orange" -> Items.DYE.orange();
			case "gray" -> Items.DYE.lightGray();
			case "dark_gray" -> Items.DYE.gray();
			case "blue" -> Items.DYE.lightBlue();
			case "green", "lime" -> Items.DYE.lime();
			case "aqua" -> Items.DYE.lightBlue();
			case "red" -> Items.DYE.red();
			case "light_purple", "pink", "magenta" -> Items.DYE.magenta();
			case "yellow" -> Items.DYE.yellow();
			case "white" -> Items.DYE.white();
			case "brown" -> Items.DYE.brown();
			default -> Items.DYE.orange();
		};
	}
}
