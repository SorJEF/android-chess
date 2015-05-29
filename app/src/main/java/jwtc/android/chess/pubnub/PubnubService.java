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
    ArrayList<PubnubUser> users = null;
    private PendingIntent pendingChessIntent;
    private PendingIntent pendingUserListIntent;

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
        pendingUserListIntent = intent.getParcelableExtra(PubnubUserListActivity.PARAM_PINTENT);
        pendingChessIntent = intent.getParcelableExtra(PubnubChessActivity.PARAM_PINTENT);
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

    void publishToPubnubChannel(final JSONObject message) {
        Log.d(LOG_TAG, "Publish to Pubnub: " + message);
        new Thread(new Runnable() {
            @Override
            public void run() {
                pubnub.publish(CHANNEL, message, getPubnubPublishCallback());
            }
        }).start();
    }

    void subscribeToPubnubChannel() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pubnub.subscribe(CHANNEL, getPubnubSubscribeCallback());
                } catch (PubnubException e) {
                    Log.d(LOG_TAG, "SUBSCRIBE_ERROR" + e.toString());
                }
            }
        }).start();
    }

    void setPubnubState(final String uuid, final JSONObject state) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                pubnub.setUUID(uuid);
                pubnub.setState(CHANNEL, uuid, state, getPubnubStateCallback());
            }
        }).start();
    }

    void pubnubHereNow() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                pubnub.hereNow(CHANNEL, true, true, getPubnubHereNowCallback());
            }
        }).start();
    }

    void pubnubPresence() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pubnub.presence(CHANNEL, getPubnubPresenceCallback());
                } catch (PubnubException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    String getUUID() {
        return pubnub.getUUID();
    }

    private ArrayList<PubnubUser> parseJsonHereNowResponse(Object response) {
        try {
            JSONObject jsonObject = new JSONObject(response.toString());
            JSONArray jsonArray = jsonObject.getJSONArray("uuids");
            users = new ArrayList<PubnubUser>();
            for (int i = 0; i < jsonArray.length(); i++) {
                PubnubUser user = new PubnubUser();
                String status;
                String name;
                JSONObject jsonUser;
                try {
                    jsonUser = jsonArray.getJSONObject(i);
                    name = jsonUser.getString("uuid");
                    if (name.equalsIgnoreCase("system_8648739c-b1da-4a4e-ab2c-f21a380896cf"))
                        continue;
                } catch (JSONException e) {
                    continue;
                }
                try {
                    status = jsonUser.getJSONObject("state").getString("status");
                } catch (JSONException e) {
                    status = "playing";
                }
                user.setName(name);
                user.setStatus(status);
                users.add(user);
            }
        } catch (JSONException e) {
            Log.d(LOG_TAG, "JSON PARSE ERROR: " + e.toString());
        }
        return users;
    }

    private Callback getPubnubPresenceCallback() {
        return new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                Log.d(LOG_TAG, channel + " : " + message.getClass() + " : " + message.toString());
                if (null == users || !PubnubUserListActivity.isActive || null == pendingUserListIntent)
                    return;
                try {
                    JSONObject response = new JSONObject(message.toString());
                    String action = response.getString("action");
                    if (action.equalsIgnoreCase("join")) {
                        String newUserName = response.getString("uuid");
                        PubnubUser pubnubUser = new PubnubUser();
                        pubnubUser.setName(newUserName);
                        pubnubUser.setStatus("waiting");
                        users.add(pubnubUser);
                        Intent intent = new Intent().putExtra(PubnubUserListActivity.PARAM_RESULT, users);
                        pendingUserListIntent.send(PubnubService.this, PubnubUserListActivity.STATUS_FINISH, intent);
                    } else if (action.equalsIgnoreCase("state-change")) {
                        String status = response.getJSONObject("data").getString("status");
                        String name = response.getString("uuid");
                        for (PubnubUser user : users) {
                            if (user.getName().equalsIgnoreCase(name)) {
                                user.setStatus(status);
                                break;
                            }
                        }
                        Intent intent = new Intent().putExtra(PubnubUserListActivity.PARAM_RESULT, users);
                        pendingUserListIntent.send(PubnubService.this, PubnubUserListActivity.STATUS_FINISH, intent);
                    }
                } catch (PendingIntent.CanceledException e) {
                    Log.d(LOG_TAG, "PubnubService.pubnubHereNow(). Can't send result to Activity: " + e.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void errorCallback(String channel, PubnubError error) {
                Log.d(LOG_TAG, "ERROR on channel " + channel + " : " + error.toString());
            }
        };
    }

    private Callback getPubnubSubscribeCallback() {
        return new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                super.successCallback(channel, message);
                Log.d(LOG_TAG, "Success callback to Pubnub channel." + message.toString());
                if (PubnubChessActivity.isActive) {
                    /*while (null == pendingChessIntent) {
                        try {
                            Thread.sleep(100);
                            Log.d(LOG_TAG, "Sleep");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }*/
                    Intent intent = new Intent().putExtra(PubnubChessActivity.PARAM_RESULT, message.toString());
                    try {
                        pendingChessIntent.send(PubnubService.this, PubnubChessActivity.STATUS_FINISH, intent);
                    } catch (PendingIntent.CanceledException e) {
                        Log.d(LOG_TAG, "PubnubService.pubnubSubscribe(). Can't send result to Activity: " + e.toString());
                    }
                } else {
                    Intent myIntent = new Intent(PubnubService.this, PubnubChessActivity.class);
                    myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    JSONObject jsonObject;
                    try {
                        jsonObject = new JSONObject(message.toString());
                        if (jsonObject.getString("acceptor").equalsIgnoreCase(getUUID())) {
                            myIntent.putExtra("gameCreate", message.toString());
                            startActivity(myIntent);
                        }
                    } catch (JSONException e) {
                        Log.d(LOG_TAG, "PubnubService.pubnubSubscribe(). Can't send result to Activity: " + e.toString());
                    }
                }
            }
            @Override
            public void errorCallback(String channel, PubnubError error) {
                super.errorCallback(channel, error);
                Log.d(LOG_TAG, "Error during subscribe to Pubnub channel.");
            }
        };
    }

    private Callback getPubnubHereNowCallback() {
        return new Callback() {
            public void successCallback(String channel, Object response) {
                Log.d(LOG_TAG, "HERE_NOW_RESPONSE" + response.toString());
                ArrayList<PubnubUser> users = parseJsonHereNowResponse(response);
                Intent intent = new Intent().putExtra(PubnubUserListActivity.PARAM_RESULT, users);
                try {
                    pendingUserListIntent.send(PubnubService.this, PubnubUserListActivity.STATUS_FINISH, intent);
                } catch (PendingIntent.CanceledException e) {
                    Log.d(LOG_TAG, "PubnubService.pubnubHereNow(). Can't send result to Activity: " + e.toString());
                }
            }
            public void errorCallback(String channel, PubnubError error) {
                Log.d(LOG_TAG, "HERE_NOW_ERROR" + error.toString());
            }
        };
    }

    private Callback getPubnubStateCallback() {
        return new Callback() {
            public void successCallback(String channel, Object response) {
                Log.d(LOG_TAG, "STATE_RESPONSE: " + response.toString());
            }
            public void errorCallback(String channel, PubnubError error) {
                Log.d(LOG_TAG, "STATE_ERROR" + error.toString());
            }
        };
    }

    private Callback getPubnubPublishCallback() {
        return new Callback() {
            public void successCallback(String channel, Object response) {
                Log.d(LOG_TAG, response.toString());
            }
            public void errorCallback(String channel, PubnubError error) {
                Log.d(LOG_TAG, error.toString());
            }
        };
    }

}
