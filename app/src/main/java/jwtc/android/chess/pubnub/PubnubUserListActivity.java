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

public class PubnubUserListActivity extends ListActivity {
    private static final String LOG_TAG = "PUBNUB";

    public static final String PARAM_PINTENT = "pendingIntent";
    public static final int STATUS_START = 100;
    public static final int STATUS_FINISH = 200;
    public final static String PARAM_RESULT = "result";
    public static final int USER_LIST_TASK = 1;

    private PubnubService pubnubService;
    private Intent intent;
    private boolean bound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        intent = new Intent(this, PubnubService.class);
        Toast.makeText(PubnubUserListActivity.this, "Download user list", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "requestCode = " + requestCode + ", resultCode = " + resultCode);
        if (resultCode == STATUS_FINISH && requestCode == USER_LIST_TASK) {
            ArrayList<PubnubUser> users = data.getExtras().getParcelableArrayList(PARAM_RESULT);
            PubnubArrayAdapter adapter = new PubnubArrayAdapter(PubnubUserListActivity.this, users);
            setListAdapter(adapter);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(!bound) return;
        unbindService(serviceConnection);
        bound = false;
    }

    @Override
    protected void onPause() {
        super.onStop();
        if(!bound) return;
        unbindService(serviceConnection);
        bound = false;
    }

    public void onUserItemPlayBtnClick(View v){
        RelativeLayout vwParentRow = (RelativeLayout)v.getParent();
        TextView child = (TextView)vwParentRow.getChildAt(0);
        String uuid = child.getText().toString();
        Intent i = new Intent();
        i.putExtra("uuid", uuid);
        i.setClass(PubnubUserListActivity.this, PubnubChessActivity.class);
        startActivity(i);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(LOG_TAG, "PubnubUserListActivity connected to Service.");
            pubnubService = ((PubnubService.LocalBinder) iBinder).getService();
            bound = true;
            PendingIntent pendingIntent = createPendingResult(USER_LIST_TASK, new Intent(), 0);
            Intent intent = new Intent(PubnubUserListActivity.this, PubnubService.class).putExtra(PARAM_PINTENT, pendingIntent);
            startService(intent);
            pubnubService.pubnubHereNow();
            pubnubService.subscribeToPubnubChannel();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(LOG_TAG, "PubnubUserListActivity disconnected from Service.");
            bound = false;
        }
    };
}
