package spawnparty.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class PasswordUtil {

	private static final SecureRandom RANDOM = new SecureRandom();

	private PasswordUtil() {
	}

	public static String newSalt() {
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		return HexFormat.of().formatHex(salt);
	}

	public static String hash(String password, String salt) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(salt.getBytes(StandardCharsets.UTF_8));
			byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	public static boolean matches(String password, String salt, String expectedHash) {
		if (password == null || salt == null || expectedHash == null || expectedHash.isBlank()) {
			return false;
		}
		return hash(password, salt).equalsIgnoreCase(expectedHash);
	}
}
