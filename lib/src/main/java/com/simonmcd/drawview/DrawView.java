package com.simonmcd.drawview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A View that allows the user to draw on a surface.
 */
public class DrawView extends View {

    /**
     * Constants used for Bundle keys.
     */
    private static final String EXTRA_STATE = "extraState";
    private static final String EXTRA_EVENT_LIST = "eventList";
    private static final String EXTRA_COLOUR_LIST = "colourList";

    /**
     * Drawing tools.
     */
    private Path drawPath;
    private Paint drawPaint;
    private Paint canvasPaint;

    /**
     * Canvas elements.
     */
    private Canvas drawCanvas;
    private Bitmap canvasBitmap;

    /**
     * Shake elements.
     */
    private boolean isShaking = false;
    private float xAccel;
    private float yAccel;
    private float zAccel;

    /* And here the previous ones */
    private float xPreviousAccel;
    private float yPreviousAccel;
    private float zPreviousAccel;

    /* Used to suppress the first shaking */
    private boolean firstUpdate = true;

    /*What acceleration difference would we assume as a rapid movement? */
    private final static float SHAKE_THRESHOLD = 10f;

    /**
     * Lists for holding data about the history of the drawing.
     */
    private ArrayList<MotionEvent> eventList = new ArrayList<MotionEvent>();
    private ArrayList<Integer> colourList = new ArrayList<Integer>();

    private SensorManager sensorManager;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {

        public void onSensorChanged(SensorEvent se) {
            updateAccelParameters(se.values[0], se.values[1], se.values[2]);
            if ((!isShaking) && isAccelerationChanged()) {
                isShaking = true;
            } else if ((isShaking) && isAccelerationChanged()) {
                clear();
            } else if ((isShaking) && (!isAccelerationChanged())) {
                isShaking = false;
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
	    /* can be ignored in this example */
        }
    };

    public DrawView(Context context) {
        super(context);
        init();
    }

    public DrawView(Context context, AttributeSet attrs){
        super(context, attrs);
        init();
        setupAttributes(attrs);
    }

    private void setupAttributes(AttributeSet attrs) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.DrawView,
                0, 0);

        try {
            final int colour = a.getColor(R.styleable.DrawView_brushColour, 0);
            if (colour != 0) {
                drawPaint.setColor(colour);
            }
        } finally {
            a.recycle();
        }
    }

    private void init() {
        setSaveEnabled(true);
        drawPath = new Path();
        drawPaint = new Paint();
        drawPaint.setColor(Color.BLACK);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(20);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        canvasPaint = new Paint(Paint.DITHER_FLAG);
    }

    @Override
    public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        // Draw canvas.
        canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);

        // Set background colour.
        final Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);

        // Draw the background on the canvas.
        drawCanvas.drawRect(0, 0, width, height, backgroundPaint);

        // If we have an existing eventList we have probably just rotated the screen and need to
        // recreate the drawing.
        if (eventList != null) {
            final List<MotionEvent> tempEventList = new ArrayList<MotionEvent>();
            final List<Integer> tempColourList = new ArrayList<Integer>();
            final int size = eventList.size();
            for (int i = 0; i < size; i++) {
                tempEventList.add(eventList.get(i));
                tempColourList.add(colourList.get(i));

            }
            eventList.clear();
            colourList.clear();

            // Redraw any recorded strokes.
            for (int i = 0; i < size; i++) {
                drawPaint.setColor(tempColourList.get(i));
                onTouchEvent(tempEventList.get(i));
            }
        }
    }

    /**
     * Implement this method to handle touch screen motion events.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise.
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final float touchX = event.getX();
        final float touchY = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // If the user has touched the screen set the start point of the path.
                drawPath.moveTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_MOVE:
                // If the user has moved their finger connect from the last point in the path.
                drawPath.lineTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_UP:
                // When the user has stopped touching the screen draw the path to the canvas and
                // reset the path for the next touch event.
                drawCanvas.drawPath(drawPath, drawPaint);
                drawPath.reset();
                break;
            default:
                return false;
        }
        // Record both the touch and the colour selected at the time of the touch for recreation
        // if the View is destroyed.
        eventList.add(MotionEvent.obtain(event));
        colourList.add(drawPaint.getColor());

        // Invalidate the View so it is redrawn.
        invalidate();
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
    }

    /**
     * Sets the colour of the brush.
     * @param colour the colour of the brush.
     */
    public void setPaintColour(final int colour) {
        this.drawPaint.setColor(colour);
    }

    /**
     * Gets the colour of the brush.
     * @return the colour of the brush or 0 if the brush is null.
     */
    public int getPaintColour() {
        return drawPaint == null ? 0 : drawPaint.getColor();
    }

    /**
     * Save the current image as a Bitmap.
     * @return the image drawn on the canvas.
     */
    public Bitmap saveDrawing() {
        return this.canvasBitmap;
    }

    /**
     * Hook allowing a view to generate a representation of its internal state
     * that can later be used to create a new instance with that same state.
     * This state should only contain information that is not persistent or can
     * not be reconstructed later. For example, you will never store your
     * current position on screen because that will be computed again when a
     * new instance of the view is placed in its view hierarchy.
     * <p/>
     * Some examples of things you may store here: the current cursor position
     * in a text view (but usually not the text itself since that is stored in a
     * content provider or other persistent storage), the currently selected
     * item in a list view.
     *
     * @return Returns a Parcelable object containing the view's current dynamic
     * state, or null if there is nothing interesting to save. The
     * default implementation returns null.
     * @see #onRestoreInstanceState(android.os.Parcelable)
     * @see #saveHierarchyState(android.util.SparseArray)
     * @see #dispatchSaveInstanceState(android.util.SparseArray)
     * @see #setSaveEnabled(boolean)
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_STATE, super.onSaveInstanceState());
        bundle.putParcelableArrayList(EXTRA_EVENT_LIST, eventList);
        bundle.putIntegerArrayList(EXTRA_COLOUR_LIST, colourList);
        return bundle;
    }

    /**
     * Hook allowing a view to re-apply a representation of its internal state that had previously
     * been generated by {@link #onSaveInstanceState}. This function will never be called with a
     * null state.
     *
     * @param state The frozen state that had previously been returned by
     *              {@link #onSaveInstanceState}.
     * @see #onSaveInstanceState()
     * @see #restoreHierarchyState(android.util.SparseArray)
     * @see #dispatchRestoreInstanceState(android.util.SparseArray)
     */
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(bundle.getParcelable(EXTRA_STATE));
            eventList = bundle.getParcelableArrayList(EXTRA_EVENT_LIST);
            colourList = bundle.getIntegerArrayList(EXTRA_COLOUR_LIST);
            if (eventList == null) {
                eventList = new ArrayList<MotionEvent>();
            }
            if (colourList == null) {
                colourList = new ArrayList<Integer>();
            }
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    /**
     * Remove all paint from the canvas.
     */
    public void clear() {
        // Clear the canvas.
        this.drawCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

        // Clear the lists so if the View is destroyed we do not recreate the cleared image.
        this.eventList.clear();
        this.colourList.clear();

        // Invalidate the View so it is redrawn.
        invalidate();
    }

    /**
     * Sets shake to clear mode. This feature uses the accelerometer to detect when the user
     * shakes the device. If enabled, a device shake will clear the drawn Bitmap.
     * @param enabled if true, shaking will clear the Bitmap. If false, shaking will not.
     */
    public void enableShakeToClear(final boolean enabled) {
        if (sensorManager == null) {
            sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        }
        if (enabled) {
            sensorManager.registerListener(sensorEventListener, sensorManager
                            .getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            sensorManager.unregisterListener(sensorEventListener);
        }
    }

    /**
     * Store the acceleration values given by the sensor
     * */
    private void updateAccelParameters(float xNewAccel, float yNewAccel,
                                       float zNewAccel) {
        // Ignore first shake.
        if (firstUpdate) {
            xPreviousAccel = xNewAccel;
            yPreviousAccel = yNewAccel;
            zPreviousAccel = zNewAccel;
            firstUpdate = false;
        } else {
            xPreviousAccel = xAccel;
            yPreviousAccel = yAccel;
            zPreviousAccel = zAccel;
        }
        xAccel = xNewAccel;
        yAccel = yNewAccel;
        zAccel = zNewAccel;
    }

    /**
     * If the values of acceleration have changed on at least two axises,
     * we are probably in a shake motion
     * */
    private boolean isAccelerationChanged() {
        float deltaX = Math.abs(xPreviousAccel - xAccel);
        float deltaY = Math.abs(yPreviousAccel - yAccel);
        float deltaZ = Math.abs(zPreviousAccel - zAccel);
        return (deltaX > SHAKE_THRESHOLD && deltaY > SHAKE_THRESHOLD)
                || (deltaX > SHAKE_THRESHOLD && deltaZ > SHAKE_THRESHOLD)
                || (deltaY > SHAKE_THRESHOLD && deltaZ > SHAKE_THRESHOLD);
    }
}