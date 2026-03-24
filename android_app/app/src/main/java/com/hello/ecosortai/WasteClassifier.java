package com.hello.ecosortai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * WasteClassifier
 * ---------------
 * Loads waste_classifier.tflite from assets and runs inference on a Bitmap.
 *
 * Usage:
 * WasteClassifier classifier = new WasteClassifier(context);
 * String label = classifier.classify(bitmap);
 * classifier.close();
 *
 * IMPORTANT: copy waste_classifier.tflite into
 * android_app/app/src/main/assets/
 * before building the APK.
 */
public class WasteClassifier {

    // ── Model constants ────────────────────────────────────────────────────────
    private static final String MODEL_FILE = "waste_classifier.tflite";
    private static final int INPUT_SIZE = 224; // px — must match training
    private static final int NUM_CHANNELS = 3; // RGB
    private static final int NUM_CLASSES = 5;
    private static final int BYTES_PER_FLOAT = 4;

    // ── Class labels — order MUST match training (CLASSES list in train_model.py)
    private static final String[] LABELS = {
            "Glass", "Metal", "Organic", "Paper", "Plastic"
    };

    // ── Recyclability info for each class ─────────────────────────────────────
    private static final Map<String, String> RECYCLABILITY = new HashMap<>();
    static {
        RECYCLABILITY.put("Glass", "♻️ Recyclable");
        RECYCLABILITY.put("Metal", "♻️ Recyclable");
        RECYCLABILITY.put("Organic", "🌱 Compostable");
        RECYCLABILITY.put("Paper", "♻️ Recyclable");
        RECYCLABILITY.put("Plastic", "♻️ Recyclable");
    }

    // ── Disposal guidance for each class ──────────────────────────────────────
    private static final Map<String, String> DISPOSAL = new HashMap<>();
    static {
        DISPOSAL.put("Glass", "Place glass in the glass recycling container.");
        DISPOSAL.put("Metal", "Dispose of metal items at a scrap or recycling center.");
        DISPOSAL.put("Organic", "Put organic waste in a compost bin.");
        DISPOSAL.put("Paper", "Recycle if the paper is clean and dry.");
        DISPOSAL.put("Plastic", "Clean the plastic item and place it in the recycling bin.");
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    private final Interpreter interpreter;

    // ── Constructor ───────────────────────────────────────────────────────────
    public WasteClassifier(Context context) throws IOException {
        MappedByteBuffer model = loadModelFile(context);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(model, options);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Holds both the predicted label and its confidence score (0–1).
     */
    public static class ClassificationResult {
        public final String label;
        /** Top softmax probability in the range [0, 1]. */
        public final float confidence;

        ClassificationResult(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    /**
     * Run inference on a Bitmap and return the predicted waste category label.
     *
     * @param source Any Bitmap; will be resized to 224×224 internally.
     * @return Label string, e.g. "Plastic"
     */
    public String classify(Bitmap source) {
        return classifyWithConfidence(source).label;
    }

    /**
     * Run inference on a Bitmap and return both the predicted label and
     * the confidence score as a value in [0, 1].
     *
     * @param source Any Bitmap; will be resized to 224×224 internally.
     * @return {@link ClassificationResult} containing label + confidence.
     */
    public ClassificationResult classifyWithConfidence(Bitmap source) {
        ByteBuffer inputBuffer = preprocessBitmap(source);

        // Output buffer: [1][NUM_CLASSES] float probabilities
        float[][] output = new float[1][NUM_CLASSES];
        interpreter.run(inputBuffer, output);

        return getTopResult(output[0]);
    }

    /** Recyclability string for a given label (e.g. "♻️ Recyclable"). */
    public static String getRecyclability(String label) {
        return RECYCLABILITY.getOrDefault(label, "Unknown");
    }

    /** Disposal guidance string for a given label. */
    public static String getDisposalGuidance(String label) {
        return DISPOSAL.getOrDefault(label, "Dispose of responsibly.");
    }

    /** Release interpreter resources. Call this when the activity is destroyed. */
    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Memory-map the .tflite file from assets so the interpreter can read it
     * directly without copying it into heap memory.
     */
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor afd = context.getAssets().openFd(MODEL_FILE);
        FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
        FileChannel channel = fis.getChannel();
        return channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.getStartOffset(),
                afd.getDeclaredLength());
    }

    /**
     * Resize the bitmap to 224×224, convert each pixel to 3 normalised floats
     * in the range [0, 1], and pack them into a direct ByteBuffer.
     *
     * Buffer layout: [R, G, B, R, G, B, …] for every pixel, row-major.
     */
    private ByteBuffer preprocessBitmap(Bitmap source) {
        // Resize to model input size
        Bitmap resized = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true);

        // Allocate a direct ByteBuffer: 224 * 224 * 3 channels * 4 bytes/float
        int bufferSize = INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * BYTES_PER_FLOAT;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        buffer.order(ByteOrder.nativeOrder()); // must match TFLite's byte order

        // Extract pixels and normalise to [0.0, 1.0]
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // R
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f); // G
            buffer.putFloat((pixel & 0xFF) / 255.0f); // B
        }

        buffer.rewind(); // reset position to 0 before passing to interpreter
        return buffer;
    }

    /** Return the label with the highest softmax probability. */
    private String getTopLabel(float[] probabilities) {
        return getTopResult(probabilities).label;
    }

    /** Return both the label and the highest softmax probability. */
    private ClassificationResult getTopResult(float[] probabilities) {
        int bestIdx = 0;
        float bestScore = probabilities[0];
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > bestScore) {
                bestScore = probabilities[i];
                bestIdx = i;
            }
        }
        return new ClassificationResult(LABELS[bestIdx], bestScore);
    }
}
