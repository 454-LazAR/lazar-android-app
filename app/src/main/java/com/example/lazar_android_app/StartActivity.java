package com.example.lazar_android_app;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.HttpResponse;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.HttpStatus;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.StatusLine;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.ClientProtocolException;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.HttpClient;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.methods.HttpGet;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.methods.HttpPost;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.utils.URIBuilder;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.entity.StringEntity;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.impl.client.DefaultHttpClient;

import org.apache.http.params.HttpParams;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Array;
import java.util.ArrayList;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        Bundle extras = getIntent().getExtras();

        // update room roster
        roster = findViewById(R.id.roster);
        usernames = new ArrayList<>();
        adapter = new ArrayAdapter<String>(this,
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
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                usernames);
        roster.setAdapter(adapter);

        // Define async thread to run to update connection
        connHandler = new Handler();
        connRunnable = new Runnable() {
            @Override
            public void run() {
                // Do your background task here
                new RequestTask().execute("http://143.244.200.36:8080/hello-world");

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
                    new RequestTask().execute("http://143.244.200.36:8080/lobby-ping", body.toString());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                lobbyHandler.postDelayed(this, 2000); // Schedule the task to run again after 1 second
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
     * Run this to start the lobby ping thread!
     */
    private void startLobbyPing() {
        lobbyHandler.post(lobbyRunnable);
    }

    /**
     * Run this to stop the lobby ping thread!
     */
    private void stopLobbyPing() {
        lobbyHandler.removeCallbacks(lobbyRunnable);
    }

    private void startAsHost() {
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(_roomCode);

        // SHOW "START" BUTTON but disable it (enables when usernames.count > 0)

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
                new RequestTask().execute("http://143.244.200.36:8080/create", body.toString());
                startAsHost();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            try {
                body.put("username", username);
                body.put("gameId", _roomCode);
                new RequestTask().execute("http://143.244.200.36:8080/join", body.toString());
                startAsJoin();
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
                if (_uri.equals("http://143.244.200.36:8080/hello-world")) {
                    // Build a param-less GET request
                    HttpGet req = new HttpGet(_uri);
                    response = httpclient.execute(req);
                }
                else if (_uri.equals("http://143.244.200.36:8080/create")) {
                    // Build the POST request with a JSON body
                    HttpPost req = new HttpPost(_uri);
                    StringEntity params = new StringEntity(_body);
                    req.addHeader("content-type", "application/json");
                    req.setEntity(params);
                    response = httpclient.execute(req);
                }
                else if (_uri.equals("http://143.244.200.36:8080/lobby-ping")) {
                    // Build the GET request with params
                    HttpPost req = new HttpPost(_uri);
                    StringEntity params = new StringEntity(_body);
                    req.addHeader("content-type", "application/json");
                    req.setEntity(params);
                    response = httpclient.execute(req);
                }
                else if (_uri.equals("http://143.244.200.36:8080/join")) {
                    HttpPost req = new HttpPost(_uri);
                    StringEntity params = new StringEntity(_body);
                    req.addHeader("content-type", "application/json");
                    req.setEntity(params);
                    response = httpclient.execute(req);
                }

                StatusLine statusLine = response.getStatusLine();
                if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    response.getEntity().writeTo(out);
                    responseString = out.toString();
                    out.close();
                }
                else{
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
            if (result == null) {
                // fail to connect to server
                setConnected(false);
            }
            else if (result.equals("Hello world!")) {
                // server connection success
                setConnected(true);
            }
            else if (_uri.equals("http://143.244.200.36:8080/create")) {
                // Parse result into user UUID and generated room code
                try {
                    _userId = json.getString("id");
                    _roomCode = json.getString("gameId");
                    startAsHost();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            else if (_uri.equals("http://143.244.200.36:8080/lobby-ping")) {
                // Parse result into game status and usernames and update the "usernames" ArrayList
                // (automatically updates the ListView)
                try {
                    _gameStatus = json.getString("gameStatus");
                    JSONArray usernameArr = json.getJSONArray("usernames");
                    ArrayList<String> new_usernames = new ArrayList<>();
                    //Iterating JSON array
                    for (int i=0; i<usernameArr.length(); ++i){
                        // Adding each element of JSON array into ArrayList
                        new_usernames.add((String)usernameArr.get(i));
                    }
                    // update "usernames" ArrayList
                    usernames = new_usernames;
                    adapter.clear();
                    adapter.addAll(usernames);
                    adapter.notifyDataSetChanged();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            else if (_uri.equals("http://143.244.200.36:8080/join")) {
                // Parse result into user UUID and generated room code
                try {
                    _userId = json.getString("id");
                    _roomCode = json.getString("gameId");
                    startAsJoin();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}


