package com.example.ringo_star;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.Toast;
import android.net.Uri;
import androidx.activity.result.contract.ActivityResultContracts;
import com.example.ringo_star.security.SecureFiles;
import com.example.ringo_star.security.SecuritySession;
import java.io.OutputStream;
import java.io.IOException;

import androidx.activity.EdgeToEdge;
import com.example.ringo_star.security.SecureActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ReportCreatedActivity extends SecureActivity {
    private final androidx.activity.result.ActivityResultLauncher<String> exportReport =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                boolean authorized = SecuritySession.documentExportAllowed();
                SecuritySession.endDocumentExport();
                if (uri == null || !authorized) return;
                new Thread(() -> {
                    try {
                        byte[] clearReport = SecureFiles.read(getApplicationContext(), "report.pdf");
                        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                            if (out == null) throw new IOException("Export destination unavailable");
                            out.write(clearReport);
                        }
                        runOnUiThread(() -> Toast.makeText(this, R.string.export_completed,
                                Toast.LENGTH_SHORT).show());
                    } catch (IOException error) {
                        try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
                        runOnUiThread(() -> Toast.makeText(this, R.string.something_went_wrong,
                                Toast.LENGTH_SHORT).show());
                    }
                }).start();
            });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_report_created);

        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.black));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button export = findViewById(R.id.btnExportReport);
        export.setOnClickListener(v -> {
            SecuritySession.beginDocumentExport();
            exportReport.launch("ringo-star-report.pdf");
        });

        ImageView imgHome = findViewById(R.id.imgHome);

        imgHome.setOnClickListener(v -> {
            startActivity(new Intent(ReportCreatedActivity.this, HomeActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        });
    }
}