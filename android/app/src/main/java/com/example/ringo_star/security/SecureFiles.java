package com.example.ringo_star.security;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Authenticated encryption for health-related files and the wrapped database passphrase. */
public final class SecureFiles {
    private static final String KEY_ALIAS = "ringo_star_health_data_v1";
    private static final byte[] MAGIC = { 'R', 'S', 'E', '1' };
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private SecureFiles() {}

    public interface OutputWriter {
        void write(OutputStream output) throws IOException;
    }

    private static synchronized SecretKey key() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    private static File privateDirectory(Context context) throws IOException {
        File directory = new File(context.getNoBackupFilesDir(), "sensitive");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to prepare encrypted storage");
        }
        return directory;
    }

    private static File file(Context context, String name) throws IOException {
        // Never allow callers to turn a file name into an arbitrary filesystem path.
        if (!"kg.ttl".equals(name) && !"report.pdf".equals(name)
                && !"database-key".equals(name)) {
            throw new IOException("Unknown secure file name");
        }
        return new File(privateDirectory(context), name + ".enc");
    }

    public static boolean exists(Context context, String name) {
        try { return file(context, name).isFile(); }
        catch (IOException failure) { throw new IllegalStateException("Cannot access secure storage", failure); }
    }

    public static void write(Context context, String name, OutputWriter writer) throws IOException {
        File destination = file(context, name);
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        boolean committed = false;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            cipher.updateAAD(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            if (iv.length != IV_BYTES) throw new IOException("Unexpected encryption IV size");
            try (FileOutputStream fileOutput = new FileOutputStream(temporary)) {
                fileOutput.write(MAGIC);
                fileOutput.write(iv);
                try (CipherOutputStream encrypted = new CipherOutputStream(fileOutput, cipher)) {
                    writer.write(encrypted);
                }
            }
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            committed = true;
        } catch (GeneralSecurityException failure) {
            throw new IOException("Encryption unavailable", failure);
        } finally {
            if (!committed && temporary.exists()) temporary.delete();
        }
    }

    /** Authenticates the entire file before exposing a single plaintext byte. */
    public static byte[] read(Context context, String name) throws IOException {
        File source = file(context, name);
        if (!source.isFile()) throw new IOException("Encrypted file not present");
        try (InputStream input = new FileInputStream(source)) {
            ByteArrayOutputStream ciphertext = new ByteArrayOutputStream();
            byte[] header = new byte[MAGIC.length + IV_BYTES];
            int offset = 0;
            while (offset < header.length) {
                int n = input.read(header, offset, header.length - offset);
                if (n < 0) throw new IOException("Truncated encrypted file");
                offset += n;
            }
            if (!Arrays.equals(MAGIC, Arrays.copyOf(header, MAGIC.length)))
                throw new IOException("Invalid encrypted file header");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) ciphertext.write(buffer, 0, length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(GCM_TAG_BITS, header, MAGIC.length, IV_BYTES));
            cipher.updateAAD(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext.toByteArray());
        } catch (GeneralSecurityException failure) {
            throw new IOException("Encrypted file could not be authenticated", failure);
        }
    }

    public static boolean delete(Context context, String name) throws IOException {
        File stored = file(context, name);
        return !stored.exists() || stored.delete();
    }

    /** The SQLCipher password is random; only its AES-GCM-wrapped form is persisted. */
    public static byte[] databasePassword(Context context, boolean databaseAlreadyExists) throws IOException {
        if (exists(context, "database-key")) return read(context, "database-key");
        if (databaseAlreadyExists) {
            // Never silently create a new key for an existing encrypted database.
            throw new IOException("Database key missing; cannot open existing health data");
        }
        byte[] passphrase = new byte[32];
        new SecureRandom().nextBytes(passphrase);
        try {
            write(context, "database-key", output -> output.write(passphrase));
            return passphrase;
        } catch (IOException failure) {
            Arrays.fill(passphrase, (byte) 0);
            throw failure;
        }
    }
    /** Irreversibly invalidate all on-device encrypted files after a full profile wipe. */
    public static synchronized void destroyKeystoreKey() throws IOException {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
        } catch (GeneralSecurityException failure) {
            throw new IOException("Unable to remove data-encryption key", failure);
        }
    }

}
