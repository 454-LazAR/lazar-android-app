package com.example.lazar_android_app;


import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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

    private Handler connHandler;
    private Runnable connRunnable;
    private RequestQueue queue;
    protected static final String URL = "https://laz-ar.duckdns.org:8443";

    private StringRequest helloWorldRequest = new StringRequest(Request.Method.GET, URL + "/hello-world",
            response -> {
                setConnected(true);
            }, error -> {
        setConnected(false);
    });

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

        // Start the async connection thread
        startConnTask();
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
            queue.cancelAll(request -> true);;
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

        if(code == null || code.isEmpty() || code.length() != 6) {
            Toast.makeText(this, "Your game ID should be a 6-character alphanumeric code.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent startStart = new Intent(getApplicationContext(), StartActivity.class);

        startStart.putExtra("mode", "JOIN");
        startStart.putExtra("roomCode", code);
        stopConnTask();
        queue.cancelAll(request -> true);;
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

        if (connected) {
            connection.setTextColor(Color.GREEN);
            connection.setText("Connected!");
        }
        else {
            connection.setTextColor(Color.RED);
            connection.setText("Not Connected");
        }
    }

}