package com.example.ringo_star.utils;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import static org.junit.Assert.*;

public class PinUtilsTest {
    @Test public void modernPinHashAndVerification() throws Exception {
        byte[] salt = PinUtils.generateSalt();
        String stored = PinUtils.hashPin("1234", salt);
        assertTrue(stored.startsWith("pbkdf2-sha256$"));
        assertTrue(PinUtils.verifyPin("1234", salt, stored));
        assertFalse(PinUtils.verifyPin("1235", salt, stored));
        assertFalse(PinUtils.needsUpgrade(stored));
    }

    @Test public void preexistingSha256HashCanBeVerifiedAndUpgraded() throws Exception {
        byte[] salt = new byte[16];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) i;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        String old = Base64.getEncoder().encodeToString(
                digest.digest("2468".getBytes(StandardCharsets.UTF_8)));
        assertTrue(PinUtils.verifyPin("2468", salt, old));
        assertFalse(PinUtils.verifyPin("0000", salt, old));
        assertTrue(PinUtils.needsUpgrade(old));
    }

    @Test public void saltsAreNotConstant() {
        assertFalse(java.util.Arrays.equals(PinUtils.generateSalt(), PinUtils.generateSalt()));
    }
}
