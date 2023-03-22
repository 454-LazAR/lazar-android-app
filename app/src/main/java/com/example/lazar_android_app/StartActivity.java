package com.example.lazar_android_app;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

public class StartActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();

        if (extras.getString("mode").equals("HOST")) {
            startAsHost();
        } else {
            startAsJoin(extras.getString("roomCode"));
        }
    }

    private void startAsHost() {
        setContentView(R.layout.activity_start_host);
    }

    private void startAsJoin(String roomCode) {
        setContentView(R.layout.activity_start_join);

        TextView banner = findViewById(R.id.lobbyBanner);
        banner.setText(roomCode);
    }
}