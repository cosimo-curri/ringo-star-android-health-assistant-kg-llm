package com.example.ringo_star.llm;

import android.content.Context;
import android.os.Environment;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;

/** Owns the active native inference engine; switching models disposes the previous one. */
public final class InferenceModel {
    private final String model;
    private final String modelPath;
    private final LlmInference engine;
    private static InferenceModel instance;

    private InferenceModel(Context context, String model) {
        this.model = model;
        File models = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (models == null) throw new IllegalStateException("Model storage is unavailable");
        File file = new File(models, model + ".bin");
        if (!file.isFile()) throw new IllegalArgumentException("Model not found: " + model);
        modelPath = file.getAbsolutePath();
        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build();
        engine = LlmInference.createFromOptions(context.getApplicationContext(), options);
    }

    public static synchronized InferenceModel getInstance(Context context, String model) {
        if (instance != null && !instance.model.equals(model)) {
            instance.engine.close();
            instance = null;
        }
        if (instance == null) instance = new InferenceModel(context, model);
        return instance;
    }

    public static synchronized void release(String model) {
        if (instance != null && instance.model.equals(model)) {
            instance.engine.close();
            instance = null;
        }
    }

    public String generateResponse(String prompt) {
        return engine.generateResponse(prompt);
    }

    public String getModelName() {
        switch (model) {
            case "gemma2b": return "Gemma 2B";
            case "falcon_rw1b": return "Falcon-RW-1B";
            case "stable_lm3b": return "StableLM-3B";
            case "phi2": return "Phi-2";
            default: return model;
        }
    }

    public String getModelPath() { return modelPath; }
}
