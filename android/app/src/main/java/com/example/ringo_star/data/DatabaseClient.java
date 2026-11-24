package com.example.ringo_star.data;

import android.content.Context;

import androidx.room.Room;

import com.example.ringo_star.data.database.AppDatabase;
import com.example.ringo_star.data.entity.User;
import com.example.ringo_star.security.SecureFiles;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/** Encrypted Room database with a one-time, failure-safe import of pre-security installs. */
public final class DatabaseClient {
    private static final String DATABASE = "ringo_health_encrypted.db";
    private static final String LEGACY = "database";
    private static DatabaseClient instance;
    private final AppDatabase database;

    private DatabaseClient(Context context) {
        Context app = context.getApplicationContext();
        File encryptedPath = app.getDatabasePath(DATABASE);
        byte[] password;
        try {
            password = SecureFiles.databasePassword(app, encryptedPath.exists());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to unlock protected health data", error);
        }
        try {
            System.loadLibrary("sqlcipher");
            database = Room.databaseBuilder(app, AppDatabase.class, DATABASE)
                    .openHelperFactory(new SupportOpenHelperFactory(Arrays.copyOf(password, password.length)))
                    .build();
            migrateLegacyDatabase(app);
        } finally {
            Arrays.fill(password, (byte) 0);
        }
    }

    private void migrateLegacyDatabase(Context context) {
        if (!context.getDatabasePath(LEGACY).exists()) return;
        AppDatabase legacy = Room.databaseBuilder(context, AppDatabase.class, LEGACY).build();
        try {
            User prior = legacy.userDAO().getUser();
            User migrated = database.userDAO().getUser();
            if (prior != null && migrated == null) {
                database.userDAO().insert(prior);
                migrated = database.userDAO().getUser();
                if (migrated == null) throw new IllegalStateException("Legacy profile migration failed");
            }
        } finally {
            legacy.close();
        }
        if (!context.deleteDatabase(LEGACY)) {
            // Data was imported, but do not silently ignore an unencrypted remnant.
            throw new IllegalStateException("Legacy plaintext database could not be removed");
        }
    }

    public static synchronized DatabaseClient getInstance(Context context) {
        if (instance == null) instance = new DatabaseClient(context);
        return instance;
    }

    /** Close and remove encrypted SQLite files before discarding the wrapped key. */
    public static synchronized void destroy(Context context) throws IOException {
        if (instance != null) {
            instance.database.close();
            instance = null;
        }
        Context app = context.getApplicationContext();
        File file = app.getDatabasePath(DATABASE);
        if (file.exists() && !app.deleteDatabase(DATABASE)) {
            throw new IOException("Unable to delete encrypted database");
        }
        if (!SecureFiles.delete(app, "database-key")) {
            throw new IOException("Unable to delete wrapped database key");
        }
    }

    public AppDatabase getDatabase() { return database; }
}
