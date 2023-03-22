package com.example.lazar_android_app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class HomeActivity extends AppCompatActivity {

    public static final boolean DEBUG = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
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
            startActivity(startStart);
        }
        else {
            // DEBUG MODE ACTIONS
            Button btn = findViewById(R.id.hostButton);
            btn.setText("CLICKED!");
        }
    }

    public void joinGame(View view) {
        if (!DEBUG) {
            Button btn = findViewById(R.id.joinButton);
            LinearLayout codeLayout = findViewById(R.id.joinCodeLayout);
            btn.setVisibility(View.GONE);
            codeLayout.setVisibility(View.VISIBLE);
        }
        else {
            // DEBUG MODE ACTIONS
            Button btn = findViewById(R.id.joinButton);
            btn.setText("CLICKED!");
        }
    }

    public void submitRoomCode(View view) {
        EditText roomCodeField = findViewById(R.id.enterRoomCode);
        String code = roomCodeField.getText().toString();

        Intent startStart = new Intent(getApplicationContext(), StartActivity.class);

        startStart.putExtra("mode", "JOIN");
        startStart.putExtra("roomCode", code);
        startActivity(startStart);
    }
}