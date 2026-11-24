package com.example.ringo_star.fragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.ringo_star.R;
import com.example.ringo_star.UserInfoActivity;
import com.example.ringo_star.data.DatabaseClient;
import com.example.ringo_star.data.dao.UserDAO;
import com.example.ringo_star.security.SecureFiles;
import com.example.ringo_star.security.PinAttemptLimiter;
import com.example.ringo_star.security.SecuritySession;

import java.io.File;

public class SettingsFragment extends Fragment {
    Button btnUpdateProfile;
    Button btnCloseApp;
    Button btnDeleteAll;

    public SettingsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnUpdateProfile = view.findViewById(R.id.btnUpdateProfile);
        btnCloseApp = view.findViewById(R.id.btnCloseApp);
        btnDeleteAll = view.findViewById(R.id.btnDeleteAll);

        btnUpdateProfile.setBackgroundResource(R.drawable.button_settings);
        btnUpdateProfile.setBackgroundTintList(null);

        btnCloseApp.setBackgroundResource(R.drawable.button_settings);
        btnCloseApp.setBackgroundTintList(null);

        btnDeleteAll.setBackgroundResource(R.drawable.button_settings_alert);
        btnDeleteAll.setBackgroundTintList(null);

        btnUpdateProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(requireContext(), UserInfoActivity.class));
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                requireActivity().finish();
            }
        });

        btnCloseApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requireActivity().finishAffinity();
                System.exit(0);
            }
        });

        btnDeleteAll.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Are you sure?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    btnUpdateProfile.setEnabled(false);
                    btnCloseApp.setEnabled(false);
                    btnDeleteAll.setEnabled(false);
                    Toast.makeText(requireContext(), "Please wait...", Toast.LENGTH_SHORT).show();
                    android.content.Context appContext = requireContext().getApplicationContext();
                    new Thread(() -> {
                        try {
                            DatabaseClient.getInstance(appContext).getDatabase().userDAO().delete();
                            if (!SecureFiles.delete(appContext, "kg.ttl")
                                    || !SecureFiles.delete(appContext, "report.pdf")) {
                                throw new IllegalStateException("Unable to delete encrypted health files");
                            }
                            File legacyGraph = new File(appContext.getFilesDir(), "kg.ttl");
                            if (legacyGraph.exists() && !legacyGraph.delete()) {
                                throw new IllegalStateException("Unable to delete legacy health graph");
                            }
                            DatabaseClient.destroy(appContext);
                            SecureFiles.destroyKeystoreKey();
                            PinAttemptLimiter.reset(appContext);
                            SecuritySession.lock();
                            requireActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Intent onboarding = new Intent(requireContext(), UserInfoActivity.class);
                                onboarding.putExtra("onboarding", true);
                                startActivity(onboarding);
                                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                                requireActivity().finish();
                            });
                        } catch (Exception error) {
                            android.util.Log.e("RingoStar", "Unable to delete profile", error);
                            if (isAdded()) requireActivity().runOnUiThread(() -> {
                                btnUpdateProfile.setEnabled(true);
                                btnCloseApp.setEnabled(true);
                                btnDeleteAll.setEnabled(true);
                                Toast.makeText(requireContext(), R.string.something_went_wrong, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show());
    }
}