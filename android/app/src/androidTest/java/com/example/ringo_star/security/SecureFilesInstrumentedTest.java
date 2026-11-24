package com.example.ringo_star.security;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Requires an emulator/device with Android Keystore; not a host JVM test. */
@RunWith(AndroidJUnit4.class)
public class SecureFilesInstrumentedTest {
    @Test public void encryptedReportRoundTripsAndRejectsTampering() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        byte[] original = "sensitive test report".getBytes(StandardCharsets.UTF_8);
        try {
            SecureFiles.write(app, "report.pdf", out -> out.write(original));
            File stored = new File(new File(app.getNoBackupFilesDir(), "sensitive"), "report.pdf.enc");
            assertTrue(stored.isFile());
            assertArrayEquals(original, SecureFiles.read(app, "report.pdf"));
            try (FileOutputStream mutate = new FileOutputStream(stored, true)) {
                mutate.write(0x42);
            }
            try {
                SecureFiles.read(app, "report.pdf");
                fail("Tampered ciphertext was accepted");
            } catch (java.io.IOException expected) {
                // Authentication failed as designed.
            }
        } finally {
            SecureFiles.delete(app, "report.pdf");
        }
    }
}
