package com.example.ringo_star.security;

import android.content.Context;
import android.content.SharedPreferences;

/** Persisted throttling for a four-digit PIN; designed to survive process restarts. */
public final class PinAttemptLimiter {
    private static final String STORE = "pin_attempt_guard";
    private PinAttemptLimiter() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public static synchronized long remainingMillis(Context context) {
        return Math.max(0, prefs(context).getLong("locked_until", 0) - System.currentTimeMillis());
    }

    public static synchronized void failed(Context context) {
        SharedPreferences p = prefs(context);
        int failures = Math.min(20, p.getInt("failures", 0) + 1);
        long duration = 0;
        if (failures >= 5) {
            duration = Math.min(15 * 60_000L, 30_000L << Math.min(failures - 5, 5));
        }
        p.edit().putInt("failures", failures)
                .putLong("locked_until", duration == 0 ? 0 : System.currentTimeMillis() + duration)
                .commit();
    }

    public static synchronized void reset(Context context) {
        prefs(context).edit().clear().commit();
    }
}
