package com.example.lazar_android_app;

import static com.example.lazar_android_app.HomeActivity.URL;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import kotlin.Pair;

public class GameActivity extends AppCompatActivity implements SensorEventListener {

    private final boolean DEBUG = true;
    private boolean ZOOMED = false;
    private String _userId;
    private String _gameStatus;
    private int _health;
    private Double _longitude;
    private Double _latitude;
    private float _bearing;
    private SensorManager compassSensorManager;
    private ObjectDetectorHelper objectDetector;
    private final float minConfidence = (float) 0.6;
    private final float zoomRatio = 4.0f;

    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"};
    private final int REQUEST_CODE_PERMISSIONS = 1001;
    private Handler gameHandler;
    private Runnable gameRunnable;
    private RequestQueue queue;
    private FusedLocationProviderClient fusedLocationProviderClient;

    Camera camera;
    CameraControl cameraControl;
    PreviewView mPreviewView;
    ProgressBar healthBar;
    Button fireButton;
    Button zoomButton;
    TextView latView;
    TextView longView;
    TextView bearView;

    @Override
    public void onBackPressed() {
        returnHome(null);
    }

    /**
     * During the create function, we boot up the layout and scale & set the health bar to 100.
     * Next, permission to use the camera and location services is asked to the user.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     */
    // Permission check is performed in allPermissionGranted() but not directly in onCreate(), so
    // Java thinks we're not doing a permission check and gets scared. This suppression fixes that.
    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        queue = Volley.newRequestQueue(this);

        // Decompile extras
        Bundle extras = getIntent().getExtras();
        _userId = extras.getString("userId");

        if (DEBUG) {
            // makes the capture ImageView visible
            findViewById(R.id.debugData).setVisibility(View.VISIBLE);
        }

        // set up view
        mPreviewView = findViewById(R.id.camera);
        healthBar = findViewById(R.id.healthBar);
        healthBar.setProgress(100);
        healthBar.setScaleY(8f);
        fireButton = findViewById(R.id.fireButton);
        zoomButton = findViewById(R.id.zoomButton);
        zoomButton.setBackgroundColor(Color.BLUE);
        fireButton.setBackgroundColor(Color.RED);
        latView = findViewById(R.id.latView);
        longView = findViewById(R.id.longView);
        bearView = findViewById(R.id.bearView);

        // set up sensor and its listener
        compassSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        compassSensorManager.registerListener(this,
                compassSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);


        if (allPermissionsGranted()) {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

            // Initialize the camera
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Define async thread to run game pings
        gameHandler = new Handler();
        gameRunnable = new Runnable() {
            @Override
            public void run() {
                if(_latitude == null || _longitude == null) {
                    gameHandler.postDelayed(this, 1000);
                    return;
                }
                // Do your background task here
                JSONObject body = new JSONObject();
                try {
                    body.put("playerId", _userId);
                    body.put("latitude", _latitude);
                    body.put("longitude", _longitude);
                    body.put("timestamp", java.time.Instant.now());
                    queue.add(getGamePingRequest(body));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                gameHandler.postDelayed(this, 1000); // Schedule the task to run again after 1 second
            }
        };

        // Start the async game ping thread
        startGamePing();
    }

    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult != null) {
                Location location = locationResult.getLastLocation();
                _latitude = location.getLatitude();
                _longitude = location.getLongitude();

                if (DEBUG) {
                    latView.setText("Latitude: " + _latitude);
                    longView.setText("Longitude: " + _longitude);
                }
            }
        }
    };

    /**
     * Call this method to begin location updates. This thread should run at a quicker frequency
     * than the game ping.
     *
     */
    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(500); // Update location every 500 ms
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    /**
     * Call this to stop location updates. (game over)
     */
    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    /**
     * Run this to start the async game ping thread!
     */
    private void startGamePing() {
        startLocationUpdates();
        gameHandler.post(gameRunnable);
    }

    /**
     * Run this to stop the async game ping thread!
     */
    private void stopGamePing() {
        queue.cancelAll(request -> true);
        stopLocationUpdates();
        gameHandler.removeCallbacks(gameRunnable);
    }

    /**
     * This function starts the camera view after permissions have been granted.
     */
    private void startCamera() {

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {

                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);

            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Binds the ProcessCameraProvider object to a view, so it can be displayed on the UI.
     *
     * @param cameraProvider passed from startCamera()
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
        cameraControl = camera.getCameraControl();

        // initialize object detector
        objectDetector = new ObjectDetectorHelper(
                0.5f,
                2,
                5,
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
            if (DEBUG) {
                fireButton.setBackgroundColor(Color.GREEN);
            }
            JSONObject body = new JSONObject();
            try {
                body.put("playerId", _userId);
                body.put("timestamp", java.time.Instant.now());
                body.put("latitude", _latitude);
                body.put("longitude", _longitude);
                body.put("heading", _bearing);
                queue.add(getCheckHitRequest(body));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            fireButton.setBackgroundColor(Color.RED);
        }
    }

    /**
     * Onclick handler for the ZOOM button.
     *
     * @param view
     */
    public void zoomInCamera(View view) {
        // zoom in OR zoom out the camera
        if (!ZOOMED) {
            cameraControl.setZoomRatio(zoomRatio);
            ZOOMED = true;
            zoomButton.setText("UNZOOM");
        }
        else {
            cameraControl.setZoomRatio(1.0f);
            ZOOMED = false;
            zoomButton.setText("ZOOM");
        }


    }

    /**
     * Given a Bitmap, this function uses the {@link ObjectDetectorHelper} to determine if a person
     * is in the image, and if that person is within the crosshairs in the app (aka: the center X
     * and Y coordinates of the image). If both conditions are true, returns true to indicate a
     * person has been hit!
     * <br><br>
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            float[] orientationAngles = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            // The orientationAngles array contains the azimuth, pitch, and roll angles
            float bearingRad = orientationAngles[0];
            _bearing = (float) Math.toDegrees(bearingRad);

            if(_bearing < 0) {
                _bearing += 360;
            }

            // Update the UI with the new orientation values
            bearView.setText("Bearing: " + _bearing);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // who cares?
    }

    private JsonObjectRequest getGamePingRequest(JSONObject requestBody) {
        return new JsonObjectRequest(Request.Method.POST, URL + "/game-ping", requestBody,
            response -> {
                try {
                    _gameStatus = response.getString("gameStatus");
                    _health = response.getInt("health");

                    if (_gameStatus.equals("FINISHED") && _health > 0) {
                        // VICTORY
                        victoryScreen();
                    }
                    else if (_health <= 0) {
                        // GAME OVER
                        lossScreen();
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                healthBar.setProgress(_health);
            }, error -> {
                if (error.networkResponse.statusCode == 400) {
                    Toast toast = Toast.makeText(getApplicationContext(), "Error pinging server -- Bad Request", Toast.LENGTH_LONG);
                    toast.show();
                    returnHome(null);
                }
                else if (error.networkResponse.statusCode == 404) {
                    Toast toast = Toast.makeText(getApplicationContext(), "Error pinging server -- Game doesn't exist", Toast.LENGTH_LONG);
                    toast.show();
                    returnHome(null);
                }
                else if (error.networkResponse.statusCode == 500) {
                    Toast toast = Toast.makeText(getApplicationContext(), "Error pinging server -- Internal Server Error", Toast.LENGTH_LONG);
                    toast.show();
                    returnHome(null);
                }
                // this should never happen
                else {
                    Toast toast = Toast.makeText(getApplicationContext(), "An unexpected error occurred while pinging the game server", Toast.LENGTH_LONG);
                    toast.show();
                    returnHome(null);
                }
            }
        );
    }

    private StringRequest getCheckHitRequest(JSONObject requestBody) {
        return new StringRequest(Request.Method.POST, URL + "/check-hit",
                response -> {
                    if (Boolean.valueOf(response)) {
                        if (DEBUG) {
                            fireButton.setBackgroundColor(Color.MAGENTA);
                        }

                        // temporarily display hitmarker! (hide it after 0.25 seconds)
                        findViewById(R.id.hitmarker).setVisibility(View.VISIBLE);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Do something here after waiting for 0.25 seconds
                                findViewById(R.id.hitmarker).setVisibility(View.GONE);
                            }
                        }, 250); // 250 milliseconds = 0.25 seconds

                    }
                }, error -> {
                    if (error.networkResponse.statusCode == 400) {
                        Toast toast = Toast.makeText(getApplicationContext(), "Error checking hit -- Bad Request", Toast.LENGTH_LONG);
                        toast.show();
                        returnHome(null);
                    }
                    else if (error.networkResponse.statusCode == 404) {
                        Toast toast = Toast.makeText(getApplicationContext(), "Error checking hit -- Game doesn't exist", Toast.LENGTH_LONG);
                        toast.show();
                        returnHome(null);
                    }
                    else if (error.networkResponse.statusCode == 500) {
                        Toast toast = Toast.makeText(getApplicationContext(), "Error checking hit -- Internal Server Error", Toast.LENGTH_LONG);
                        toast.show();
                        returnHome(null);
                    }
                    // this should never happen
                    else {
                        Toast toast = Toast.makeText(getApplicationContext(), "An unexpected error occurred while getting a hitcheck from the server", Toast.LENGTH_LONG);
                        toast.show();
                        returnHome(null);
                    }
        }) {
            @Override
            public byte[] getBody() {
                return requestBody.toString().getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json";
            }
        };
    }

    /**
     * This method displays the victory screen.
     * TODO: Display stats such as damage dealt, damage taken, eliminations, etc.
     */
    public void victoryScreen(){
        stopGamePing();

        ImageView skyPopup = findViewById(R.id.skyBackground);

        skyPopup.setVisibility(View.VISIBLE);

        ImageView victoryPopup = findViewById(R.id.winScreen);
        victoryPopup.setVisibility(View.VISIBLE);

        Button exitButton = findViewById(R.id.exitButton);
        Button fireButton = findViewById(R.id.fireButton);
        exitButton.setText("YOU WIN");
        fireButton.setVisibility(View.GONE);
        exitButton.setVisibility(View.VISIBLE);

        ImageView capture = findViewById(R.id.capture);
        TextView latView = findViewById(R.id.latView);
        TextView longView = findViewById(R.id.longView);
        TextView bearView = findViewById(R.id.bearView);
        Button zoomButton = findViewById(R.id.zoomButton);
        capture.setVisibility(View.GONE);
        latView.setVisibility(View.GONE);
        longView.setVisibility(View.GONE);
        bearView.setVisibility(View.GONE);
        zoomButton.setVisibility(View.GONE);

        //visibilityGoneHelper();
    }

    /**
     * This method displays the loss screen.
     * TODO: Display stats such as damage dealt, damage taken, eliminations, etc.
     */
    public void lossScreen(){
        stopGamePing();

        ImageView stormPopup = findViewById(R.id.stormBackground);
        stormPopup.setVisibility(View.VISIBLE);

        ImageView lossPopup = findViewById(R.id.lossScreen);
        lossPopup.setVisibility(View.VISIBLE);

        Button exitButton = findViewById(R.id.exitButton);
        Button fireButton = findViewById(R.id.fireButton);
        exitButton.setText("YOU LOSE");
        fireButton.setVisibility(View.GONE);
        exitButton.setVisibility(View.VISIBLE);

        ImageView capture = findViewById(R.id.capture);
        TextView latView = findViewById(R.id.latView);
        TextView longView = findViewById(R.id.longView);
        TextView bearView = findViewById(R.id.bearView);
        Button zoomButton = findViewById(R.id.zoomButton);
        capture.setVisibility(View.GONE);
        latView.setVisibility(View.GONE);
        longView.setVisibility(View.GONE);
        bearView.setVisibility(View.GONE);
        zoomButton.setVisibility(View.GONE);
       // visibilityGoneHelper();
    }

    private void visibilityGoneHelper(){
        ImageView capture = findViewById(R.id.capture);
        TextView latView = findViewById(R.id.latView);
        TextView longView = findViewById(R.id.longView);
        TextView bearView = findViewById(R.id.bearView);
        Button zoomButton = findViewById(R.id.zoomButton);
        capture.setVisibility(View.GONE);
        latView.setVisibility(View.GONE);
        longView.setVisibility(View.GONE);
        bearView.setVisibility(View.GONE);
        zoomButton.setVisibility(View.GONE);
    }

    /**
     * This method returns the user back to the home screen.
     */
    public void returnHome(View view){
        stopGamePing();
        Intent homeScreen = new Intent(getApplicationContext(), HomeActivity.class);
        startActivity(homeScreen);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        finish();
    }
}