package com.example.lazar_android_app;

import static com.example.lazar_android_app.GameActivity.URL;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.HttpResponse;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.HttpStatus;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.StatusLine;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.ClientProtocolException;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.HttpClient;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.methods.HttpGet;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class HomeActivity extends AppCompatActivity {

    public static final boolean DEBUG = false;

    private Handler connHandler;
    private Runnable connRunnable;
    private RequestQueue queue;

    private StringRequest helloWorldRequest = new StringRequest(Request.Method.GET, URL + "/hello-world",
            response -> {
                setConnected(true);
            }, error -> {
        setConnected(false);
    });

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Swiper to previous screen is disabled", Toast.LENGTH_SHORT).show();
    }
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
            queue.cancelAll(null);
            startActivity(startStart);
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

        Intent startStart = new Intent(getApplicationContext(), StartActivity.class);

        startStart.putExtra("mode", "JOIN");
        startStart.putExtra("roomCode", code);
        stopConnTask();
        queue.cancelAll(null);
        startActivity(startStart);
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

//    public void openGame(View view){
//        Intent startGame = new Intent(getApplicationContext(), GameActivity.class);
//        startActivity(startGame);
//    }

}