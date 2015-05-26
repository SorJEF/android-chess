package jwtc.android.chess.pubnub;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubError;
import com.pubnub.api.PubnubException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class PubnubService extends Service {

    private static final String LOG_TAG = "PUBNUB";
    private static final String SUBSCRIBE_KEY = "sub-c-2a5d3110-fd71-11e4-afbd-02ee2ddab7fe";
    private static final String PUBLISH_KEY = "pub-c-067bc448-4128-49dd-b522-8b8ff9e038f0";
    private static final String CHANNEL = "chess_channel";
    private final Pubnub pubnub = new Pubnub(PUBLISH_KEY, SUBSCRIBE_KEY, true);
    private PendingIntent pendingIntent;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        PubnubService getService() {
            return PubnubService.this;
        }
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "PubnubService.onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("PubnubService", "Received start id " + startId + ": " + intent);
        pendingIntent = intent.getParcelableExtra(PubnubUserListActivity.PARAM_PINTENT);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "Destroy Service.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    void publishToPubnubChannel(final String message){
        new Thread(new Runnable() {
            @Override
            public void run() {
                Callback callback = new Callback() {
                    public void successCallback(String channel, Object response) {
                        Log.d(LOG_TAG, response.toString());
                    }
                    public void errorCallback(String channel, PubnubError error) {
                        Log.d(LOG_TAG, error.toString());
                    }
                };
                pubnub.publish(CHANNEL, message , callback);
            }
        }).start();
    }


    void subscribeToPubnubChannel() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Callback callback = new Callback() {
                        @Override
                        public void successCallback(String channel, Object message) {
                            super.successCallback(channel, message);
                            Log.d(LOG_TAG, "Success callback to Pubnub channel." + message.toString());
                            if(PubnubChessActivity.isActive){
                                Intent intent = new Intent().putExtra(PubnubChessActivity.PARAM_RESULT, message.toString());
                                try {
                                    pendingIntent.send(PubnubService.this, PubnubChessActivity.STATUS_FINISH, intent);
                                } catch (PendingIntent.CanceledException e) {
                                    Log.d("PUBNUB", "PubnubService.pubnubHereNow(). Can't send result to Activity: " + e.toString());
                                }
                            } else {
                                Intent myIntent = new Intent(PubnubService.this, PubnubChessActivity.class);
                                myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(myIntent);
                            }
                        }

                        @Override
                        public void errorCallback(String channel, PubnubError error) {
                            super.errorCallback(channel, error);
                            Log.d(LOG_TAG, "Error during subscribe to Pubnub channel.");
                        }

                        @Override
                        public void connectCallback(String channel, Object message) {
                            super.connectCallback(channel, message);
                            Log.d(LOG_TAG, "Subscribe to Pubnub channel.");
                        }

                        @Override
                        public void reconnectCallback(String channel, Object message) {
                            super.reconnectCallback(channel, message);
                            Log.d(LOG_TAG, "Reconnect to Pubnub channel.");
                        }

                        @Override
                        public void disconnectCallback(String channel, Object message) {
                            super.disconnectCallback(channel, message);
                            Log.d(LOG_TAG, "Disconnect to Pubnub channel.");
                        }
                    };
                    pubnub.subscribe(CHANNEL, callback);
                } catch (PubnubException e) {
                    Log.d(LOG_TAG, "SUBSCRIBE_ERROR" + e.toString());
                }
            }
        }).start();
    }

    void setPubnubState(final String uuid, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject state = new JSONObject();
                try {
                    state.put("status", "waiting");
                } catch (JSONException e) {
                    Log.d(LOG_TAG, "STATE_JSON_ERROR" + e.toString());
                    return;
                }
                pubnub.setUUID(uuid);
                pubnub.setState(CHANNEL, uuid, state, callback);
            }
        }).start();
    }

    void pubnubHereNow() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (null == pendingIntent) {
                    try {
                        Thread.sleep(100);
                        Log.d(LOG_TAG, "Sleep");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Callback callback = new Callback() {
                    public void successCallback(String channel, Object response) {
                        Log.d("PUBNUB", "HERE_NOW_RESPONSE" + response.toString());
                        ArrayList<PubnubUser> users = parseJsonHereNowResponse(response);
                        Intent intent = new Intent().putExtra(PubnubUserListActivity.PARAM_RESULT, users);
                        try {
                            pendingIntent.send(PubnubService.this, PubnubUserListActivity.STATUS_FINISH, intent);
                        } catch (PendingIntent.CanceledException e) {
                            Log.d("PUBNUB", "PubnubService.pubnubHereNow(). Can't send result to Activity: " + e.toString());
                        }
                    }
                    public void errorCallback(String channel, PubnubError error) {
                        Log.d("PUBNUB", "HERE_NOW_ERROR" + error.toString());
                    }
                };
                try {
                    pendingIntent.send(PubnubUserListActivity.STATUS_START);
                } catch (PendingIntent.CanceledException e) {
                    Log.d("PUBNUB", "PubnubService.pubnubHereNow(). Can't send STATUS_START to Activity: " + e.toString());
                }
                pubnub.hereNow(CHANNEL, true, true, callback);
            }
        }).start();
    }

    String getUUID(){
        return pubnub.getUUID();
    }

    private ArrayList<PubnubUser> parseJsonHereNowResponse(Object response){
        ArrayList<PubnubUser> users = null;
        try {
            JSONObject jsonObject = new JSONObject(response.toString());
            JSONArray jsonArray = jsonObject.getJSONArray("uuids");
            users = new ArrayList<PubnubUser>();
            for (int i=0; i< jsonArray.length(); i++){
                PubnubUser user = new PubnubUser();
                String status;
                String name;
                JSONObject jsonUser;
                try{
                    jsonUser = jsonArray.getJSONObject(i);
                    name = jsonUser.getString("uuid");
                }catch (JSONException e){
                    continue;
                }
                try{
                    status = jsonUser.getJSONObject("state").getString("status");
                }catch (JSONException e){
                    status = "playing";
                }
                user.setName(name);
                user.setStatus(status);
                users.add(user);
            }
        } catch (JSONException e) {
            Log.d("PUBNUB", "JSON PARSE ERROR: " + e.toString());
        }
        return users;
    }


}
