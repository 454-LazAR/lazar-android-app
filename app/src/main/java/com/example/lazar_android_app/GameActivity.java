package com.example.lazar_android_app;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import kotlin.Pair;

public class GameActivity extends AppCompatActivity {

    public static final int OUTPUT_IMAGE_FORMAT_RGBA_8888 = 2;

    private boolean DEBUG = true;
    private float minConfidence = (float) 0.6;

    private ObjectDetectorHelper objectDetector;

    private Executor executor = Executors.newSingleThreadExecutor();
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};

    Camera camera;
    PreviewView mPreviewView;
    ImageView crosshair;
    ProgressBar healthBar;
    Button fireButton;

    /**
     * During the create function, we boot up the layout and scale & set the health bar to 100.
     * Next, permission to use the camera is asked to the user.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        if (DEBUG) {
            // makes the capture ImageView visible
            findViewById(R.id.capture).setVisibility(View.VISIBLE);
        }

        mPreviewView = findViewById(R.id.camera);
        crosshair = findViewById(R.id.crosshair);
        healthBar = findViewById(R.id.healthBar);
        fireButton = findViewById(R.id.fireButton);
        int min = healthBar.getMin();
        int max = healthBar.getMax();
        healthBar.setProgress(100);
        healthBar.setScaleY(8f);

        // Get location of crosshair
        //crosshairX = getResources().getDisplayMetrics().widthPixels / 2;
        //crosshairY = getResources().getDisplayMetrics().heightPixels / 2;

        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    /**
     * This function starts the camera view after permissions have been granted.
     */
    private void startCamera() {

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {

                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);

                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Binds the ProcessCameraProvider object to a view, so it can be displayed on the UI.
     *
     * @param cameraProvider passed during startCamera()
     */
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();


        ImageCapture.Builder builder = new ImageCapture.Builder();

        final ImageCapture imageCapture = builder
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .build();
        preview.setSurfaceProvider(mPreviewView.createSurfaceProvider());
        camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis, imageCapture);

        // initialize object detector
        objectDetector = new ObjectDetectorHelper(
                0.5f,
                2,
                3,
                2,
                0,
                this,
                null);
    }

    /**
     * Boolean check if all permissions in {@link #REQUIRED_PERMISSIONS} are allowed
     *
     * @return true if all permissions in {@link #REQUIRED_PERMISSIONS} are allowed, false
     * otherwise.
     */
    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Interface implementation
     *
     * @param requestCode The request code passed.
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
     *     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    /**
     * Onclick handler for the FIRE button.
     *
     * @param view
     */
    public void fireLazar(View view) {
        // grab image from mPreviewView and do human detection on it, bozo
        Bitmap captureBmp = mPreviewView.getBitmap();
        // TO-DO: double check how the orientation is grabbed
        int imageOrientation = mPreviewView.getDeviceRotationForRemoteDisplayMode();
        if (DetectPerson(captureBmp, imageOrientation)) {
            fireButton.setTextColor(Color.GREEN);
        }
        else {
            fireButton.setTextColor(Color.BLACK);
        }

        healthBar.setProgress(healthBar.getProgress() - 10);
    }

    /**
     * Given a Bitmap, this function uses the ObjectDetectorHelper class to determine if a person is
     * in the image, and if that person is within the crosshairs in the app (aka: the center X and Y
     * coordinates of the image). If both conditions are true, returns true to indicate a person has
     * been hit!
     *
     * In DEBUG mode, draws GREEN bounding boxes around people detected who have score greater than
     * minConfidence and overlap their bounding boxes with the center of the image (the crosshair).
     * Draws RED bounding boxes around people detected who don't meet at least one of those
     * conditions.
     *
     * @param bitmap The image passed in for person detection.
     * @param orientation Orientation of the image for reorientation before detection.
     * @return True if at least one person was detected in the given bitmap overlapping with the
     * center of the crosshair, false otherwise.
     */
    public boolean DetectPerson(Bitmap bitmap, int orientation) {
        ArrayList<Pair<RectF, Float>> personDetections = objectDetector.detect(bitmap, orientation);

        int chX = bitmap.getWidth()/2;
        int chY = bitmap.getHeight()/2;

        ArrayList<Pair<RectF, Float>> hitPersons = new ArrayList<>();

        Bitmap tempBitmap = null;
        Canvas canvas = null;

        if (DEBUG) {
            tempBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(), true);
            canvas = new Canvas(tempBitmap);
        }

        for (Pair<RectF, Float> person : personDetections) {
            if (DEBUG) {
                Log.w("BOUNDING BOX COORDINATES", person.getFirst().toString());
                Log.w("CONFIDENCE SCORE", person.getSecond().toString());

                // draw bounding boxes for the mini-image!
                Paint p = new Paint();
                p.setStyle(Style.FILL_AND_STROKE);
                p.setAntiAlias(true);
                p.setFilterBitmap(true);
                p.setDither(true);
                p.setStrokeWidth(5);
                if (PointInRectF(person.getFirst(), chX, chY) /*&& person.getSecond() > minConfidence*/)
                    p.setColor(Color.GREEN);
                else
                    p.setColor((Color.RED));

                float x1 = person.getFirst().left;
                float x2 = person.getFirst().right;
                float y1 = person.getFirst().top;
                float y2 = person.getFirst().bottom;

                canvas.drawLine(x1, y1, x2, y1, p); //top
                canvas.drawLine(x1, y1, x1, y2, p); //left
                canvas.drawLine(x1, y2, x2, y2, p); //bottom
                canvas.drawLine(x2, y1, x2, y2, p); //right
            }

            // add person to "hitPersons" list if confidence is >0.6 and they semi-overlap the
            // crosshair on the screen
            if (PointInRectF(person.getFirst(), chX, chY) && person.getSecond() > minConfidence) hitPersons.add(person);
        }

        if (DEBUG) {
            ImageView captureView = findViewById(R.id.capture);
            captureView.setImageBitmap(tempBitmap);
        }

        return hitPersons.size() > 0;
    }

    /**
     * This method checks if an arbitrary point defined by x and y coordinates is within the edges
     * of a RectF bounding box and returns true if it is, false otherwise.
     *
     * @param bounds RectF object that contains the coordinates of the bounding box.
     * @param x X-coordinate of an arbitrary point.
     * @param y Y-coordinate of an arbitrary point.
     * @return True if the arbitrary point is within the edges of the bounding box, false otherwise.
     */
    public boolean PointInRectF(RectF bounds, int x, int y) {
        // left to right = less --> more
        // top to bottom = less --> more
        if (x > bounds.left && x < bounds.right &&
            y > bounds.top && y < bounds.bottom) {
            // X/Y coords are completely within the RectF boundary box
            return true;
        }

        // at least one of the conditions isn't true, so it's not within the boundary box
        return false;
    }
}