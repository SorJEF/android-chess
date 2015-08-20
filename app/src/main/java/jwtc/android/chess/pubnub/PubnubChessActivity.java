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
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;

public class PubnubChessActivity extends MyBaseActivity {

    public static final int SUBSCRIBE_CHESS_TASK = 5;
    public static final int STATUS_FINISH = 200;
    public static final String CHESS_PINTENT = "pendingChessIntent";
    public static final String CHESS_RESULT = "chessResult";
    private static final String LOG_TAG = "PUBNUB";

    private PubnubChessView view;
    private boolean bound = false;
    private PubnubService pubnubService;
    private String opponentName = null;
    private String myName = null;
    private String gameId;

    private TextView tvTendencies;

    static boolean isActive = false; // used in PubnubService to understand is PubnubChessActivity active now or not
    static boolean isGameCreated = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        tvTendencies = (TextView) findViewById(R.id.tvTendencies);
        Log.i(LOG_TAG, "PubnubChessActivity.onCreate()");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(LOG_TAG, "PubnubChessActivity.onStart()");
        PendingIntent pendingIntent = createPendingResult(SUBSCRIBE_CHESS_TASK, new Intent(), 0);
        Intent intent = new Intent(PubnubChessActivity.this, PubnubService.class).putExtra(CHESS_PINTENT, pendingIntent);
        startService(intent);
        view.setConfirmMove(true);
        isActive = true;
        if (bound) return;
        // bind to PubnubService on start
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        pubnubService.unsubscribeFromPubnubChannel();
        setPubnubStateWaiting();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(LOG_TAG, "PubnubChessActivity.onStop()");
        isActive = false;
        // sending {status: 'waiting'} to pubnub channel when activity stopped
        // setPubnubStateWaiting();
        isGameCreated = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "PubnubChessActivity.onDestroy()");
        //pubnubService.unsubscribeFromPubnubChannel();
        // unbind from PubnubService if we bound when activity stopped
        if (!bound) return;
        unbindService(serviceConnection);
        bound = false;
    }

    /**
     * This method will be called when PubnubService will send the information to this Activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "requestCode = " + requestCode + ", resultCode = " + resultCode);
        if (requestCode == SUBSCRIBE_CHESS_TASK && resultCode == STATUS_FINISH) {
            String result = data.getStringExtra(CHESS_RESULT);
            parsePubnubJson(result); // parse received data
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(LOG_TAG, "PubnubChessActivity connected to Service.");
            pubnubService = ((PubnubService.LocalBinder) iBinder).getService();
            bound = true;
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
                        } else if(acceptor.equalsIgnoreCase(myName)){
                            gameId = id;
                            opponentName = initiator;
                            view.setOpponent(opponentName);
                            view.setGameId(gameId);
                            view.startGame(false);
                            pubnubService.publishToPubnubChannel(getTendenciesOpponentObject(gameId, opponentName));
                            setPubnubStatePlaying();
                        }
                        break;
                    case END:
                        if(gameId.equalsIgnoreCase(id) && jsonObject.getString("sendTo").equalsIgnoreCase(myName)){ // get PGN result from pubnub only if I am a winner
                            String pgn = fromJsonToPGN(jsonObject);
                            if(jsonObject.getString("winner") != null){
                                showResultDialog("Congrats, you win!", pgn);
                            }else{
                                showResultDialog("Oh, that's draw!", pgn);
                            }
                            isGameCreated = false;
                        }
                        break;
                    case TENDENCIES:
                        String opponent = jsonObject.getString("user");
                        if(id.equalsIgnoreCase(gameId) && opponent.equalsIgnoreCase(opponentName)){ // display opponent tendencies only if this my opponent
                            showOpponentTendencies(jsonObject);
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (JSONException ex) {
            Log.d(LOG_TAG, "PubnubChessActivity. Can't parse pubnub json response. Error: " + ex.toString());
            try{
                jsonObject = new JSONObject(line);
                if(tvTendencies.getText().toString().trim().length() == 0){
                    showOpponentTendencies(jsonObject);
                    return;
                }
                String user = jsonObject.getString("user");
                String id = jsonObject.getString("gameId");
                if(id.equalsIgnoreCase(gameId)){ // display opponent tendencies only if this my opponent
                    String opening = jsonObject.getString("opening");
                    String firstMoveTimestamp = jsonObject.getString("firstMoveTimestamp");
                    String lastMoveTimestamp = jsonObject.getString("timestamp");
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    Date firstMoveDate = format.parse(firstMoveTimestamp);
                    Date lastMoveDate = format.parse(lastMoveTimestamp);
                    long delta = lastMoveDate.getTime() - firstMoveDate.getTime();
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(delta);
                    long minutes = seconds / 60;
                    long hours = minutes / 60;
                    String timeForOpening = "";
                    if(hours > 0) {
                        timeForOpening += hours + " hours ";
                        timeForOpening += minutes % 60 + " minutes ";
                        timeForOpening += seconds % 60 + " seconds for this opening.";
                    } else if(minutes > 0) {
                        timeForOpening += minutes + " minutes ";
                        timeForOpening += seconds % 60 + " seconds for this opening.";
                    } else {
                        timeForOpening += seconds + " seconds for this opening.";
                    }
                    if(user.equalsIgnoreCase(opponentName)){
                        showOpeningsDialog("Be careful! Your opponent made powerful opening move: " +  opening + ". He spent " + timeForOpening);
                    }else if(user.equalsIgnoreCase(myName)){
                        showOpeningsDialog("Great! You made powerful opening move: " +  opening + ". You spent " + timeForOpening);
                    }
                }
            }catch (JSONException e){
                Log.d(LOG_TAG, "PubnubChessActivity. Can't parse pubnub json response. Error: " + e.toString());
            } catch (ParseException e) {
                Log.d(LOG_TAG, "PubnubChessActivity. Can't parse date json response. Error: " + e.toString());
            }
        }
    }

    public void sendJsonToPubnub(JSONObject o) {
        if (null != o) {
            pubnubService.publishToPubnubChannel(o);
        }
    }

    private JSONObject getTendenciesOpponentObject(String gameId, String opponentName) throws JSONException {
        JSONObject tendencies = new JSONObject();
        tendencies.put("gameId", gameId);
        tendencies.put("game", "create");
        tendencies.put("opponent", opponentName);
        return  tendencies;
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

    private void showOpponentTendencies(JSONObject jsonObject) throws JSONException {
        String tendencies = parseOpponentTendencies(jsonObject);
        tvTendencies.setText(tendencies);
    }

    private String parseOpponentTendencies(JSONObject jsonObject) throws JSONException {
        JSONArray tendenciesArray = jsonObject.optJSONArray("openings");
        String tendencies = "Player '" + opponentName + "' used next chess openings: ";
        if(tendenciesArray == null) return "Player '" + opponentName + "' didn't use any chess opening.";
        for (int i = 0; i < tendenciesArray.length(); i++) {
            JSONObject tendency = tendenciesArray.getJSONObject(i);
            tendencies += tendency.getString("name") + " in ";
            tendencies += tendency.getString("count") + " games";
            if(i + 1 < tendenciesArray.length()){
                tendencies += " , ";
            }else{
                tendencies += ".";
            }
        }
        return tendencies;
    }

    private void showOpeningsDialog(String openingMove){
        new AlertDialog.Builder(this)
                .setTitle("Chess opening move!")
                .setMessage(openingMove)
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

    private void showResultDialog(String title, String pgnResult){
        new AlertDialog.Builder(this)
                .setTitle(title)
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
        String[] arrHead = {"Event", "Site", "Date", "Round", "White", "Black", "gameResult", "EventDate", "Variant", "Setup", "FEN", "PlyCount"};
        for (String anArrHead : arrHead) {
            try {
                if(anArrHead.equalsIgnoreCase("gameResult")){
                    result += "[Result \"" + json.getString(anArrHead) + "\"]\n";
                }else{
                    result += "[" + anArrHead + " \"" + json.getString(anArrHead.toLowerCase()) + "\"]\n";
                }
            } catch (JSONException e) {
                // do nothing
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
                // do nothing
            }
        }
        return result;
    }

}

enum GameType {

    CONTINUE("continue"),
    CREATE("create"),
    END("end"),
    TENDENCIES("tendencies"),
//    OPENING_MOVE("openingName"),
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
