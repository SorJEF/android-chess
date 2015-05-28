package jwtc.android.chess.pubnub;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ViewAnimator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;

public class PubnubChessActivity extends MyBaseActivity {

    public static final int SUBSCRIBE_TASK = 1;
    public static final int STATUS_FINISH = 200;
    public static final String PARAM_PINTENT = "pendingIntent";
    public static final String PARAM_RESULT = "result";
    private static final String LOG_TAG = "PUBNUB";

    private PubnubChessView view;
    private Intent intent;
    private boolean bound = false;
    private PubnubService pubnubService;
    private String opponentName = null;
    private String myName = null;
    private String gameId;

    static boolean isActive = false; // used in PubnubService to understand is PubnubChessActivity active now or not
    static boolean isGameCreated = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        intent = new Intent(this, PubnubService.class);
        SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
        if (prefs.getBoolean("fullScreen", true)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getResources().getBoolean(R.bool.portraitOnly)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        setContentView(R.layout.pubnub_chess);
        view = new PubnubChessView(this);
        view.init();
        Log.i(LOG_TAG, "onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        view.setConfirmMove(true);
        // bind to PubnubService on start
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isActive = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActive = false;
        // sending {status: 'waiting'} to pubnub channel when activity stopped
        setPubnubStateWaiting();
        // unbind from PubnubService if we bound when activity stopped
        if (!bound) return;
        unbindService(serviceConnection);
        bound = false;
    }

    /**
     * This method will be called when PubnubService will send the information to this Activity.
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "requestCode = " + requestCode + ", resultCode = " + resultCode);
        if (requestCode == SUBSCRIBE_TASK && resultCode == STATUS_FINISH) {
            String result = data.getStringExtra(PARAM_RESULT);
            parsePubnubJson(result); // parse received data
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(LOG_TAG, "PubnubChessActivity connected to Service.");
            pubnubService = ((PubnubService.LocalBinder) iBinder).getService();
            bound = true;
            PendingIntent pendingIntent = createPendingResult(SUBSCRIBE_TASK, new Intent(), 0);
            Intent intent = new Intent(PubnubChessActivity.this, PubnubService.class).putExtra(PARAM_PINTENT, pendingIntent);
            startService(intent);
            myName = pubnubService.getUUID();
            String gameCreate = getIntent().getStringExtra("gameCreate");
            if (!TextUtils.isEmpty(gameCreate)) {
                parsePubnubJson(gameCreate);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(LOG_TAG, "PubnubChessActivity disconnected from Service.");
            bound = false;
        }
    };

    private void parsePubnubJson(String line) {
        Log.d(LOG_TAG, "Parse message from pubnub channel: " + line);
        String game;
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(line);
            game = jsonObject.getString("game");
            String id = jsonObject.getString("gameId");
            if(null != game){
                GameType gameType = GameType.getType(game);
                switch(gameType){
                    case CONTINUE:
                        String move;
                        String sender = jsonObject.getString("user");
                        if(id.equalsIgnoreCase(gameId) && sender.equalsIgnoreCase(opponentName)){ // paint move only if this my opponents move
                            move = jsonObject.getString("move");
                            view.paintMove(move);
                        }
                        break;
                    case CREATE:
                        String initiator = jsonObject.getString("initiator");
                        String acceptor = jsonObject.getString("acceptor");
                        view.setMe(myName);
                        if(!isGameCreated && initiator.equalsIgnoreCase(myName)){ // accept game challenge
                            isGameCreated = true;
                            gameId = id;
                            opponentName = acceptor;
                            view.setOpponent(opponentName);
                            view.setGameId(gameId);
                            view.startGame(true);
                            pubnubService.publishToPubnubChannel(jsonObject);
                            setPubnubStatePlaying();
                        }else if(acceptor.equalsIgnoreCase(myName)){
                            gameId = id;
                            opponentName = initiator;
                            view.setOpponent(opponentName);
                            view.setGameId(gameId);
                            view.startGame(false);
                            setPubnubStatePlaying();
                        }
                        break;
                    case END:
                        if(gameId.equalsIgnoreCase(id) && jsonObject.getString("winner").equalsIgnoreCase(myName)){ // get PGN result from punub only if I am a winner
                            JSONObject resultPGN = jsonObject.getJSONObject("result");
                            String pgn = fromJsonToPGN(resultPGN);
                            showWinnerDialog(pgn);
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (JSONException e) {

            Log.d(LOG_TAG, "PubnubChessActivity. Can't parse pubnub json response. Error: " + e.toString());
        }
    }

    /*public void sendString(String s) {
        if (!TextUtils.isEmpty(s)) {
            pubnubService.publishToPubnubChannel(s);
        }
    }*/

    public void sendJsonToPubnub(JSONObject o) {
        if (null != o) {
            pubnubService.publishToPubnubChannel(o);
        }
    }

    private boolean setPubnubStatePlaying(){
        JSONObject state = new JSONObject();
        try {
            state.put("status", "playing");
            pubnubService.setPubnubState(myName, state);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "STATE_JSON_ERROR" + e.toString());
            return false;
        }
        return true;
    }

    private boolean setPubnubStateWaiting(){
        JSONObject state = new JSONObject();
        try {
            state.put("status", "waiting");
            pubnubService.setPubnubState(myName, state);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "STATE_JSON_ERROR" + e.toString());
            return false;
        }
        return true;
    }

    private void showWinnerDialog(String pgnResult){
        new AlertDialog.Builder(this)
                .setTitle("Congrats, you win!")
                .setMessage("Game PGN:")
                .setMessage(pgnResult)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }


    private String fromJsonToPGN(JSONObject json) throws JSONException {
        String result = "";
        String[] arrHead = {"Event", "Site", "Date", "Round", "White", "Black", "Result", "EventDate", "Variant", "Setup", "FEN", "PlyCount"};
        for (int i = 0; i < arrHead.length; i++) {
            try {
                result += "[" + arrHead[i] + " \"" + json.getString(arrHead[i]) + "\"]\n";
            } catch (JSONException e) {
                continue;
            }
        }
        JSONArray jsonArray = json.getJSONArray("moves");
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONArray move;
            try {
                move = jsonArray.getJSONArray(i);
                result += (i+1) + "." + move.get(0) + " ";
                result += move.get(1) + " ";
            } catch (JSONException e) {
                continue;
            }
        }
        return result;
    }

}

enum GameType {

    CONTINUE("continue"),
    CREATE("create"),
    END("end"),
    UNKNOWN("unknown");

    private String typeValue;

    GameType(String type){
        this.typeValue = type;
    }

    static public GameType getType(String pType) {
        for (GameType type: GameType.values()) {
            if (type.getTypeValue().equalsIgnoreCase(pType)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public String getTypeValue() {
        return typeValue;
    }

}
