package com.vg.restrictacesscontrol.activitys;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.vg.restrictacesscontrol.R;
import com.vg.restrictacesscontrol.views.ScanOverlayView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanActivity extends AppCompatActivity {

    private static final String PREF_FILE = "trip_cache";
    private static final String KEY_LOCATION = "location"; // "1" / "2"
    private static final String TAG_LANG = "LANG";

    private PreviewView previewView;
    private ScanOverlayView overlayView;
    private MaterialButton btnStart, btnLang;
    private TextView tvTitle, tvGate;

    private ExecutorService cameraExecutor;
    private volatile boolean handled = false;
    private volatile boolean scanningEnabled = false;

    private ImageAnalysis analysis;
    private BarcodeScanner scanner;

    private int secretTapCount = 0;
    private static final int SECRET_TAP_THRESHOLD = 19;

    private final androidx.activity.result.ActivityResultLauncher<String> requestCamera =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else { Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show(); finish(); }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logLocales("onCreate() BEFORE setContentView");
        setContentView(R.layout.activity_scan);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        btnStart    = findViewById(R.id.btnStart);
        btnLang     = findViewById(R.id.btnLang);
        tvTitle     = findViewById(R.id.tvTitle);
        tvGate      = findViewById(R.id.tvGate);

        cameraExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient();

        // LOG xem resource đã đổi chưa
        Log.d(TAG_LANG, "Strings now -> scanqrcode=" + getString(R.string.scanqrcode)
                + ", select_language_title=" + getString(R.string.select_language_title));

        // Start scan
        btnStart.setOnClickListener(v -> {
            if (!scanningEnabled) {
                handled = false;
                scanningEnabled = true;
                overlayView.start();
                btnStart.setEnabled(false);
            }
        });

        // Language button
        if (btnLang != null) {
            updateLangButtonLabel();
            btnLang.setOnClickListener(v -> showLanguageSelector());
        }

        // Easter egg chọn cổng
        tvTitle.setOnClickListener(v -> {
            secretTapCount++;
            if (secretTapCount >= SECRET_TAP_THRESHOLD) {
                secretTapCount = 0;
                showHiddenLocationSelector();
            }
        });

        ensureDefaultLocation();
        updateGateTitle();

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCamera.launch(Manifest.permission.CAMERA);
        }

        logLocales("onCreate() END");
    }

    // ===== Language helpers with logs =====
    private void showLanguageSelector() {
        String[] items = new String[]{
                getString(R.string.lang_vietnamese),
                getString(R.string.lang_english)
        };
        int current = getCurrentLangIndex();
        Log.d(TAG_LANG, "showLanguageSelector: current=" + current + ", tag=" + getCurrentLangTag());

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.select_language_title))
                .setSingleChoiceItems(items, current, (dialog, which) -> {
                    String tag = (which == 0) ? "vi" : "en";
                    Log.d(TAG_LANG, "User chose index=" + which + ", tag=" + tag
                            + ", before setApplicationLocales=" + AppCompatDelegate.getApplicationLocales());
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
                    Log.d(TAG_LANG, "After setApplicationLocales -> " + AppCompatDelegate.getApplicationLocales());
                    dialog.dismiss();
                    recreate(); // sẽ vào onCreate lại -> kiểm tra log
                })
                .show();
    }

    private void updateLangButtonLabel() {
        String tag = getCurrentLangTag();
        String label = tag.startsWith("vi") ? "VI" : "EN";
        if (btnLang != null) btnLang.setText(label);
        Log.d(TAG_LANG, "updateLangButtonLabel -> tag=" + tag + ", label=" + label);
    }

    private String getCurrentLangTag() {
        LocaleListCompat list = AppCompatDelegate.getApplicationLocales();
        String tag = (!list.isEmpty()) ? list.toLanguageTags() : java.util.Locale.getDefault().toLanguageTag();
        Log.d(TAG_LANG, "getCurrentLangTag -> " + tag);
        return tag;
    }

    private int getCurrentLangIndex() {
        return getCurrentLangTag().startsWith("vi") ? 0 : 1;
    }

    private void logLocales(String where) {
        LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
        String appTag = appLocales.isEmpty() ? "(empty)" : appLocales.toLanguageTags();
        String sysTag = getResources().getConfiguration().getLocales().toLanguageTags();
        Log.d(TAG_LANG, where + " | AppLocales=" + appTag + ", Resources.Locales=" + sysTag);
    }

    // ===== Gate helpers =====
    private void ensureDefaultLocation() {
        SharedPreferences sp = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String cur = sp.getString(KEY_LOCATION, null);
        if (cur == null || cur.trim().isEmpty()) {
            sp.edit().putString(KEY_LOCATION, "1").apply();
        }
    }
    private void updateGateTitle() {
        String code = getSharedPreferences(PREF_FILE, MODE_PRIVATE).getString(KEY_LOCATION, "1");
        int resId = "2".equals(code) ? R.string.gate_back : R.string.gate_front;
        String label = getString(resId);
        Log.d(TAG_LANG, "updateGateTitle -> code=" + code + ", label=" + label);
        if (tvGate != null) tvGate.setText(label);
    }

    private void showHiddenLocationSelector() {
        String[] labels = new String[]{ getString(R.string.gate_front), getString(R.string.gate_back) };
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.select_gate_title))
                .setSingleChoiceItems(labels, getCurrentSelectionIndex(), (dialog, which) -> {
                    String code = (which == 0) ? "1" : "2";
                    saveLocationPref(code);
                    updateGateTitle();
                    Toast.makeText(this, labels[which], Toast.LENGTH_SHORT).show();
                    Log.d(TAG_LANG, "Gate chosen -> code=" + code + ", label=" + labels[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private int getCurrentSelectionIndex() {
        SharedPreferences sp = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String code = sp.getString(KEY_LOCATION, "1");
        return "2".equals(code) ? 1 : 0;
    }
    private void saveLocationPref(String code) {
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit().putString(KEY_LOCATION, code).apply();
    }

    // ===== Camera / Scan =====
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, analysis);
            } catch (Exception ignore) {}
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(ImageProxy imageProxy) {
        try {
            if (!scanningEnabled || handled || imageProxy.getImage() == null) {
                imageProxy.close(); return;
            }
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            scanner.process(image)
                    .addOnSuccessListener(this::onBarcodes)
                    .addOnCompleteListener(t -> imageProxy.close());
        } catch (Exception e) {
            imageProxy.close();
        }
    }

    private void onBarcodes(List<Barcode> list) {
        if (!scanningEnabled || handled || list == null || list.isEmpty()) return;
        RectF frame = overlayView.getFrame();
        Barcode chosen = null;
        for (Barcode bc : list) {
            android.graphics.Rect box = bc.getBoundingBox();
            if (box != null) {
                RectF boxF = new RectF(box);
                if (RectF.intersects(boxF, frame)) { chosen = bc; break; }
            } else if (bc.getRawValue() != null) { chosen = bc; break; }
        }
        if (chosen == null) return;

        handled = true;
        scanningEnabled = false;
        runOnUiThread(() -> {
            overlayView.stop();
            btnStart.setEnabled(true);
        });

        String raw = chosen.getRawValue();
        Intent i = new Intent(this, TripDetailActivity.class);
        i.putExtra("qr_raw", raw);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (overlayView != null) overlayView.stop();
    }
}
