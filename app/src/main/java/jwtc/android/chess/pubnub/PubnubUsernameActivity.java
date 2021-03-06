package jwtc.android.chess.pubnub;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import jwtc.android.chess.R;

public class PubnubUsernameActivity extends Activity {

    private EditText usernameField;

    private static final String LOG_TAG = "PUBNUB";
    public static final String HERE_NOW_PINTENT = "usernameHereNowIntent";
    public static final int HERE_NOW_CODE = 100;
    public final static String HERE_NOW_RESULT = "usernameHereNowResult";
    public static final int HERE_NOW_TASK = 1;

    private PubnubService pubnubService;
    private boolean bound = false;
    private  ArrayList<PubnubUser> hereNowUsers;

    private void findAllViewId() {
        usernameField = (EditText) findViewById(R.id.pubnubUsernameField);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pubnub_username);
        findAllViewId();
        hereNowUsers = new ArrayList<PubnubUser>();
    }

    public void onNextBtnClick(View view) {
        String username = usernameField.getText().toString();
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(getApplicationContext(), "Enter your username, please.", Toast.LENGTH_SHORT).show();
        } else {
            if (!bound) return;
            for(PubnubUser user: hereNowUsers){
                if(user.getName().equalsIgnoreCase(username)){
                    Toast.makeText(getApplicationContext(), "Sorry, this username is already taken. Choose another.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            JSONObject state = getJsonStateWaitingObject();
            if (null == state) return;
            pubnubService.setPubnubState(username, state);
            startUserListActivity(username);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == HERE_NOW_TASK && resultCode == HERE_NOW_CODE){
            ArrayList<PubnubUser> users = data.getExtras().getParcelableArrayList(HERE_NOW_RESULT);
            hereNowUsers = users;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        PendingIntent pendingIntent;
        Intent intent;
        pendingIntent = createPendingResult(HERE_NOW_TASK, new Intent(), 0);
        intent = new Intent(PubnubUsernameActivity.this, PubnubService.class).putExtra(HERE_NOW_PINTENT, pendingIntent);
        startService(intent);
        if (bound) return;
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!bound) return;
        unbindService(serviceConnection);
        bound = false;
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(LOG_TAG, "PubnubUsernameActivity connected to Service.");
            pubnubService = ((PubnubService.LocalBinder) iBinder).getService();
            bound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(LOG_TAG, "PubnubUsernameActivity disconnected from Service.");
            bound = false;
        }
    };

    private void startUserListActivity(String myName) {
        Intent intent = new Intent(PubnubUsernameActivity.this, PubnubUserListActivity.class);
        Bundle b = new Bundle();
        b.putString("myName", myName);
        intent.putExtras(b);
        //intent.putParcelableArrayListExtra("users", hereNowUsers);
        startActivity(intent);
    }

    private JSONObject getJsonStateWaitingObject() {
        JSONObject state = new JSONObject();
        try {
            state.put("status", "waiting");
        } catch (JSONException e) {
            Log.d(LOG_TAG, "STATE_JSON_ERROR" + e.toString());
            return null;
        }
        return state;
    }
}
