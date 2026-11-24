package com.example.ringo_star.fragment;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.example.ringo_star.BuildConfig;
import com.example.ringo_star.R;
import com.example.ringo_star.ReportCreatedActivity;
import com.example.ringo_star.data.DatabaseClient;
import com.example.ringo_star.data.entity.User;
import com.example.ringo_star.llm.InferenceModel;
import com.example.ringo_star.llm.ModelArtifactVerifier;
import com.example.ringo_star.utils.ProfileGraphUpdater;
import com.example.ringo_star.utils.PDFUtils;
import com.example.ringo_star.utils.RingoStarRDF4J;

import org.eclipse.rdf4j.model.Model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MagicFragment extends Fragment {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private DownloadManager downloadManager;
    private android.content.SharedPreferences downloadPreferences;
    private long downloadId = -1;
    private String activeDownloadModel;
    private static final String DOWNLOAD_PREFS = "model_download_state";
    private Runnable downloadPoll;
    private boolean downloading;
    private boolean generating;

    private RadioGroup radioGroup;
    private RadioButton radioButtonGemma, radioButtonFalcon, radioButtonStable, radioButtonPhi;
    private TextView txtModelDescription, txtPercentage;
    private ConstraintLayout progressContainer;
    private ProgressBar progressBarDownload;
    private Button btnDownloadModel;
    private ImageView imgMagic;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_magic, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        downloading = false;
        radioGroup = view.findViewById(R.id.radioGroup);
        radioButtonGemma = view.findViewById(R.id.radioButtonGemma);
        radioButtonFalcon = view.findViewById(R.id.radioButtonFalcon);
        radioButtonStable = view.findViewById(R.id.radioButtonStable);
        radioButtonPhi = view.findViewById(R.id.radioButtonPhi);
        txtModelDescription = view.findViewById(R.id.txtModelDescription);
        txtPercentage = view.findViewById(R.id.txtPercentage);
        progressContainer = view.findViewById(R.id.constraintLayoutProgressBar);
        progressBarDownload = view.findViewById(R.id.progressBarDownload);
        btnDownloadModel = view.findViewById(R.id.btnDownloadModel);
        imgMagic = view.findViewById(R.id.imgMagic);
        downloadManager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        downloadPreferences = requireContext().getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE);

        radioGroup.setOnCheckedChangeListener((group, id) -> {
            if (id == R.id.radioButtonGemma) setModelDescription(R.string.gemma2b_hint);
            else if (id == R.id.radioButtonFalcon) setModelDescription(R.string.falcon_rw1b_hint);
            else if (id == R.id.radioButtonStable) setModelDescription(R.string.stable_lm3b_hint);
            else if (id == R.id.radioButtonPhi) setModelDescription(R.string.phi2_hint);
            updateModelControls();
        });

        btnDownloadModel.setOnClickListener(v -> {
            String model = selectedModel();
            if (model.isEmpty()) return;
            if (downloading) {
                downloadManager.remove(downloadId);
                cancelDownload();
            } else if (modelExists(model)) {
                InferenceModel.release(model);
                File path = modelPath(model);
                if (path != null && !path.delete()) showError();
                updateModelControls();
            } else {
                beginDownload(model);
            }
        });

        imgMagic.setOnClickListener(v -> generateReport(selectedModel()));
        setModelDescription(R.string.gemma2b_hint);
        // DownloadManager survives fragment recreation; restore the active transfer.
        android.content.SharedPreferences saved = downloadPreferences;
        downloadId = saved.getLong("id", -1);
        activeDownloadModel = saved.getString("model", null);
        if (downloadId != -1 && activeDownloadModel != null) {
            downloading = true;
            progressContainer.setVisibility(View.VISIBLE);
            txtPercentage.setVisibility(View.VISIBLE);
            pollDownload(activeDownloadModel);
        }
        updateModelControls();
    }

    private void setModelDescription(int resource) {
        txtModelDescription.setText(Html.fromHtml(getString(resource), Html.FROM_HTML_MODE_LEGACY));
    }

    private String selectedModel() {
        int id = radioGroup.getCheckedRadioButtonId();
        if (id == R.id.radioButtonGemma) return "gemma2b";
        if (id == R.id.radioButtonFalcon) return "falcon_rw1b";
        if (id == R.id.radioButtonStable) return "stable_lm3b";
        if (id == R.id.radioButtonPhi) return "phi2";
        return "";
    }

    private File modelPath(String model) {
        File downloads = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return downloads == null ? null : new File(downloads, model + ".bin");
    }

    private boolean modelExists(String model) {
        File file = modelPath(model);
        return file != null && file.isFile() && file.length() > 0;
    }

    private File partialPath(String model) {
        File complete = modelPath(model);
        return complete == null ? null : new File(complete.getParentFile(), complete.getName() + ".part");
    }

    private void clearDownloadState() {
        if (downloadPreferences != null) downloadPreferences.edit().clear().apply();
        downloadId = -1;
        activeDownloadModel = null;
    }

    private void cancelDownload() {
        if (activeDownloadModel != null) {
            File partial = partialPath(activeDownloadModel);
            if (partial != null && partial.exists() && !partial.delete()) {
                Log.w("RingoStar", "Unable to remove cancelled partial download");
            }
        }
        finishDownload(false, false);
    }

    private void beginDownload(String model) {
        String base = BuildConfig.MODEL_SERVER_BASE_URL;
        if (base == null || base.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Configure the model server URL before downloading", Toast.LENGTH_LONG).show();
            return;
        }
        if (downloadManager == null || modelPath(model) == null) { showError(); return; }
        File partial = partialPath(model);
        if (partial != null && partial.exists() && !partial.delete()) { showError(); return; }
        try {
            String url = base.replaceAll("/+$", "") + "/api/download/" + model;
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
            request.setDestinationInExternalFilesDir(requireContext(), Environment.DIRECTORY_DOWNLOADS, model + ".bin.part");
            downloadId = downloadManager.enqueue(request);
            activeDownloadModel = model;
            downloadPreferences.edit().putLong("id", downloadId).putString("model", model).apply();
            downloading = true;
            progressBarDownload.setProgress(0);
            txtPercentage.setText("0%");
            progressContainer.setVisibility(View.VISIBLE);
            txtPercentage.setVisibility(View.VISIBLE);
            updateModelControls();
            pollDownload(model);
        } catch (RuntimeException exception) {
            Log.e("RingoStar", "Model download failed to start", exception);
            showError();
        }
    }

    private void pollDownload(String model) {
        downloadPoll = new Runnable() {
            @Override public void run() {
                if (!isAdded() || !downloading || downloadManager == null) return;
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        finishDownload(false, true); return;
                    }
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        if (downloadPoll != null) handler.removeCallbacks(downloadPoll);
                        File partial = partialPath(model);
                        File complete = modelPath(model);
                        executor.execute(() -> {
                            boolean valid = false;
                            try {
                                if (ModelArtifactVerifier.matches(partial, ModelArtifactVerifier.expectedSha256(model))) {
                                    Files.move(partial.toPath(), complete.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    valid = true;
                                }
                            } catch (IOException error) {
                                Log.e("RingoStar", "Unable to verify or install downloaded model", error);
                            }
                            boolean success = valid;
                            handler.post(() -> finishDownload(success, true));
                        });
                        return;
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        finishDownload(false, true); return;
                    }
                    long bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (total > 0) {
                        int percentage = (int) Math.min(100, bytes * 100 / total);
                        progressBarDownload.setProgress(percentage);
                        txtPercentage.setText(percentage + "%");
                    } else {
                        txtPercentage.setText("Downloading…");
                    }
                } catch (RuntimeException exception) {
                    Log.e("RingoStar", "Download status unavailable", exception);
                    finishDownload(false, true); return;
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(downloadPoll);
    }

    private void finishDownload(boolean success, boolean notifyFailure) {
        downloading = false;
        if (downloadPoll != null) handler.removeCallbacks(downloadPoll);
        if (!success && activeDownloadModel != null) {
            File partial = partialPath(activeDownloadModel);
            if (partial != null && partial.exists() && !partial.delete()) {
                Log.w("RingoStar", "Unable to remove failed partial download");
            }
        }
        clearDownloadState();
        if (!isAdded() || getView() == null) return;
        progressContainer.setVisibility(View.GONE);
        txtPercentage.setVisibility(View.GONE);
        updateModelControls();
        if (!success && notifyFailure) showError();
    }

    private void generateReport(String modelName) {
        if (modelName.isEmpty() || generating || !modelExists(modelName)) return;
        generating = true;
        updateModelControls();
        imgMagic.setImageResource(R.drawable.hourglass);
        Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                File selectedFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), modelName + ".bin");
                if (!ModelArtifactVerifier.matches(selectedFile, ModelArtifactVerifier.expectedSha256(modelName))) {
                    throw new IOException("Installed model does not match the configured checksum");
                }
                User user = DatabaseClient.getInstance(context).getDatabase().userDAO().getUser();
                if (user == null) throw new IllegalStateException("Profile unavailable");
                Model model = RingoStarRDF4J.loadModelFromFile(context, "kg.ttl");
                // Room is the profile source of truth after an interrupted update.
                Model aligned = ProfileGraphUpdater.apply(model, user, LocalDate.now());
                if (model == null || !model.equals(aligned)) RingoStarRDF4J.saveModelToFile(aligned, context, "kg.ttl");
                model = aligned;
                String prompt = "Briefly describe the following information as if you were a doctor:\n"
                        + RingoStarRDF4J.promptifyModel(model);
                String response = InferenceModel.getInstance(context, modelName).generateResponse(prompt);
                if (response == null || response.isEmpty()) throw new IllegalStateException("Empty response");
                PDFUtils.createPdf(context, user, response);
                handler.post(() -> {
                    if (!isAdded()) return;
                    generating = false;
                    updateModelControls();
                    imgMagic.setImageResource(R.drawable.ic_magic);
                    startActivity(new Intent(requireActivity(), ReportCreatedActivity.class));
                    requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    requireActivity().finish();
                });
            } catch (Exception error) {
                Log.e("RingoStar", "Unable to generate report", error);
                handler.post(() -> {
                    if (!isAdded()) return;
                    generating = false;
                    imgMagic.setImageResource(R.drawable.ic_magic);
                    updateModelControls();
                    showError();
                });
            } finally {
                InferenceModel.release(modelName);
            }
        });
    }

    private void showError() {
        if (isAdded()) Toast.makeText(requireContext(), R.string.something_went_wrong, Toast.LENGTH_SHORT).show();
    }

    private void updateModelControls() {
        markAvailable(radioButtonGemma, "gemma2b", R.string.gemma2b);
        markAvailable(radioButtonFalcon, "falcon_rw1b", R.string.falcon_rw1b);
        markAvailable(radioButtonStable, "stable_lm3b", R.string.stable_lm3b);
        markAvailable(radioButtonPhi, "phi2", R.string.phi2);
        String model = selectedModel();
        boolean available = !model.isEmpty() && modelExists(model);
        btnDownloadModel.setEnabled(!generating && !model.isEmpty());
        btnDownloadModel.setText(downloading ? R.string.cancel : available ? R.string.delete : R.string.download);
        imgMagic.setVisibility(available ? View.VISIBLE : View.GONE);
        imgMagic.setEnabled(!generating && !downloading && available);
        for (int i = 0; i < radioGroup.getChildCount(); i++) {
            radioGroup.getChildAt(i).setEnabled(!downloading && !generating);
        }
    }

    private void markAvailable(RadioButton button, String model, int label) {
        if (modelExists(model)) button.setText(label);
        else button.setText(Html.fromHtml(getString(label) +
                " (<strong>↓<font color='#FF0000'>*</font></strong>)", Html.FROM_HTML_MODE_LEGACY));
    }

    @Override public void onDestroyView() {
        if (downloadPoll != null) handler.removeCallbacks(downloadPoll);
        executor.shutdown();
        super.onDestroyView();
    }
}
