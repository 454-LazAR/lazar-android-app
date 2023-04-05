package com.example.lazar_android_app;

import androidx.appcompat.app.AppCompatActivity;

import android.os.AsyncTask;
import android.os.Bundle;
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
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.entity.StringEntity;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class StartActivity extends AppCompatActivity {

    private boolean hosting;
    ArrayList<String> usernames;
    ArrayAdapter<String> adapter;
    ListView roster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        Bundle extras = getIntent().getExtras();

        if (extras.getString("mode").equals("HOST")) {
            hosting = true;
            startAsHost();
        } else {
            hosting = false;
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
        // get "Hello World!" from server and set it as the room code
        //new RequestTask().execute("http://143.244.200.36:8080/hello-world");

        // generate room code on server and set room code view text
        String roomCode = "Loading..."; // TODO: get string from server
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(roomCode);

        // SHOW "START" BUTTON but disable it (enables when usernames.count > 0)
    }

    private void hostSetRoomCode(String roomCode) {
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(roomCode);
    }

    private void startAsJoin(String roomCode) {
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(roomCode);
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

        if (hosting) {
            JSONObject body = new JSONObject();
            try {
                body.put("username", (Object)username);
                new RequestTask().execute("http://143.244.200.36:8080/create", body.toString());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
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
                if (_uri.equals("http://143.244.200.36:8080/create")) {
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
                } else{
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
            try {
                json = new JSONObject(result);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            // Switch based on executed API call
            if (_uri.equals("http://143.244.200.36:8080/create")) {
                try {
                    String userId = json.getString("id");
                    // TODO: Remember the userId
                    String roomCode = json.getString("gameId");
                    hostSetRoomCode(roomCode);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}


