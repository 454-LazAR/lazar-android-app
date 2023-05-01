package com.example.lazar_android_app;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class HomeActivity extends AppCompatActivity {
    public static final boolean DEBUG = false;
    protected static Boolean SOUND = true;
    protected static Boolean MC_MODE = false;
    protected static Boolean HIGHLIGHTER = false;

    protected static int BGMChoice = 0;

    private Intent BGMIntent;
    private Handler connHandler;
    private Runnable connRunnable;
    private RequestQueue queue;
    protected static final String URL = "https://laz-ar.duckdns.org:8443";
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"};
    private final int REQUEST_CODE_PERMISSIONS = 1001;
    private final StringRequest helloWorldRequest = new StringRequest(Request.Method.GET, URL + "/hello-world",
        response -> setConnected(true),
        error -> setConnected(false)
    );
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        queue = Volley.newRequestQueue(this);

        // Define async thread to run to update connection
        connHandler = new Handler();
        connRunnable = new Runnable() {
            @Override
            public void run() {
                // Do your background task here
                queue.add(helloWorldRequest);

                connHandler.postDelayed(this, 2000); // Schedule the task to run again after 2 seconds
            }
        };

        // set settings button colors
        Button soundSetting = findViewById(R.id.sounds);
        soundSetting.setBackgroundColor(Color.GREEN);
        Button mcSetting = findViewById(R.id.mcMode);
        mcSetting.setBackgroundColor(Color.RED);
        Button highlightButton = findViewById(R.id.highlighter);
        highlightButton.setBackgroundColor(Color.RED);

        if (!allPermissionsGranted()){
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);

        }

        playBackgroundMusic();

        // Start the async connection thread
        startConnTask();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopService(BGMIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        playBackgroundMusic();
    }


    /**
     * Run this to start the async connection thread!
     */
    private void startConnTask() {
        connHandler.post(connRunnable);
    }

    /**
     * Run this to stop the async connection thread!
     */
    private void stopConnTask() {
        connHandler.removeCallbacks(connRunnable);
    }

    /**
     * Starts background music
     */
    public void playBackgroundMusic() {
        if(!SOUND) return;
        BGMIntent = new Intent(this, BackgroundMusicService.class);
        startService(BGMIntent);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void hostGame(View view) {
        if (!DEBUG) {
            // Change view to StartActivity (search for nearby "joining")
            Intent startStart = new Intent(getApplicationContext(), StartActivity.class);
            startStart.putExtra("mode", "HOST");
            stopConnTask();
            queue.cancelAll(request -> true);
            startActivity(startStart);
            finish();
        }
        else {
            // DEBUG MODE ACTIONS
            Button btn = findViewById(R.id.hostButton);
            btn.setText("CLICKED!");
        }
    }

    public void joinGame(View view) {
        Button btn = findViewById(R.id.joinButton);
        if (!DEBUG) {
            LinearLayout codeLayout = findViewById(R.id.joinCodeLayout);
            btn.setVisibility(View.GONE);
            codeLayout.setVisibility(View.VISIBLE);
        }
        else {
            // DEBUG MODE ACTIONS
            btn.setText("CLICKED!");
        }
    }

    public void submitRoomCode(View view) {
        EditText roomCodeField = findViewById(R.id.enterRoomCode);
        String code = roomCodeField.getText().toString();

        hideKeyboard();

        if(code.length() != 6) {
            Toast.makeText(this, "Your game ID should be a 6-character alphanumeric code.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent startStart = new Intent(getApplicationContext(), StartActivity.class);

        startStart.putExtra("mode", "JOIN");
        startStart.putExtra("roomCode", code);
        stopConnTask();
        queue.cancelAll(request -> true);
        startActivity(startStart);
        finish();
    }

    private void hideKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        if(getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void setConnected(boolean connected) {
        TextView connection = findViewById(R.id.serverConnection);

        // Uncomment if u want a noise every time the connection is updated (for debugging)
//        tryPlaySound(MC_MODE ? R.raw.mc_villagerhmm : R.raw.mc_villagerhmm)

        if (connected) {
            connection.setTextColor(Color.GREEN);
            connection.setText("Connected!");
        }
        else {
            connection.setTextColor(Color.RED);
            connection.setText("Not Connected");
        }
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

    public void tryPlaySound(int soundId) {
        if (SOUND) {
            MediaPlayer mp = MediaPlayer.create(this, soundId);
            mp.start();
        }
    }

    public void toggleSounds(View view) {
        SOUND = !SOUND;

        Button soundSetting = findViewById(R.id.sounds);
        soundSetting.setBackgroundColor(SOUND ? Color.GREEN : Color.RED);

        if (SOUND) {
            playBackgroundMusic();
        }
        else {
            stopService(BGMIntent);
        }
    }
    public void toggleMcMode(View view) {
        MC_MODE = !MC_MODE;

        Button mcSetting = findViewById(R.id.mcMode);
        mcSetting.setBackgroundColor(MC_MODE ? Color.GREEN : Color.RED);
    }
    public void toggleHighlighter(View view) {
        HIGHLIGHTER = !HIGHLIGHTER;

        Button highlightButton = findViewById(R.id.highlighter);
        highlightButton.setBackgroundColor(HIGHLIGHTER ? Color.GREEN : Color.RED);
    }
}