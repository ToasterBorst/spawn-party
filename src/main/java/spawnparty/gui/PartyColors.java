package spawnparty.gui;

/**
 * Chat-formatting colors plus brown (not a vanilla ChatFormatting name).
 */
public final class PartyColors {

	/** Order: two rows of 8, then brown in slot 16. */
	public static final String[] ALL = {
			"black", "dark_blue", "dark_green", "dark_aqua",
			"dark_red", "dark_purple", "gold", "gray",
			"dark_gray", "blue", "green", "aqua",
			"red", "light_purple", "yellow", "white", "brown"
	};

	private PartyColors() {
	}

	public static void fillPalette(PartyChestMenu menu) {
		for (int i = 0; i < ALL.length; i++) {
			String color = ALL[i];
			menu.setSlot(i, GuiItems.button(
					GuiItems.dyeForColor(color),
					color,
					color,
					"chat color"
			));
		}
	}

	public static String fromPaletteSlot(int slotId) {
		if (slotId < 0 || slotId >= ALL.length) {
			return null;
		}
		return ALL[slotId];
	}
}
