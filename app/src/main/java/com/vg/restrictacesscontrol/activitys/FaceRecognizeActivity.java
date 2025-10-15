package com.vg.restrictacesscontrol.activitys;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Matrix;
import android.media.ExifInterface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.AsyncTask;
import android.os.HandlerThread;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import okhttp3.*;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.vg.restrictacesscontrol.R;
import com.vg.restrictacesscontrol.overlay.FaceAnalyzer;
import com.vg.restrictacesscontrol.overlay.FaceGraphicOverlay;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class FaceRecognizeActivity extends AppCompatActivity implements FaceAnalyzer.FaceDetectionListener {
    private PreviewView previewView;
    private FaceGraphicOverlay graphicOverlay;
    private Preview preview;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ProcessCameraProvider cameraProvider;

    // Thread cho Analyzer (KHÔNG chạy trên main)
    private HandlerThread analysisThread;
    private Executor analysisExecutor;

    private boolean isTakingPhoto = false;
    private boolean isCameraBound = false;

    private final OkHttpClient httpClient = new OkHttpClient();
    private long faceStraightStartTime = 0;
    private static final long STRAIGHT_FACE_DURATION = 1000; // ms

    TextView alertTextView, labelName, nameTextView, labelCardId, cardIDTextView, labelSimilarity, similarityTextView;
    ProgressBar loadingSpinner;
    private LinearLayout loadingContainer, userInfoPanel;

    private final ArrayList<JSONObject> verifiedPeople = new ArrayList<>();
    private FloatingActionButton btnDone;
    private long IDLE_DELAY_MS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_recognize);

        if (!isInternetAvailable()) {
            Toast.makeText(this, "No Internet Connection", Toast.LENGTH_LONG).show();
        }

        // UI refs
        loadingContainer = findViewById(R.id.loadingContainer);
        userInfoPanel = findViewById(R.id.userInfoPanel);
        previewView = findViewById(R.id.previewView);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        alertTextView = findViewById(R.id.labelAlert);
        labelName = findViewById(R.id.labelUserName);
        nameTextView = findViewById(R.id.userName);
        labelCardId = findViewById(R.id.labelUserCardId);
        cardIDTextView = findViewById(R.id.userCardId);
        labelSimilarity = findViewById(R.id.labelUserSimilarity);
        similarityTextView = findViewById(R.id.userSimilarity);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        btnDone = findViewById(R.id.btnDone);

        graphicOverlay.setCameraFacing(true); // camera trước

        IDLE_DELAY_MS = getSharedPreferences("settings", MODE_PRIVATE).getInt("time_waiting", 1000);
        Log.d("IDLE_DELAY_MS", String.valueOf(IDLE_DELAY_MS));

        // Khởi tạo thread cho Analyzer
        analysisThread = new HandlerThread("cam-analysis");
        analysisThread.start();
        analysisExecutor = new Handler(analysisThread.getLooper())::post;

        btnDone.setOnClickListener(v -> {
            try {
                Intent data = new Intent();
                ArrayList<String> verifiedJsonList = new ArrayList<>();
                for (JSONObject person : verifiedPeople) {
                    if (!person.has("photo")) {
                        String cardId = person.optString("cardId", "");
                        if (!cardId.isEmpty()) {
                            person.put("photo",
                                    "http://gmo021.cansportsvg.com/api/pccRfid/publicPhotoByEmpno/"
                                            + cardId + "/CSVN@741236");
                        }
                    }
                    verifiedJsonList.add(person.toString());
                }
                data.putStringArrayListExtra("verified_people", verifiedJsonList);
                setResult(RESULT_OK, data);
            } catch (Exception e) {
                Log.e("FaceRecognize", "btnDone error", e);
            }
            stopCamera();
            finish();
        });

        // Quyền camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }
    }

    /** Lifecycle chuẩn cho CameraX */
    @Override protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        stopCamera();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopCamera();
        try {
            if (analysisThread != null) {
                analysisThread.quitSafely();
                analysisThread = null;
            }
        } catch (Exception ignored) {}
    }

    private void startCamera() {
        if (isFinishing() || isDestroyed()) {
            Log.w("CameraX", "startCamera() ignored — activity is finishing/destroyed");
            return;
        }
        if (isCameraBound) {
            Log.d("CameraX", "startCamera() skipped — already bound");
            return;
        }

        isTakingPhoto = false;
        faceStraightStartTime = 0;

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // Chạy Analyzer trên background thread
                imageAnalysis.setAnalyzer(analysisExecutor,
                        new FaceAnalyzer(this, graphicOverlay, this));

                // Rotation từ previewView (ổn định hơn getDefaultDisplay)
                int rotation = (previewView.getDisplay() != null)
                        ? previewView.getDisplay().getRotation()
                        : android.view.Surface.ROTATION_0;

                imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(rotation)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
                isCameraBound = true;

                Log.d("CameraX", "Camera bound OK");

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "startCamera error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCamera() {
        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
        } catch (Exception e) {
            Log.w("CameraX", "stopCamera() failed", e);
        }
        isCameraBound = false;
        isTakingPhoto = false;
        faceStraightStartTime = 0;
        try {
            if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        } catch (Exception ignored) {}
        imageCapture = null;
    }

    /** Callback từ FaceAnalyzer */
    @Override
    public void onFaceLookingStraight() {
        runOnUiThread(() -> {
            if (!isCameraBound || imageCapture == null) return;

            long now = System.currentTimeMillis();
            if (faceStraightStartTime == 0) {
                faceStraightStartTime = now;
                return;
            }
            long elapsed = now - faceStraightStartTime;

            // Chỉ chụp khi đã nhìn thẳng đủ STRAIGHT_FACE_DURATION và chưa có phiên chụp
            if (elapsed >= STRAIGHT_FACE_DURATION && !isTakingPhoto) {
                isTakingPhoto = true;
                takePhoto();
            }
        });
    }

    @Override
    public void onFaceNotLookingStraight() {
        runOnUiThread(() -> faceStraightStartTime = 0);
    }

    private void takePhoto() {
        if (imageCapture == null) {
            isTakingPhoto = false;
            return;
        }

        // Tạm dừng analyzer để tránh callback đè trong lúc chụp/upload
        try { if (imageAnalysis != null) imageAnalysis.clearAnalyzer(); } catch (Exception ignored) {}

        String filename = "IMG_" + System.currentTimeMillis() + ".jpg";
        File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            isTakingPhoto = false;
            resumeAnalyzer();
            return;
        }
        File file = new File(dir, filename);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(file).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        uploadImageToApi(file);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraX", "takePicture error", exception);
                        isTakingPhoto = false;
                        resumeAnalyzer();
                    }
                });
    }

    private void resumeAnalyzer() {
        // Bật lại analyzer nếu camera còn bound
        if (isCameraBound && imageAnalysis != null) {
            try {
                imageAnalysis.setAnalyzer(analysisExecutor,
                        new FaceAnalyzer(FaceRecognizeActivity.this, graphicOverlay, FaceRecognizeActivity.this));
            } catch (Exception ignored) {}
        }
    }

    private void uploadImageToApi(File file) {
        showLoading("Recognizing...");

        new Thread(() -> {
            Response response = null;
            try {
                // Đọc file bytes
                byte[] fileBytes;
                try (InputStream is = new FileInputStream(file);
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    byte[] data = new byte[16_384];
                    int nRead;
                    while ((nRead = is.read(data)) != -1) buffer.write(data, 0, nRead);
                    fileBytes = buffer.toByteArray();
                }

                String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date());

                // API chính
                RequestBody requestBodyPrimary = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("image_file", file.getName(),
                                RequestBody.create(fileBytes, MediaType.parse("image/jpeg")))
                        .build();

                Request requestPrimary = new Request.Builder()
                        .url("http://10.13.32.50:5001/recognize-anti-spoofing")
                        .addHeader("X-API-Key", "vg_login_app")
                        .addHeader("X-Time", currentTime)
                        .post(requestBodyPrimary)
                        .build();

                OkHttpClient clientWithTimeout = httpClient.newBuilder()
                        .callTimeout(5, TimeUnit.SECONDS)
                        .build();

                String responseBody;
                String apiUsed;

                try {
                    Log.d("API_CALL", "Calling PRIMARY API: 5001");
                    response = clientWithTimeout.newCall(requestPrimary).execute();
                    if (response.body() == null) throw new IllegalStateException("Empty body from Primary API");
                    responseBody = response.body().string();
                    apiUsed = "Primary";
                } catch (Exception ex) {
                    if (response != null) try { response.close(); } catch (Exception ignore) {}

                    // Fallback
                    RequestBody requestBodyFallback = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("env_token", "8d59d8d588f84fc0a24291b8c36b6206")
                            .addFormDataPart("image_file", file.getName(),
                                    RequestBody.create(file, MediaType.parse("image/jpeg")))
                            .build();

                    Request requestFallback = new Request.Builder()
                            .url("http://10.1.16.23:8001/api/x/fr/env/face_search")
                            .post(requestBodyFallback)
                            .build();

                    Log.d("API_CALL", "Calling FALLBACK API: 8001");
                    response = httpClient.newCall(requestFallback).execute();
                    if (response.body() == null) throw new IllegalStateException("Empty body from Fallback API");
                    responseBody = response.body().string();
                    apiUsed = "Fallback";
                }

                if (response == null || !response.isSuccessful()) {
                    handleRecognitionFail();
                    return;
                }

                JSONObject jsonObject = new JSONObject(responseBody);

                if (jsonObject.optBoolean("is_fake", false)) {
                    Log.w("AntiSpoofing", "Detected fake face (" + apiUsed + ")");
                    handleRecognitionFail();
                    return;
                }

                if (jsonObject.optInt("is_recognized", 0) == 1) {
                    String name   = jsonObject.optString("name");
                    String cardId = jsonObject.optString("id_string");
                    double similarityVal = jsonObject.optDouble("similarity", 0) * 100.0;

                    if (similarityVal <= 55) {
                        Log.w("Recognition", "Low similarity (" + similarityVal + "%) from " + apiUsed);
                        handleRecognitionFail();
                        return;
                    }

                    JSONObject person = new JSONObject();
                    person.put("cardId", cardId);
                    person.put("name", name);
                    verifiedPeople.add(person);

                    String similarity = String.format(Locale.getDefault(), "%.2f%%", similarityVal);

                    runOnUiThread(() -> {
                        hideLoading();
                        alertTextView.setVisibility(View.VISIBLE);
                        alertTextView.setText("Facial recognition successful");

                        labelName.setVisibility(View.VISIBLE);
                        nameTextView.setVisibility(View.VISIBLE);
                        labelCardId.setVisibility(View.VISIBLE);
                        cardIDTextView.setVisibility(View.VISIBLE);
                        labelSimilarity.setVisibility(View.VISIBLE);
                        similarityTextView.setVisibility(View.VISIBLE);

                        labelName.setText("Name:");
                        nameTextView.setText(name);
                        labelCardId.setText("Card ID:");
                        cardIDTextView.setText(cardId);
                        labelSimilarity.setText("Similarity:");
                        similarityTextView.setText(similarity);

                        userInfoPanel.setVisibility(View.VISIBLE);
                        userInfoPanel.postDelayed(() -> userInfoPanel.setVisibility(View.GONE), 3000);
                    });
                } else {
                    runOnUiThread(() -> alertTextView.setText("Face not recognized"));
                    handleRecognitionFail();
                }

            } catch (Exception e) {
                Log.e("FaceAPI_Error", String.valueOf(e.getMessage()), e);
                showToastOnMainThread("Error: " + e.getMessage());
                handleRecognitionFail();
            } finally {
                if (response != null) try { response.close(); } catch (Exception ignore) {}
                runOnUiThread(() -> {
                    isTakingPhoto = false;
                    // KHÔNG rebind camera tùy tiện; chỉ bật lại analyzer nếu vẫn đang bound
                    resumeAnalyzer();
                });
            }
        }).start();
    }

    private void handleRecognitionFail() {
        runOnUiThread(() -> {
            hideLoading();
            userInfoPanel.setVisibility(View.VISIBLE);

            alertTextView.setVisibility(View.VISIBLE);
            alertTextView.setText("Facial recognition failed");

            labelName.setVisibility(View.GONE);
            nameTextView.setVisibility(View.GONE);
            labelCardId.setVisibility(View.GONE);
            cardIDTextView.setVisibility(View.GONE);
            labelSimilarity.setVisibility(View.GONE);
            similarityTextView.setVisibility(View.GONE);

            loadingContainer.setVisibility(View.GONE);

            alertTextView.postDelayed(() -> alertTextView.setText(""), 2000);
        });
    }

    private void showToastOnMainThread(String message) {
        runOnUiThread(() -> Toast.makeText(FaceRecognizeActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private void showLoading(String message) {
        runOnUiThread(() -> {
            userInfoPanel.setVisibility(View.VISIBLE);
            loadingContainer.setVisibility(View.VISIBLE);
            loadingSpinner.setVisibility(View.VISIBLE);

            alertTextView.setVisibility(View.VISIBLE);
            alertTextView.setText(message != null ? message : "Recognizing...");

            labelName.setVisibility(View.GONE);
            nameTextView.setVisibility(View.GONE);
            labelCardId.setVisibility(View.GONE);
            cardIDTextView.setVisibility(View.GONE);
            labelSimilarity.setVisibility(View.GONE);
            similarityTextView.setVisibility(View.GONE);
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            loadingSpinner.setVisibility(View.GONE);
            loadingContainer.setVisibility(View.GONE);
        });
    }

    private void showImageDialog(String imagePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            Toast.makeText(this, "Không thể tải ảnh để hiển thị", Toast.LENGTH_SHORT).show();
            isTakingPhoto = false;
            resumeAnalyzer();
            return;
        }

        bitmap = rotateBitmapIfRequired(imagePath, bitmap);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ảnh đã chụp")
                .setView(imageView)
                .setPositiveButton("Đóng", (d, which) -> {
                    d.dismiss();
                    resumeAnalyzer();
                })
                .setCancelable(false)
                .create();

        dialog.show();
    }

    private Bitmap rotateBitmapIfRequired(String imagePath, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            int rotationDegrees = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  rotationDegrees = 90;  break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotationDegrees = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotationDegrees = 270; break;
                default: return bitmap;
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return rotatedBitmap;
        } catch (Exception e) {
            Log.e("Rotate", "rotateBitmapIfRequired error", e);
            return bitmap;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    public boolean isInternetAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                return capabilities != null &&
                        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        }
        return false;
    }
}
