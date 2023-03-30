package com.example.lazar_android_app;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GameActivity extends AppCompatActivity {

    public static final int OUTPUT_IMAGE_FORMAT_RGBA_8888 = 2;

    private boolean DEBUG = true;

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

    public void fireLazar(View view) {
        // grab image from mPreviewView and do human detection on it, bozo
        Bitmap captureBmp = mPreviewView.getBitmap();
        if (DEBUG) {
            ImageView captureView = findViewById(R.id.capture);
            captureView.setImageBitmap(captureBmp);
        }
        // TO-DO: double check how the orientation is grabbed
        int imageOrientation = mPreviewView.getDeviceRotationForRemoteDisplayMode();
        if (DetectPerson(captureBmp, imageOrientation)) {
            fireButton.setBackgroundColor(Color.GREEN);
        }
        else {
            fireButton.setBackgroundColor(Color.RED);
        }

        healthBar.setProgress(healthBar.getProgress() - 10);
    }

    // Returns true if a person was detected in the given bitmap
    public boolean DetectPerson(Bitmap bitmap, int orientation) {
        if (objectDetector.detect(bitmap, orientation)) {
            Log.w("DETECTION OF PERSON", "A person was detected in the bitmap");
            return true;
        };
        return false;
    }


}