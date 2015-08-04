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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

public class PubnubService extends Service {

    private static final String LOG_TAG = "PUBNUB";
    private static final String SUBSCRIBE_KEY = "sub-c-2a5d3110-fd71-11e4-afbd-02ee2ddab7fe";
    private static final String PUBLISH_KEY = "pub-c-067bc448-4128-49dd-b522-8b8ff9e038f0";
    private static final String CHANNEL = "chess_channel";
    private final Pubnub pubnub = new Pubnub(PUBLISH_KEY, SUBSCRIBE_KEY, true);
    private PendingIntent pendingPresenceIntent = null;
    private static boolean isPresenceWasRunned = false;

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

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "PubnubService.onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("PubnubService", "Received start id " + startId + ": " + intent);
        PendingIntent pendingUserNameIntent = intent.getParcelableExtra(PubnubUsernameActivity.HERE_NOW_PINTENT);
        if (pendingUserNameIntent != null) {
            pubnubUserNameHereNow(pendingUserNameIntent);
        }
        PendingIntent pendingUserListIntent = intent.getParcelableExtra(PubnubUserListActivity.HERE_NOW_PINTENT);
        if (pendingUserListIntent != null) {
            pubnubUserListHereNow(pendingUserListIntent);
        }
        PendingIntent pendingUserItemIntent = intent.getParcelableExtra(PubnubUserListActivity.PRESENCE_PINTENT);
        if (pendingUserItemIntent != null) {
            pubnubPresence(pendingUserItemIntent);
        }
        PendingIntent pendingUserStatisticsIntent = intent.getParcelableExtra(PubnubUserListActivity.SUBSCRIBE_PINTENT);
        if (pendingUserStatisticsIntent != null) {
            subscribeToPubnubChannelForStatistics(pendingUserStatisticsIntent);
        }
        PendingIntent pendingChessIntent = intent.getParcelableExtra(PubnubChessActivity.CHESS_PINTENT);
        if (pendingChessIntent != null) {
            subscribeToPubnubChannelForChess(pendingChessIntent);
        }
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
        SimpleDateFormat f = new SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss.SSS'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = f.format(new Date());
        try {
            message.put("timestamp", date);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Couldn't add timestamp field to message '" + message + "' before publish to Pubnub. Error: " + e);
        }
        Log.d(LOG_TAG, "Publish to Pubnub: " + message);
        pubnub.publish(CHANNEL, message, getPubnubPublishCallback());
    }

    void subscribeToPubnubChannelForChess(final PendingIntent pendingIntent) {
        Log.d(LOG_TAG, "Subscribe on Pubnub Channel for chess.");
        try {
            pubnub.subscribe(CHANNEL, getPubnubChessSubscribeCallback(pendingIntent));
        } catch (PubnubException e) {
            Log.d(LOG_TAG, "SUBSCRIBE_ERROR" + e.toString());
        }
    }

    void subscribeToPubnubChannelForStatistics(final PendingIntent pendingIntent) {
        Log.d(LOG_TAG, "Subscribe on Pubnub Channel for statistics.");
        try {
            pubnub.subscribe(CHANNEL, getPubnubStatisticsSubscribeCallback(pendingIntent));
        } catch (PubnubException e) {
            Log.d(LOG_TAG, "SUBSCRIBE_ERROR" + e.toString());
        }
    }

    void unsubscribeFromPubnubChannel() {
        Log.d(LOG_TAG, "Unsubscribe from Pubnub Channel.");
        pubnub.unsubscribe(CHANNEL);
    }

    void setPubnubState(final String uuid, final JSONObject state) {
        Log.d(LOG_TAG, "Set pubnub state for: " + uuid);
        pubnub.setUUID(uuid);
        pubnub.setState(CHANNEL, uuid, state, getPubnubStateCallback());
    }

    void pubnubUserNameHereNow(final PendingIntent pendingIntent) {
        Log.d(LOG_TAG, "Pubnub here now(). For UsernameActivity.");
        pubnub.hereNow(CHANNEL, true, true, getPubnubHereNowUserNameCallback(pendingIntent));
    }

    void pubnubUserListHereNow(final PendingIntent pendingIntent) {
        Log.d(LOG_TAG, "Pubnub here now(). For UserListActivity.");
        pubnub.hereNow(CHANNEL, true, true, getPubnubHereNowUserListCallback(pendingIntent));
    }

    void pubnubPresence(PendingIntent pendingIntent) {
        Log.d(LOG_TAG, "Pubnub presence().");
        pendingPresenceIntent = pendingIntent;
        if (!isPresenceWasRunned) {
            isPresenceWasRunned = true;
            try {
                pubnub.presence(CHANNEL, getPubnubPresenceCallback());
            } catch (PubnubException e) {
                e.printStackTrace();
            }
        }
    }

    String getUUID() {
        return pubnub.getUUID();
    }

    private ArrayList<PubnubUser> parseJsonHereNowResponse(Object response) {
        ArrayList<PubnubUser> users = null;
        try {
            users = new ArrayList<PubnubUser>();
            JSONObject jsonObject = new JSONObject(response.toString());
            JSONArray jsonArray = jsonObject.getJSONArray("uuids");
            for (int i = 0; i < jsonArray.length(); i++) {
                PubnubUser user = new PubnubUser();
                String status;
                String name;
                JSONObject jsonUser;
                try {
                    jsonUser = jsonArray.getJSONObject(i);
                    name = jsonUser.getString("uuid");
                    if (name.equalsIgnoreCase("system_8648739c-b1da-4a4e-ab2c-f21a380896cf") || name.equalsIgnoreCase(getUUID()))
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
                if (!PubnubUserListActivity.isActive) return;
                PubnubUser pubnubUser = new PubnubUser();
                try {
                    JSONObject response = new JSONObject(message.toString());
                    String action = response.getString("action");
                    if (action.equalsIgnoreCase("join")) {
                        String name = response.getString("uuid");
                        if (name.equalsIgnoreCase(getUUID())) return;
                        pubnubUser.setName(name);
                        pubnubUser.setStatus("waiting");
                        Intent intent = new Intent().putExtra(PubnubUserListActivity.PRESENCE_JOIN_RESULT, pubnubUser);
                        pendingPresenceIntent.send(PubnubService.this, PubnubUserListActivity.PRESENCE_JOIN_CODE, intent);
                    } else if (action.equalsIgnoreCase("state-change")) {
                        String status = response.getJSONObject("data").getString("status");
                        String name = response.getString("uuid");
                        pubnubUser.setName(name);
                        pubnubUser.setStatus(status);
                        Intent intent = new Intent().putExtra(PubnubUserListActivity.PRESENCE_STATE_RESULT, pubnubUser);
                        pendingPresenceIntent.send(PubnubService.this, PubnubUserListActivity.PRESENCE_STATE_CODE, intent);
                    } else if (action.equalsIgnoreCase("leave") || action.equalsIgnoreCase("timeout")) {
                        String name = response.getString("uuid");
                        Intent intent = new Intent().putExtra(PubnubUserListActivity.PRESENCE_LEAVE_RESULT, name);
                        pendingPresenceIntent.send(PubnubService.this, PubnubUserListActivity.PRESENCE_LEAVE_CODE, intent);
                    }
                } catch (PendingIntent.CanceledException e) {
                    Log.d(LOG_TAG, "PubnubService.pubnubPresence(). Can't send result to Activity: " + e.toString());
                } catch (JSONException e) {
                    Log.d(LOG_TAG, "PubnubService.pubnubPresence(). Can't send result to Activity: " + e.toString());
                }
            }

            @Override
            public void errorCallback(String channel, PubnubError error) {
                Log.d(LOG_TAG, "ERROR on channel " + channel + " : " + error.toString());
            }
        };
    }

    private Callback getPubnubStatisticsSubscribeCallback(final PendingIntent pendingIntent) {
        return new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                super.successCallback(channel, message);
                Log.d(LOG_TAG, "Success callback to Pubnub channel." + message.toString());
                JSONObject jsonObject;
                try {
                    jsonObject = new JSONObject(message.toString());
                    if (jsonObject.has("action") && jsonObject.getString("action").equalsIgnoreCase("statistics") && jsonObject.getString("user").equalsIgnoreCase(getUUID())) {
                        String statistics = "Here is your statistic, " + getUUID() + ".\nTotal: " + jsonObject.getInt("total") + ", Win: " + jsonObject.getInt("win") + ", Lose: " + jsonObject.getInt("lose") + ", Draw: " + jsonObject.getInt("draw");
                        Intent intent = new Intent().putExtra(PubnubUserListActivity.SUBSCRIBE_STATISTICS_RESULT, statistics);
                        pendingIntent.send(PubnubService.this, PubnubUserListActivity.SUBSCRIBE_STATISTICS_CODE, intent);
                    } else if (jsonObject.has("acceptor") && jsonObject.getString("acceptor").equalsIgnoreCase(getUUID())) {
                        pubnub.unsubscribe(CHANNEL);
                        Intent intent = new Intent(PubnubService.this, PubnubChessActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("gameCreate", message.toString());
                        startActivity(intent);
                    }
                } catch (JSONException e) {
                    try {
                        JSONArray jsonArray = new JSONArray(message.toString());
                        if (jsonArray.length() == 0) {
                            String statistics = "Here is your statistic, " + getUUID() + ".\nTotal: 0, Win: 0, Lose: 0, Draw: 0";
                            Intent intent = new Intent().putExtra(PubnubUserListActivity.SUBSCRIBE_STATISTICS_RESULT, statistics);
                            pendingIntent.send(PubnubService.this, PubnubUserListActivity.SUBSCRIBE_STATISTICS_CODE, intent);
                        }
                    } catch (JSONException e1) {
                        e1.printStackTrace();
                    } catch (PendingIntent.CanceledException e1) {
                        e1.printStackTrace();
                    }
                    Log.d(LOG_TAG, "PubnubService.pubnubSubscribe(). Can't send result to Activity: " + e.toString());
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void errorCallback(String channel, PubnubError error) {
                super.errorCallback(channel, error);
                Log.d(LOG_TAG, "Error during subscribe to Pubnub channel.");
            }
        };
    }

    private Callback getPubnubChessSubscribeCallback(final PendingIntent pendingChessIntent) {
        return new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                super.successCallback(channel, message);
                Log.d(LOG_TAG, "Success callback to Pubnub channel." + message.toString());
                if (PubnubChessActivity.isActive) {
                    Intent intent = new Intent().putExtra(PubnubChessActivity.CHESS_RESULT, message.toString());
                    try {
                        pendingChessIntent.send(PubnubService.this, PubnubChessActivity.STATUS_FINISH, intent);
                    } catch (PendingIntent.CanceledException e) {
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

    private Callback getPubnubHereNowUserNameCallback(final PendingIntent pendingIntent) {
        return new Callback() {
            public void successCallback(String channel, Object response) {
                Log.d(LOG_TAG, "HERE_NOW_RESPONSE" + response.toString());
                ArrayList<PubnubUser> users = parseJsonHereNowResponse(response);
                Intent intent = new Intent().putExtra(PubnubUsernameActivity.HERE_NOW_RESULT, users);
                try {
                    pendingIntent.send(PubnubService.this, PubnubUsernameActivity.HERE_NOW_CODE, intent);
                } catch (PendingIntent.CanceledException e) {
                    Log.d(LOG_TAG, "PubnubService.pubnubHereNow(). Can't send result to Activity: " + e.toString());
                }
            }

            public void errorCallback(String channel, PubnubError error) {
                Log.d(LOG_TAG, "HERE_NOW_ERROR" + error.toString());
            }
        };
    }

    private Callback getPubnubHereNowUserListCallback(final PendingIntent pendingIntent) {
        return new Callback() {
            public void successCallback(String channel, Object response) {
                Log.d(LOG_TAG, "HERE_NOW_RESPONSE" + response.toString());
                ArrayList<PubnubUser> users = parseJsonHereNowResponse(response);
                Intent intent = new Intent().putExtra(PubnubUserListActivity.HERE_NOW_RESULT, users);
                try {
                    pendingIntent.send(PubnubService.this, PubnubUserListActivity.HERE_NOW_CODE, intent);
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
