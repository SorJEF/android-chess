package jwtc.android.chess.pubnub;

import android.app.ListActivity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

public class PubnubUserListActivity extends ListActivity {
    private static final String LOG_TAG = "PUBNUB";

    public static final String PARAM_PINTENT = "pendingUserListIntent";
    public static final int STATUS_FINISH = 100;
    public final static String PARAM_RESULT = "userListResult";
    public static final int USER_LIST_TASK = 1;
    static boolean isActive = false; // used in PubnubService to understand is PubnubUserListActivity active now or not
    private String myName;
    private PubnubService pubnubService;

    private boolean bound = false;
    static boolean isUserListWasBuild = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myName = getIntent().getStringExtra("myName");
        Toast.makeText(PubnubUserListActivity.this, "Download user list", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "requestCode = " + requestCode + ", resultCode = " + resultCode);
        if (resultCode == STATUS_FINISH && requestCode == USER_LIST_TASK) {
            ArrayList<PubnubUser> users = data.getExtras().getParcelableArrayList(PARAM_RESULT);
            PubnubArrayAdapter adapter = new PubnubArrayAdapter(PubnubUserListActivity.this, users);
            adapter.setMyName(myName);
            setListAdapter(adapter);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "PubnubUserListActivity.onStart()");
        PendingIntent pendingIntent = createPendingResult(USER_LIST_TASK, new Intent(), 0);
        Intent intent = new Intent(PubnubUserListActivity.this, PubnubService.class).putExtra(PARAM_PINTENT, pendingIntent);
        startService(intent);
        myName = getIntent().getStringExtra("myName");
        isActive = true;
        if(bound) return;
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        bound = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOG_TAG, "PubnubUserListActivity.onStop()");
        isActive = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "PubnubUserListActivity.onDestroy()");
        pubnubService.unsubscribeFromPubnubChannel();
        if(!bound) return;
        unbindService(serviceConnection);
        bound = false;
    }

    public void onUserItemPlayBtnClick(View v){
        isActive = false;
        RelativeLayout vwParentRow = (RelativeLayout)v.getParent();
        TextView child = (TextView)vwParentRow.getChildAt(0);
        String acceptor = child.getText().toString();
        String gameId = UUID.randomUUID().toString();
        String gameCreate = "{ game : 'create',  initiator : '" + myName + "', acceptor: '" + acceptor + "', gameId: '" + gameId + "' }";
        Intent i = new Intent();
        i.putExtra("gameCreate", gameCreate);
        i.setClass(PubnubUserListActivity.this, PubnubChessActivity.class);
        startActivity(i);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.d(LOG_TAG, "PubnubUserListActivity.onBackPressed()");
        pubnubService.unsubscribeFromPubnubChannel();
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(LOG_TAG, "PubnubUserListActivity connected to Service.");
            pubnubService = ((PubnubService.LocalBinder) iBinder).getService();
            bound = true;
            pubnubService.subscribeToPubnubChannel();
            if(!isUserListWasBuild){
                pubnubService.pubnubHereNow();
                pubnubService.pubnubPresence();
                isUserListWasBuild = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(LOG_TAG, "PubnubUserListActivity disconnected from Service.");
            bound = false;
        }
    };
}
