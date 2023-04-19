package com.example.lazar_android_app;


import static com.example.lazar_android_app.HomeActivity.URL;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;

import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;

public class StartActivity extends AppCompatActivity {
    private boolean hosting;
    ArrayList<String> usernames;
    ArrayAdapter<String> adapter;
    ListView roster;
    private Handler connHandler;
    private Runnable connRunnable;
    private Handler lobbyHandler;
    private Runnable lobbyRunnable;
    private String _userId;
    private String _roomCode;
    private String _gameStatus;

    private RequestQueue queue;

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Swiper to previous screen is disabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        queue = Volley.newRequestQueue(this);

        setContentView(R.layout.activity_start);

        Bundle extras = getIntent().getExtras();

        // update room roster
        roster = findViewById(R.id.roster);
        usernames = new ArrayList<>();
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                usernames);
        roster.setAdapter(adapter);

        if (extras.getString("mode").equals("HOST")) {
            hosting = true;
        } else {
            hosting = false;
            _roomCode = extras.getString("roomCode");
            TextView roomCodeView = findViewById(R.id.roomCode);
            roomCodeView.setText(_roomCode);
        }

        // update room roster
        roster = findViewById(R.id.roster);
        usernames = new ArrayList<>();
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                usernames);
        roster.setAdapter(adapter);

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

        // Define async thread to run lobby pings
        lobbyHandler = new Handler();
        lobbyRunnable = new Runnable() {
            @Override
            public void run() {
                // Do your background task here
                JSONObject body = new JSONObject();
                try {
                    body.put("playerId", _userId);
                    queue.add(getLobbyPingRequest(body));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                lobbyHandler.postDelayed(this, 1000); // Schedule the task to run again after 1 second
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

    /**
     * Run this to start the lobby ping thread! Also stops the connectivity thread.
     */
    private void startLobbyPing() {
        stopConnTask();
        lobbyHandler.post(lobbyRunnable);
    }

    /**
     * Run this to stop the lobby ping thread!
     */
    private void stopLobbyPing() {
        lobbyHandler.removeCallbacks(lobbyRunnable);
    }

    private void startAsHost() {
        // Set room code at top
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(_roomCode);

        // Begin pinging lobby
        startLobbyPing();
    }

    private void startAsJoin() {
        // Begin pinging lobby
        startLobbyPing();
    }

    public void submitNickname(View view) {
        EditText nameField = findViewById(R.id.enterNickname);
        Button joinButton = findViewById(R.id.joinButton);
        String username = nameField.getText().toString();

        if (!usernames.contains(username)) {
            usernames.add(username);

            // hide the username entry field and join button
            nameField.setVisibility(View.GONE);
            joinButton.setVisibility(View.GONE);

            // notify roster's adapter that
            // data in list is updated to
            // update our list view.
            adapter.notifyDataSetChanged();
        }

        JSONObject body = new JSONObject();
        if (hosting) {
            try {
                body.put("username", username);
                queue.add(getCreateRequest(body));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            try {
                body.put("username", username);
                body.put("gameId", _roomCode);
                queue.add(getJoinRequest(body));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
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

    /**
     * OnClick handler for the host's start button
     *
     * @param view
     */
    public void hostTryStart(View view) {
        JSONObject body = new JSONObject();
        try {
            body.put("playerId", _userId);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        queue.add(getStartRequest(body));
    }

    /**
     * Hosts and non-hosts can run this method to switch over from the lobby to the in progress game
     * activity.
     *
     * Passes the following info to GameActivity:
     * - userId
     */
    private void startGame() {
        // Stop the running threads!! We're starting a GAME bestie LET'S GOOOOOOO
        stopLobbyPing();
        queue.cancelAll(request -> true);

        // Put extras to transfer data to GameActivity, then start the game activity
        Intent startGame = new Intent(getApplicationContext(), GameActivity.class);
        startGame.putExtra("userId", _userId);
        startActivity(startGame);
    }

    private void handleAbandoned() {
        Toast.makeText(this, "Game was abandoned by the host.", Toast.LENGTH_SHORT).show();

        stopLobbyPing();
        queue.cancelAll(request -> true);;

        Intent homeActivity = new Intent(getApplicationContext(), HomeActivity.class);
        startActivity(homeActivity);
    }

    private StringRequest helloWorldRequest = new StringRequest(Request.Method.GET, URL + "/hello-world",
        response -> {
            setConnected(true);
        }, error -> {
            setConnected(false);
        });

    private JsonObjectRequest getJoinRequest(JSONObject requestBody) {
        return new JsonObjectRequest(Request.Method.POST, URL + "/join", requestBody,
            response -> {
                try {
                    _userId = response.getString("id");
                    _roomCode = response.getString("gameId");
                    startAsJoin();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }, error -> {

            }
        );
    }

    private JsonObjectRequest getCreateRequest(JSONObject requestBody) {
        return new JsonObjectRequest(Request.Method.POST, URL + "/create", requestBody,
            response -> {
                try {
                    _userId = response.getString("id");
                    _roomCode = response.getString("gameId");
                    startAsHost();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }, error -> {
                System.out.println("here");
            }
        );
    }

    private JsonObjectRequest getLobbyPingRequest(JSONObject requestBody) {
        return new JsonObjectRequest(Request.Method.POST, URL + "/lobby-ping", requestBody,
            response -> {
                // Parse result into game status and usernames and update the "usernames" ArrayList
                // (automatically updates the ListView)
                try {
                    setConnected(true);
                    _gameStatus = response.getString("gameStatus");
                    JSONArray usernameArr = response.getJSONArray("usernames");
                    ArrayList<String> new_usernames = new ArrayList<>();
                    //Iterating JSON array
                    for (int i = 0; i < usernameArr.length(); ++i) {
                        // Adding each element of JSON array into ArrayList
                        new_usernames.add((String) usernameArr.get(i));
                    }

                    // update "usernames" ArrayList and refresh roster
                    usernames = new_usernames;
                    Collections.sort(usernames);
                    adapter.clear();
                    adapter.addAll(usernames);
                    adapter.notifyDataSetChanged();

                    // reveal start button for host if at least 2 players in lobby, else vanish it
                    if (hosting) {
                        Button startButton = findViewById(R.id.startButton);
                        startButton.setVisibility(usernames.size() > 1 ? View.VISIBLE : View.GONE);
                    }
                } catch (JSONException e) {
                    // server returned different data set, likely since lobby status has changed
                }

                // regardless of if the roster updated, CHECK IF THE GAME STARTED AND REACT

                // Check if host has started game! If so, start game!
                if (_gameStatus.equals("IN_PROGRESS")) {
                    startGame();
                } else if (_gameStatus.equals("ABANDONED")) {
                    handleAbandoned();
                } else {
                    // crash
                }
            }, error -> {

            }
        );
    }

    private StringRequest getStartRequest(JSONObject requestBody) {
        return new StringRequest(Request.Method.POST, URL + "/start",
            response -> {
                // Success: A 200 will be sent with a boolean indicating that the game was started
                //          successfully.
                if (Boolean.valueOf(response)) {
                    startGame();
                }
            }, error -> {
                // do nothing?
            }
        ) {
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

}


