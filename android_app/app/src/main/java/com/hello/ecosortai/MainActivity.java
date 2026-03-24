package com.hello.ecosortai;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * MainActivity
 * ------------
 * Entry point of the EcoSort AI waste classifier app.
 *
 * Flow:
 * 1. User taps Camera or Gallery button.
 * 2. System returns a Bitmap via onActivityResult.
 * 3. Bitmap is displayed in ImageView and classified by WasteClassifier.
 * 4. Results (type, recyclability, disposal guidance) are shown in the
 * CardView.
 */
public class MainActivity extends AppCompatActivity {

    // ── UI references ─────────────────────────────────────────────────────────
    private ImageView imageViewPreview;
    private TextView tvImageHint;
    private TextView tvPredictedType;
    private TextView tvConfidenceScore;
    private TextView tvRecyclability;
    private TextView tvDisposalGuidance;
    private TextView tvScanCount;
    private CardView cardResults;
    private View layoutLoading;

    // ── Background executor (single thread — inference is sequential) ──────────
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Scan history ────────────────────────────────────────────────────────
    private static final int MAX_HISTORY = 10;
    private final List<ScanRecord> scanHistory = new ArrayList<>();
    private ScanHistoryAdapter historyAdapter;
    private RecyclerView rvScanHistory;

    // ── Session counter ───────────────────────────────────────────────────────
    private int scanCount = 0;

    // ── Classifier ────────────────────────────────────────────────────────────
    private WasteClassifier classifier;

    // Temporary URI used to store the full-resolution camera image
    private Uri currentPhotoUri;

    // ── Activity result launchers ─────────────────────────────────────────────

    /** Gallery: pick image from the device photo library */
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        Bitmap bitmap;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            // API 28+ (Android 9+): use ImageDecoder (non-deprecated)
                            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), imageUri);
                            bitmap = ImageDecoder.decodeBitmap(source,
                                    (decoder, info, src) -> decoder.setTargetSampleSize(1));
                            // Ensure the bitmap is mutable and in ARGB_8888 for pixel ops
                            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                        } else {
                            // API 24-27: getBitmap is the only option
                            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                        }
                        handleImage(bitmap);
                    } catch (IOException e) {
                        showError("Failed to load image from gallery.");
                    }
                }
            });

    /** Camera: capture a photo using the device camera */
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && currentPhotoUri != null) {
                    try {
                        Bitmap bitmap;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(),
                                    currentPhotoUri);
                            bitmap = ImageDecoder.decodeBitmap(source,
                                    (decoder, info, src) -> decoder.setTargetSampleSize(1));
                            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                        } else {
                            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), currentPhotoUri);
                        }
                        handleImage(bitmap);
                    } catch (IOException e) {
                        showError("Failed to load captured image.");
                    }
                }
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        imageViewPreview = findViewById(R.id.imageViewPreview);
        tvImageHint = findViewById(R.id.tvImageHint);
        tvPredictedType = findViewById(R.id.tvPredictedType);
        tvConfidenceScore = findViewById(R.id.tvConfidenceScore);
        tvRecyclability = findViewById(R.id.tvRecyclability);
        tvDisposalGuidance = findViewById(R.id.tvDisposalGuidance);
        tvScanCount = findViewById(R.id.tvScanCount);
        cardResults = findViewById(R.id.cardResults);
        layoutLoading = findViewById(R.id.layoutLoading);

        // Set up the Recent Scans RecyclerView
        rvScanHistory = findViewById(R.id.rvScanHistory);
        historyAdapter = new ScanHistoryAdapter(scanHistory);
        rvScanHistory.setLayoutManager(new LinearLayoutManager(this));
        rvScanHistory.setAdapter(historyAdapter);
        rvScanHistory.setHasFixedSize(false);

        Button btnCamera = findViewById(R.id.btnCamera);
        Button btnGallery = findViewById(R.id.btnGallery);
        Button btnClear = findViewById(R.id.btnClear);

        // Load TFLite model
        // IMPORTANT: waste_classifier.tflite must be placed in app/src/main/assets/
        try {
            classifier = new WasteClassifier(this);
        } catch (IOException e) {
            showError("Failed to load model. Make sure waste_classifier.tflite " +
                    "is in app/src/main/assets/");
        }

        // Button listeners
        btnCamera.setOnClickListener(v -> launchCamera());
        btnGallery.setOnClickListener(v -> launchGallery());
        btnClear.setOnClickListener(v -> clearImage());
    }

    private void clearImage() {
        // Reset image to placeholder
        imageViewPreview.setImageResource(R.drawable.ic_image_placeholder);
        tvImageHint.setVisibility(View.VISIBLE);

        // Hide results and clear button
        cardResults.setVisibility(View.GONE);
        findViewById(R.id.btnClear).setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) {
            classifier.close(); // release TFLite interpreter resources
        }
        executor.shutdownNow(); // stop background thread
    }

    // ── Button actions ────────────────────────────────────────────────────────

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            // Create a temporary file in the cache directory
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = new File(getCacheDir(), "camera_images");
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                showError("Could not create directory for image.");
                return;
            }
            photoFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException ex) {
            showError("Error creating image file for camera.");
        }

        // Continue only if the File was successfully created
        if (photoFile != null) {
            // Generate URI using the FileProvider
            currentPhotoUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    photoFile);

            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            try {
                cameraLauncher.launch(intent);
            } catch (android.content.ActivityNotFoundException e) {
                showError("No camera app found on this device.");
            }
        }
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    // ── Inference pipeline ────────────────────────────────────────────────────

    /**
     * Called whenever a Bitmap is available (camera or gallery).
     * Shows the image immediately, then runs inference on a background thread
     * so the ProgressBar can animate smoothly without freezing the UI.
     */
    private void handleImage(Bitmap bitmap) {
        // 1. Show the image & hide the hint
        imageViewPreview.setImageBitmap(bitmap);
        tvImageHint.setVisibility(View.GONE);

        if (classifier == null) {
            showError("Classifier not initialised.");
            return;
        }

        // 2. Show loading indicator, hide stale results
        layoutLoading.setVisibility(View.VISIBLE);
        cardResults.setVisibility(View.GONE);

        // 3. Run inference off the UI thread
        executor.submit(() -> {
            final WasteClassifier.ClassificationResult result = classifier.classifyWithConfidence(bitmap);

            // 4. Post all UI updates back to the main thread
            runOnUiThread(() -> {
                // Hide loading indicator
                layoutLoading.setVisibility(View.GONE);

                String label = result.label;
                float confidence = result.confidence;

                // Populate result TextViews
                tvPredictedType.setText(label);

                // Color-code the waste type label
                int labelColor;
                switch (label) {
                    case "Plastic":
                        labelColor = 0xFF1565C0;
                        break; // Blue
                    case "Paper":
                        labelColor = 0xFF6D4C41;
                        break; // Brown
                    case "Metal":
                        labelColor = 0xFF546E7A;
                        break; // Gray
                    case "Glass":
                        labelColor = 0xFF00695C;
                        break; // Teal
                    case "Organic":
                        labelColor = 0xFF2E7D32;
                        break; // Green
                    default:
                        labelColor = 0xFF000000;
                        break;
                }
                tvPredictedType.setTextColor(labelColor);

                tvConfidenceScore.setText(
                        String.format(Locale.US, "Confidence: %.2f%%", confidence * 100f));

                String rawCategory = WasteClassifier.getRecyclability(label);
                String categoryWord = rawCategory.contains(" ")
                        ? rawCategory.substring(rawCategory.lastIndexOf(' ') + 1)
                        : rawCategory;
                tvRecyclability.setText("Category: " + categoryWord);
                tvDisposalGuidance.setText(WasteClassifier.getDisposalGuidance(label));

                // Increment scan counter
                scanCount++;
                tvScanCount.setText("Items Scanned: " + scanCount);

                // Add to history (most recent first, capped at MAX_HISTORY)
                String timestamp = new SimpleDateFormat("hh:mm a", Locale.US).format(new Date());
                scanHistory.add(0, new ScanRecord(label, confidence, timestamp));
                if (scanHistory.size() > MAX_HISTORY) {
                    scanHistory.remove(scanHistory.size() - 1);
                }
                historyAdapter.notifyDataSetChanged();

                // Reveal results card & clear button
                cardResults.setVisibility(View.VISIBLE);
                findViewById(R.id.btnClear).setVisibility(View.VISIBLE);
            });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
