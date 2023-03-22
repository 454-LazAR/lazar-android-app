package com.example.lazar_android_app;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class StartActivity extends AppCompatActivity {

    ArrayList<String> usernames;
    ArrayAdapter<String> adapter;
    ListView roster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        Bundle extras = getIntent().getExtras();

        if (extras.getString("mode").equals("HOST")) {
            startAsHost();
        } else {
            startAsJoin(extras.getString("roomCode"));
        }

        // update room roster
        roster = findViewById(R.id.roster);
        usernames = new ArrayList<>(); // TODO: update "usernames" with pull from database
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                usernames);
        roster.setAdapter(adapter);
    }

    private void startAsHost() {
        // generate room code on server and set room code view text
        String roomCode = "Loading..."; // TODO: get string from server
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(roomCode);

        // SHOW "START" BUTTON but disable it (enables when usernames.count > 0)
    }

    private void startAsJoin(String roomCode) {
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(roomCode);
    }

    public void submitNickname(View view) {
        EditText roomCodeField = findViewById(R.id.enterNickname);
        Button joinButton = findViewById(R.id.joinButton);
        String username = roomCodeField.getText().toString();

        if (!usernames.contains(username)) {
            usernames.add(username);

            // hide the username entry field and join button
            roomCodeField.setVisibility(View.GONE);
            joinButton.setVisibility(View.GONE);

            // notify roster's adapter that
            // data in list is updated to
            // update our list view.
            adapter.notifyDataSetChanged();
        }
    }
}