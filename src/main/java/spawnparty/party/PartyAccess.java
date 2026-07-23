package spawnparty.party;

public enum PartyAccess {
	OPEN,
	CLOSED,
	PASSWORD;

	public static PartyAccess fromString(String value) {
		if (value == null) return OPEN;
		return switch (value.trim().toUpperCase()) {
			case "CLOSED" -> CLOSED;
			case "PASSWORD" -> PASSWORD;
			default -> OPEN;
		};
	}
}
