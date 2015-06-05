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

import jwtc.android.chess.R;

public class PubnubUserListActivity extends ListActivity {
    private static final String LOG_TAG = "PUBNUB";

    public static final String HERE_NOW_PINTENT = "hereNowIntent";
    public static final String PRESENCE_PINTENT = "presenceIntent";
    public static final String SUBSCRIBE_PINTENT = "subscribeIntent";

    public static final int HERE_NOW_CODE = 100;
    public static final int PRESENCE_JOIN_CODE = 101;
    public static final int PRESENCE_STATE_CODE = 102;
    public static final int PRESENCE_LEAVE_CODE = 103;
    public static final int SUBSCRIBE_STATISTICS_CODE = 103;

    public final static String HERE_NOW_RESULT = "hereNowResult";
    public final static String PRESENCE_JOIN_RESULT = "presenceJoinResult";
    public final static String PRESENCE_STATE_RESULT = "presenceStateResult";
    public final static String PRESENCE_LEAVE_RESULT = "presenceLeaveResult";
    public final static String SUBSCRIBE_STATISTICS_RESULT = "subscribeStatisticsResult";

    public static final int HERE_NOW_TASK = 1;
    public static final int PRESENCE_TASK = 2;
    public static final int SUBSCRIBE_STATISTICS_TASK = 3;

    static boolean isActive = false; // used in PubnubService to understand is PubnubUserListActivity active now or not
    private String myName;
    private PubnubService pubnubService;

    private TextView tvStatistics;
    private ArrayList<PubnubUser> users;
    private PubnubArrayAdapter adapter;

    private boolean bound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "UserListActivity.onCreate()");
        setContentView(R.layout.pubnub_list_view);
        myName = getIntent().getStringExtra("myName");
        users = getIntent().getParcelableArrayListExtra("users");

        tvStatistics = (TextView) findViewById(R.id.tvStatistics);
        //users = new ArrayList<PubnubUser>();
        adapter = new PubnubArrayAdapter(PubnubUserListActivity.this, users);
        adapter.setMyName(myName);
        setListAdapter(adapter);
        Toast.makeText(PubnubUserListActivity.this, "Download user list", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "requestCode = " + requestCode + ", resultCode = " + resultCode);
        switch (requestCode) {
            /*case HERE_NOW_TASK:
                if(resultCode == HERE_NOW_CODE){
                    ArrayList<PubnubUser> hereNowUsers = data.getExtras().getParcelableArrayList(HERE_NOW_RESULT);
                    users.addAll(hereNowUsers);
                    adapter.notifyDataSetChanged();
                }
                break;*/
            case PRESENCE_TASK:
                switch (resultCode){
                    case PRESENCE_JOIN_CODE:
                        PubnubUser joinedUser = data.getParcelableExtra(PRESENCE_JOIN_RESULT);
                        if(null == joinedUser) break;
                        if(users.isEmpty()) break;
                        for(PubnubUser user: users){
                            if(user.getName().equalsIgnoreCase(joinedUser.getName())){
                                return;
                            }
                        }
                        users.add(joinedUser);
                        adapter.notifyDataSetChanged();
                        break;
                    case PRESENCE_STATE_CODE:
                        PubnubUser stateChangedUser = data.getParcelableExtra(PRESENCE_STATE_RESULT);
                        for(PubnubUser user: users){
                            if(user.getName().equalsIgnoreCase(stateChangedUser.getName())){
                                user.setStatus(stateChangedUser.getStatus());
                                adapter.notifyDataSetChanged();
                                break;
                            }
                        }
                        break;
                    case PRESENCE_LEAVE_CODE:
                        String name = data.getStringExtra(PRESENCE_LEAVE_RESULT);
                        for(PubnubUser user: users){
                            if(user.getName().equalsIgnoreCase(name)){
                                users.remove(user);
                                adapter.notifyDataSetChanged();
                                break;
                            }
                        }
                        break;
                    default:
                        break;
                }
                break;
            case SUBSCRIBE_STATISTICS_TASK:
                if(resultCode == SUBSCRIBE_STATISTICS_CODE){
                    String statistics = data.getStringExtra(SUBSCRIBE_STATISTICS_RESULT);
                    tvStatistics.setText(statistics);
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "PubnubUserListActivity.onStart()");
        PendingIntent pendingIntent;
        Intent intent;
        /*pendingIntent = createPendingResult(HERE_NOW_TASK, new Intent(), 0);
        intent = new Intent(PubnubUserListActivity.this, PubnubService.class).putExtra(HERE_NOW_PINTENT, pendingIntent);
        startService(intent);*/
        pendingIntent = createPendingResult(PRESENCE_TASK, new Intent(), 0);
        intent = new Intent(PubnubUserListActivity.this, PubnubService.class).putExtra(PRESENCE_PINTENT, pendingIntent);
        startService(intent);
        pendingIntent = createPendingResult(SUBSCRIBE_STATISTICS_TASK, new Intent(), 0);
        intent = new Intent(PubnubUserListActivity.this, PubnubService.class).putExtra(SUBSCRIBE_PINTENT, pendingIntent);
        startService(intent);
        myName = getIntent().getStringExtra("myName");
        isActive = true;
        if (bound) return;
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        bound = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOG_TAG, "PubnubUserListActivity.onStop()");
        isActive = false;
        pubnubService.unsubscribeFromPubnubChannel();
        if (!bound) return;
        unbindService(serviceConnection);
        bound = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "PubnubUserListActivity.onDestroy()");
    }

    public void onUserItemPlayBtnClick(View v) {
        isActive = false;
        RelativeLayout vwParentRow = (RelativeLayout) v.getParent();
        TextView child = (TextView) vwParentRow.getChildAt(0);
        String acceptor = child.getText().toString();
        String gameId = UUID.randomUUID().toString();
        String gameCreate = "{ game : 'create',  initiator : '" + myName + "', acceptor: '" + acceptor + "', gameId: '" + gameId + "' }";
        Intent i = new Intent();
        i.putExtra("gameCreate", gameCreate);
        i.setClass(PubnubUserListActivity.this, PubnubChessActivity.class);
        startActivity(i);
    }

/*    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.d(LOG_TAG, "PubnubUserListActivity.onBackPressed()");
        pubnubService.unsubscribeFromPubnubChannel();
    }*/

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(LOG_TAG, "PubnubUserListActivity connected to Service.");
            pubnubService = ((PubnubService.LocalBinder) iBinder).getService();
            bound = true;
            /*pubnubService.subscribeToPubnubChannel();
            if(!isUserListWasBuild){
                pubnubService.pubnubHereNow();
                pubnubService.pubnubPresence();
                isUserListWasBuild = true;
            }*/
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(LOG_TAG, "PubnubUserListActivity disconnected from Service.");
            bound = false;
        }
    };
}
