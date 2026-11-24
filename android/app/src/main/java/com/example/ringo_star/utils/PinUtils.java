package com.example.ringo_star.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PIN hashes are salted and expensive to guess; legacy SHA-256 PINs remain readable. */
public final class PinUtils {
    private static final int ITERATIONS = 150_000;
    private static final String PREFIX = "pbkdf2-sha256$";
    private PinUtils() {}

    public static String hashPin(String pin, byte[] salt) throws Exception {
        if (salt == null || salt.length < 16) throw new IllegalArgumentException("Invalid PIN salt");
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return PREFIX + ITERATIONS + "$" + Base64.getEncoder().encodeToString(hash);
        } finally { spec.clearPassword(); }
    }

    public static boolean verifyPin(String pin, byte[] salt, String stored) throws Exception {
        if (stored == null || salt == null) return false;
        byte[] expected;
        byte[] actual;
        if (stored.startsWith(PREFIX)) {
            String[] fields = stored.split("\\$", -1);
            if (fields.length != 3) return false;
            int iterations = Integer.parseInt(fields[1]);
            if (iterations < 100_000 || iterations > 1_000_000) return false;
            expected = Base64.getDecoder().decode(fields[2]);
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, expected.length * 8);
            try { actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded(); }
            finally { spec.clearPassword(); }
        } else {
            // One-time compatibility with the historical SHA-256 + 16-byte salt format.
            expected = Base64.getDecoder().decode(stored);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(salt);
            actual = sha.digest(pin.getBytes(StandardCharsets.UTF_8));
        }
        return MessageDigest.isEqual(actual, expected);
    }

    public static boolean needsUpgrade(String stored) { return stored != null && !stored.startsWith(PREFIX); }

    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
