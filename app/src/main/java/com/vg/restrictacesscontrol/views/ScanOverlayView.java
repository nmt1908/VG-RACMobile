package com.vg.restrictacesscontrol.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class ScanOverlayView extends View {
    private final Paint maskPaint = new Paint();
    private final Paint clearPaint = new Paint();
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF frame = new RectF();
    private float lineY;
    private ValueAnimator animator;
    private boolean scanning = false;

    // ==== Tùy chỉnh khung ====
    private float frameMarginH = 32f;       // lề trong khung
    private float cornerLen    = 24f;       // độ dài góc chữ L
    private float cornerStroke = 5f;        // độ dày góc
    private float frameRadius  = 12f;       // bo góc khung
    private float lineStroke   = 3.5f;      // độ dày line
    private float targetWidthRatio = 0.60f;
    private float maxHeightRatio  = 0.50f;

    public ScanOverlayView(Context c) { super(c); init(); }
    public ScanOverlayView(Context c, AttributeSet a) { super(c, a); init(); }
    public ScanOverlayView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        maskPaint.setColor(Color.parseColor("#CC000000"));
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(cornerStroke);
        cornerPaint.setStrokeCap(Paint.Cap.SQUARE);
        linePaint.setColor(Color.parseColor("#E04D25"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(lineStroke);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float maxByWidth  = w * targetWidthRatio - 2 * frameMarginH;
        float maxByHeight = h * maxHeightRatio;
        float size = Math.min(maxByWidth, maxByHeight);

        float cx = w / 2f;
        float cy = h / 2f;

        float left   = cx - size / 2f;
        float right  = cx + size / 2f;
        float top    = cy - size / 2f;
        float bottom = cy + size / 2f;

        frame.set(left, top, right, bottom);
        resetAnimator();
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        Path r = new Path();
        r.addRoundRect(frame, frameRadius, frameRadius, Path.Direction.CW);
        c.drawPath(r, clearPaint);
        float l = frame.left, t = frame.top, rgt = frame.right, btm = frame.bottom;
        c.drawLine(l, t, l + cornerLen, t, cornerPaint);
        c.drawLine(l, t, l, t + cornerLen, cornerPaint);
        c.drawLine(rgt, t, rgt - cornerLen, t, cornerPaint);
        c.drawLine(rgt, t, rgt, t + cornerLen, cornerPaint);
        c.drawLine(l, btm, l + cornerLen, btm, cornerPaint);
        c.drawLine(l, btm, l, btm - cornerLen, cornerPaint);
        c.drawLine(rgt, btm, rgt - cornerLen, btm, cornerPaint);
        c.drawLine(rgt, btm, rgt, btm - cornerLen, cornerPaint);

        // 4) Line quét
        if (scanning) {
            c.drawLine(frame.left + 8, lineY, frame.right - 8, lineY, linePaint);
        }
    }

    private void resetAnimator() {
        if (animator != null) animator.cancel();
        lineY = frame.top + 10;
        animator = ValueAnimator.ofFloat(frame.top + 10, frame.bottom - 10);
        animator.setDuration(1500);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(a -> {
            lineY = (float) a.getAnimatedValue();
            invalidate();
        });
        if (scanning) animator.start();
    }

    public void start() {
        scanning = true;
        if (animator == null) resetAnimator();
        if (!animator.isRunning()) animator.start();
        invalidate();
    }

    public void stop() {
        scanning = false;
        if (animator != null) animator.cancel();
        invalidate();
    }

    public RectF getFrame() { return new RectF(frame); }
    public boolean isScanning() { return scanning; }
}
