package com.example.ringo_star.llm;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ModelArtifactVerifierTest {
    @Test public void acceptsMatchingSha256AndRejectsWrongHash() throws Exception {
        File file = File.createTempFile("ringo-test-model", ".bin");
        try {
            Files.write(file.toPath(), "abc".getBytes(StandardCharsets.US_ASCII));
            assertTrue(ModelArtifactVerifier.matches(file,
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
            assertFalse(ModelArtifactVerifier.matches(file,
                    "0000000000000000000000000000000000000000000000000000000000000000"));
            assertTrue(ModelArtifactVerifier.matches(file, ""));
            Files.write(file.toPath(), new byte[0]);
            assertFalse(ModelArtifactVerifier.matches(file, ""));
        } finally { file.delete(); }
    }
}
