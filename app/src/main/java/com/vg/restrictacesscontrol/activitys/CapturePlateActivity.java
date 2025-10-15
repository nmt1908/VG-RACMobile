package com.vg.restrictacesscontrol.activitys;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Size;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.vg.restrictacesscontrol.R;
import com.vg.restrictacesscontrol.net.OllamaApi;
import com.vg.restrictacesscontrol.net.OllamaRequest;
import com.vg.restrictacesscontrol.net.OllamaResponse;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CapturePlateActivity extends ComponentActivity {

    private static final int REQ_CAMERA = 1001;

    private PreviewView previewView;
    private FloatingActionButton btnCapture;
    private CircularProgressIndicator progress;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private String mode = "plate"; // "plate" | "odo"

    // Đổi IP theo server của bạn
    private static final String OLLAMA_BASE = "http://10.13.34.166:11434/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_plate);

        // Nhận mode
        String m = getIntent().getStringExtra("mode");
        if (m != null) mode = m;

        previewView = findViewById(R.id.previewView);
        btnCapture  = findViewById(R.id.btnCapture);
        progress    = findViewById(R.id.progress);

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }

        btnCapture.setOnClickListener(v -> takePhotoAndOcr());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                Snackbar.make(previewView, "Không khởi tạo được camera: " + e.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhotoAndOcr() {
        if (imageCapture == null) return;
        try {
            File outFile = File.createTempFile("scan_", ".jpg", getCacheDir());
            ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(outFile).build();

            imageCapture.takePicture(opts, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
                @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    runOnUiThread(() -> { progress.setVisibility(View.VISIBLE); btnCapture.setEnabled(false); });
                    ocrWithOllama(outFile);
                }
                @Override public void onError(@NonNull ImageCaptureException exception) {
                    runOnUiThread(() -> Snackbar.make(previewView,
                            "Chụp lỗi: " + exception.getMessage(), Snackbar.LENGTH_LONG).show());
                }
            });

        } catch (Exception e) {
            Snackbar.make(previewView, "Lỗi file tạm: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void ocrWithOllama(File imageFile) {
        try {
            byte[] bytes = readAll(imageFile);
            String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            String prompt;
            if ("odo".equals(mode)) {
                prompt = "Task: Read the vehicle odometer (ODO) number from the attached image. " +
                        "Return ONLY a JSON object with keys: odo_reading (string), confidence (number 0..1). " +
                        "If unsure, put 'unknown' and a low confidence. Use digits only (0–9) and keep any leading zeros if present. " +
                        "Do not add any text outside the JSON. Example: {odo_reading: 998665, confidence: 0.95}";
            } else {
                prompt = "Task: Read a Vietnam vehicle license plate from the attached image. " +
                        "Return ONLY a JSON object with keys: plate_number (string), confidence (number 0..1). " +
                        "If unsure, put \\\"unknown\\\" and a low confidence. Use uppercase letters and keep the VN formatting " +
                        "with hyphen and dot when visible (e.g. 70-C1 053.14). Do not add any text outside the JSON.";
            }

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(OLLAMA_BASE)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            OllamaApi api = retrofit.create(OllamaApi.class);

            OllamaRequest body = OllamaRequest.build(b64, prompt);
            api.generate(body).enqueue(new Callback<OllamaResponse>() {
                @Override public void onResponse(Call<OllamaResponse> call, Response<OllamaResponse> resp) {
                    runOnUiThread(() -> { progress.setVisibility(View.GONE); btnCapture.setEnabled(true); });
                    if (!resp.isSuccessful() || resp.body() == null) {
                        runOnUiThread(() -> Snackbar.make(previewView,
                                "OCR lỗi: " + resp.code(), Snackbar.LENGTH_LONG).show());
                        return;
                    }
                    handleResponseAndFinish(resp.body().response);
                }

                @Override public void onFailure(Call<OllamaResponse> call, Throwable t) {
                    runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        btnCapture.setEnabled(true);
                        Snackbar.make(previewView, "Mạng/OCR lỗi: " + t.getMessage(),
                                Snackbar.LENGTH_LONG).show();
                    });
                }
            });

        } catch (Exception e) {
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                btnCapture.setEnabled(true);
                Snackbar.make(previewView, "Encode lỗi: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
            });
        }
    }

    private void handleResponseAndFinish(String responseField) {
        String plate = null, odo = null;
        try {
            JSONObject o = new JSONObject(responseField);
            plate = o.optString("plate_number", null);
            odo   = o.optString("odo_reading", null);
        } catch (Exception ignored) {}

        Intent data = new Intent();
        data.putExtra("mode", mode);
        data.putExtra("from_capture", true);
        if ("odo".equals(mode)) {
            data.putExtra("odo_reading", odo != null ? odo : "unknown");
        } else {
            data.putExtra("plate_number", plate != null ? plate : "unknown");
        }
        setResult(RESULT_OK, data);
        finish();
    }

    private byte[] readAll(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) f.length());
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        in.close();
        return bos.toByteArray();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) startCamera();
    }
}
