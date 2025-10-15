package com.vg.restrictacesscontrol.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.face.Face;
import com.vg.restrictacesscontrol.R;

import java.util.List;

public class FaceGraphicOverlay extends View {
    private List<Face> faces;
    private boolean isFrontCamera = true;

    public FaceGraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCameraFacing(boolean isFrontCamera) {
        this.isFrontCamera = isFrontCamera;
    }

    public void setFaces(List<Face> faces) {
        this.faces = faces;
        postInvalidate(); // vẽ lại
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (faces == null || faces.isEmpty()) {
            return;
        }
        int recognizeThresholdArea = getContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getInt("recognize_threshold", 10000);
        for (Face face : faces) {
            Rect bounds = face.getBoundingBox();

            boolean isLookingStraight =
                    Math.abs(face.getHeadEulerAngleY()) < 10 &&
                            Math.abs(face.getHeadEulerAngleZ()) < 10;
            int faceArea = bounds.width() * bounds.height();
            boolean isFaceCloseEnough = faceArea > recognizeThresholdArea;
            boolean isRecognizable = isLookingStraight && isFaceCloseEnough;
            Paint paint = new Paint();
            paint.setColor(isRecognizable  ? Color.GREEN : Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f);

            // Tỉ lệ vẽ khung theo kích thước view
            float scaleX = (float) getWidth() / 480f;
            float scaleY = (float) getHeight() / 640f;

            float left = bounds.left * scaleX;
            float top = bounds.top * scaleY;
            float right = bounds.right * scaleX;
            float bottom = bounds.bottom * scaleY;

            if (isFrontCamera) {
                float centerX = getWidth() / 2f;
                float newLeft = 2 * centerX - right;
                float newRight = 2 * centerX - left;
                left = newLeft;
                right = newRight;
            }

            canvas.drawRect(left, top, right, bottom, paint);
            if (isRecognizable) {
                Paint textPaint = new Paint();
                textPaint.setColor(Color.GREEN);
                textPaint.setTextSize(40f);
                textPaint.setStyle(Paint.Style.FILL);
                textPaint.setAntiAlias(true);
                canvas.drawText(getResources().getString(R.string.recognizing),
                        left, top - 10, textPaint);

            }
        }
    }
}
