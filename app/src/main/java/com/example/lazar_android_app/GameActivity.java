package com.example.lazar_android_app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
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
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.HttpResponse;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.HttpStatus;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.StatusLine;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.ClientProtocolException;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.HttpClient;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.methods.HttpPost;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.entity.StringEntity;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import kotlin.Pair;

public class GameActivity extends AppCompatActivity {


    private boolean DEBUG = true;

    private String _userId;
    private String _gameStatus;
    private int _health;
    private double _longitude;
    private double _latitude;
    private ObjectDetectorHelper objectDetector;
    private float minConfidence = (float) 0.6;
    LocationManager lm;
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            _longitude = location.getLongitude();
            _latitude = location.getLatitude();
        }
    };
    private Executor executor = Executors.newSingleThreadExecutor();
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"};
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private Handler gameHandler;
    private Runnable gameRunnable;
    Camera camera;
    PreviewView mPreviewView;
    ProgressBar healthBar;
    Button fireButton;

    /**
     * During the create function, we boot up the layout and scale & set the health bar to 100.
     * Next, permission to use the camera is asked to the user.
     *
     * @param savedInstanceState
     */
    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Decompile extras
        Bundle extras = getIntent().getExtras();
        _userId = extras.getString("userId");

        if (DEBUG) {
            // makes the capture ImageView visible
            findViewById(R.id.capture).setVisibility(View.VISIBLE);
        }

        mPreviewView = findViewById(R.id.camera);
        healthBar = findViewById(R.id.healthBar);
        fireButton = findViewById(R.id.fireButton);
        fireButton.setBackgroundColor(Color.RED);
        healthBar.setProgress(100);
        healthBar.setScaleY(8f);

        if (allPermissionsGranted()) {
            startCamera();
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            // Get all possible location updates
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Define async thread to run game pings
        gameHandler = new Handler();
        gameRunnable = new Runnable() {
            @Override
            public void run() {
                // Do your background task here
                JSONObject body = new JSONObject();
                try {
                    body.put("playerId", _userId);
                    body.put("latitude", _latitude);
                    body.put("longitude", _longitude);
                    body.put("timestamp", java.time.Instant.now());
                    new RequestTask().execute("http://143.244.200.36:8080/game-ping", body.toString());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                gameHandler.postDelayed(this, 1000); // Schedule the task to run again after 1 second
            }
        };

        // Start the async game ping thread
        startGamePing();
    }

    /**
     * Run this to start the async game ping thread!
     */
    private void startGamePing() {
        gameHandler.post(gameRunnable);
    }

    /**
     * Run this to stop the async game ping thread!
     */
    private void stopGamePing() {
        gameHandler.removeCallbacks(gameRunnable);
    }

    private void getLat() {
        @SuppressLint("MissingPermission") Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        _latitude = location.getLongitude();
    }

    private void getLong() {
        @SuppressLint("MissingPermission") Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        _longitude = location.getLongitude();
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
            fireButton.setBackgroundColor(Color.GREEN);
        }
        else {
            fireButton.setBackgroundColor(Color.RED);
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

    private class RequestTask extends AsyncTask<String, String, String> {
        private String _uri = null;
        private String _body = null;
        HttpResponse response;

        @Override
        protected String doInBackground(String... uri) {
            _uri = uri[0];
            if (uri.length == 2) {
                _body = uri[1];
            }
            HttpClient httpclient = new DefaultHttpClient();
            String responseString = null;
            try {
                if (_uri.equals("http://143.244.200.36:8080/game-ping")) {
                    // Build a POST request with a JSON body
                    HttpPost req = new HttpPost(_uri);
                    StringEntity params = new StringEntity(_body);
                    req.addHeader("content-type", "application/json");
                    req.setEntity(params);
                    response = httpclient.execute(req);
                }

                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    response.getEntity().writeTo(out);
                    responseString = out.toString();
                    out.close();
                } else {
                    //Closes the connection.
                    response.getEntity().getContent().close();
                    throw new IOException(statusLine.getReasonPhrase());
                }
            } catch (ClientProtocolException e) {
                //TODO Handle problems..
            } catch (IOException e) {
                e.printStackTrace();
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            //Do anything with response...

            JSONObject json = null;
            if (result != null) {
                try {
                    json = new JSONObject(result);
                } catch (JSONException e) {
                    // don't throw anything yet
                    // this won't be a JSON after GET /hello-world
                }
            }

            // Switch based on executed API call
            if (_uri.equals("http://143.244.200.36:8080/game-ping")) {
                try {
                    _gameStatus = json.getString("gameStatus");
                    _health = json.getInt("health");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                healthBar.setProgress(_health);
            }
        }
    }
}