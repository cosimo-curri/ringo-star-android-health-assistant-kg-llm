package com.example.ringo_star.security;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ringo_star.PinActivity;

/** Blocks screenshots and requires a fresh PIN after backgrounding the app. */
public abstract class SecureActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override protected void onStart() {
        super.onStart();
        if (requiresUnlock() && !SecuritySession.isUnlocked()) {
            Intent pin = new Intent(this, PinActivity.class);
            pin.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(pin);
            finish();
        }
    }

    protected boolean requiresUnlock() { return true; }
}
