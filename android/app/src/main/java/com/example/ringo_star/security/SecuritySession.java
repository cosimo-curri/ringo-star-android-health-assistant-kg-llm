package com.example.ringo_star.security;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

/** In-memory authentication state; cleared after the application leaves the foreground. */
public final class SecuritySession extends Application implements Application.ActivityLifecycleCallbacks {
    private static volatile boolean unlocked;
    private int startedActivities;
    private static volatile long externalDocumentPickerUntil;

    /** Allow a short, explicit system document-picker round trip without losing the session. */
    public static void beginDocumentExport() {
        if (unlocked) externalDocumentPickerUntil = SystemClock.elapsedRealtime() + 120_000L;
    }
    public static void endDocumentExport() { externalDocumentPickerUntil = 0; }
    public static boolean documentExportAllowed() {
        return isUnlocked() && externalDocumentPickerUntil > SystemClock.elapsedRealtime();
    }

    @Override public void onCreate() {
        super.onCreate();
        unlocked = false;
        registerActivityLifecycleCallbacks(this);
    }

    public static void unlock() { unlocked = true; }
    public static void lock() { unlocked = false; externalDocumentPickerUntil = 0; }
    public static boolean isUnlocked() {
        if (externalDocumentPickerUntil != 0 && SystemClock.elapsedRealtime() > externalDocumentPickerUntil) lock();
        return unlocked;
    }

    @Override public void onActivityStarted(Activity activity) { startedActivities++; }
    @Override public void onActivityStopped(Activity activity) {
        if (startedActivities > 0) startedActivities--;
        if (startedActivities == 0 && externalDocumentPickerUntil == 0) lock();
    }
    @Override public void onActivityCreated(Activity a, Bundle saved) {}
    @Override public void onActivityResumed(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle out) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
