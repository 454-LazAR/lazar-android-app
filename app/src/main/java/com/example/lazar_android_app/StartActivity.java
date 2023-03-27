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
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        // get "Hello World!" from server and set it as the room code
        new RequestTask().execute("http://143.244.200.36:8080/hello-world");

        // generate room code on server and set room code view text
        String roomCode = "Loading..."; // TODO: get string from server
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(roomCode);

        // SHOW "START" BUTTON but disable it (enables when usernames.count > 0)
    }

    private void hostSetRoomCode(String res) {
        String roomCode = res;
        TextView roomCodeView = findViewById(R.id.roomCode);
        roomCodeView.setText(roomCode);
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

    private class RequestTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... uri) {
            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response;
            String responseString = null;
            try {
                response = httpclient.execute(new HttpGet(uri[0]));
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

            // WE WILL NEED A SWITCH OR IF-ELSE BLOCK BASED ON THE RESPONSE TO DETERMINE
            // WHICH ACTIVITY METHOD TO CALL!!!
            hostSetRoomCode(result);
        }
    }
}


