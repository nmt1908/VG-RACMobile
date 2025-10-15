package com.vg.restrictacesscontrol.overlay;

import android.content.Context;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;

public class FaceAnalyzer implements ImageAnalysis.Analyzer {

    public interface FaceDetectionListener {
        void onFaceLookingStraight();
        void onFaceNotLookingStraight();

    }

    private final FaceGraphicOverlay overlay;
    private final FaceDetectionListener listener;
    private final FaceDetector detector;
    private final Context context;

    public FaceAnalyzer(Context context,FaceGraphicOverlay overlay, FaceDetectionListener listener) {
        this.context = context;
        this.overlay = overlay;
        this.listener = listener;

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();

        detector = FaceDetection.getClient(options);
    }


    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        int detectThreshold = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getInt("detect_threshold", 3000);

        int recognizeThreshold = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getInt("recognize_threshold", 3000);
        Log.e("detect_threshold", String.valueOf(detectThreshold));
        Log.e("recognize_threshold",String.valueOf(recognizeThreshold));
        Image mediaImage = imageProxy.getImage();

        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (overlay != null) {
                        List<Face> filteredFaces = new java.util.ArrayList<>();
                        for (Face face : faces) {
                            float faceArea = face.getBoundingBox().width() * face.getBoundingBox().height();
                            if (faceArea > detectThreshold) {
                                filteredFaces.add(face);
                            }
                        }
                        overlay.setFaces(filteredFaces);
                    }
                    boolean foundStraightAndCloseFace = false;
                    boolean foundLargeFace = false;

                    for (Face face : faces) {
                        float rotY = face.getHeadEulerAngleY();
                        float rotZ = face.getHeadEulerAngleZ();

                        float faceArea = face.getBoundingBox().width() * face.getBoundingBox().height();

                        // Kiểm tra mặt lớn hơn 20000 (để bật preview UI)
                        if (faceArea > detectThreshold) {
                            foundLargeFace = true;
                        }

                        // Kiểm tra mặt nhìn thẳng và diện tích > 40000 (để nhận diện + chụp ảnh)
                        if (Math.abs(rotY) < 10 && Math.abs(rotZ) < 10 && faceArea > recognizeThreshold) {
                            foundStraightAndCloseFace = true;
                        }
                    }



                    // Gọi callback nhận diện mặt nhìn thẳng >40000
                    if (foundStraightAndCloseFace) {
                        listener.onFaceLookingStraight();
                    } else {
                        listener.onFaceNotLookingStraight();
                    }
                })
                .addOnFailureListener(e -> {
                    // Log lỗi nếu cần
                })
                .addOnCompleteListener(task -> {
                    imageProxy.close();
                });
    }

}
