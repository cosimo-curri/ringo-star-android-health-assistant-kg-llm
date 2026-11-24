package com.example.ringo_star.llm;

import com.example.ringo_star.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Optional, build-pinned model hashes: downloaded bytes are checked before activation. */
public final class ModelArtifactVerifier {
    private ModelArtifactVerifier() {}

    public static String expectedSha256(String model) {
        switch (model) {
            case "gemma2b": return BuildConfig.MODEL_SHA256_GEMMA2B;
            case "falcon_rw1b": return BuildConfig.MODEL_SHA256_FALCON_RW1B;
            case "stable_lm3b": return BuildConfig.MODEL_SHA256_STABLE_LM3B;
            case "phi2": return BuildConfig.MODEL_SHA256_PHI2;
            default: throw new IllegalArgumentException("Unknown model: " + model);
        }
    }

    public static boolean matches(File file, String expected) throws IOException {
        if (file == null || !file.isFile() || file.length() == 0) return false;
        // A missing pin allows installation, but cannot provide cryptographic verification.
        if (expected == null || expected.isEmpty()) return true;
        if (!expected.matches("(?i)[0-9a-f]{64}")) throw new IOException("Invalid pinned SHA-256");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            byte[] expectedBytes = new byte[32];
            for (int i = 0; i < expectedBytes.length; i++) {
                expectedBytes[i] = (byte) Integer.parseInt(expected.substring(2 * i, 2 * i + 2), 16);
            }
            return MessageDigest.isEqual(digest.digest(), expectedBytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
    }
}
