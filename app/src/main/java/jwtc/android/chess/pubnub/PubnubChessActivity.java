package jwtc.android.chess.pubnub;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
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

import org.json.JSONException;
import org.json.JSONObject;

import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;
import jwtc.chess.Pos;

public class PubnubChessActivity extends MyBaseActivity {

    private PowerManager.WakeLock _wakeLock;
    private PubnubChessView _view;
    private PubnubConfimDialog _dlgConfirm;
    private TextView _tvHeader;
    private ViewAnimator _viewAnimatorMain;

    private Intent intent;
    private boolean bound = false;
    private PubnubService pubnubService;
    public static final int SUBSCRIBE_TASK = 1;
    public static final String PARAM_PINTENT = "pendingIntent";
    public static final int STATUS_START = 100;
    public static final int STATUS_FINISH = 200;
    public final static String PARAM_RESULT = "result";
    static boolean isActive = false;
    static boolean isGameCreateRequestSend = false;

    private String opponentName = null;
    private String myName = null;

    protected static final int VIEW_MAIN_BOARD = 0;

    private static final String LOG_TAG = "PUBNUB";

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

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        _wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "DoNotDimScreen");

        // needs to be called first because of chess statics init
        _view = new PubnubChessView(this);
        _view.init();

        _dlgConfirm = new PubnubConfimDialog(this);

        _tvHeader = (TextView) findViewById(R.id.TextViewHeader);

        _viewAnimatorMain = (ViewAnimator) findViewById(R.id.ViewAnimatorMain);
        _viewAnimatorMain.setOutAnimation(this, R.anim.slide_left);
        _viewAnimatorMain.setInAnimation(this, R.anim.slide_right);

        switchToBoardView();

        Log.i(LOG_TAG, "onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        _view.setConfirmMove(true);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isActive = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!bound) return;
        unbindService(serviceConnection);
        bound = false;
        //isActive = false;
        isGameCreateRequestSend = false;
    }

    @Override
    protected void onPause() {
        super.onStop();
        if (!bound) return;
        unbindService(serviceConnection);
        bound = false;
        //isActive = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "requestCode = " + requestCode + ", resultCode = " + resultCode);
        if (requestCode == SUBSCRIBE_TASK && resultCode == STATUS_FINISH) {
            String result = data.getStringExtra(PARAM_RESULT);
            parsePubnubJson(result);
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
            String gameCreate = getIntent().getStringExtra("game_create");
            if (!TextUtils.isEmpty(gameCreate)) {
                parsePubnubJson(gameCreate);
            } else {
                gameCreate = "{ game : 'create', acceptor: '',  initiator : '" + myName + "'}";
                pubnubService.publishToPubnubChannel(gameCreate);
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
        String game = null;
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(line);
            game = jsonObject.getString("game");
            if (null != game && game.equalsIgnoreCase("continue")) {
                String move = null;
                String sender = jsonObject.getString("user");
                if(sender.equalsIgnoreCase(opponentName)){
                    move = jsonObject.getString("move");
                    String[] moveArray = move.split("-");
                    Log.d(LOG_TAG, "Paint move: " + move);
                    int from = Pos.fromString(moveArray[0]);
                    int to = Pos.fromString(moveArray[1]);
                    get_view().paintMove(from, to);
                    //switchToBoardView();
                }else{
                    Log.d(LOG_TAG, "Hey, that's my move. Just ignore it.");
                }
            }else if (null != game && game.equalsIgnoreCase("create")) {
                String initiator = jsonObject.getString("initiator");
                String acceptor = jsonObject.getString("acceptor");
                get_view().setMe(myName);
                if(!TextUtils.isEmpty(acceptor) && !acceptor.equalsIgnoreCase(myName)){
                    opponentName = acceptor;
                    get_view().setOpponent(opponentName);
                    get_view().startGame(true);
                }else if(!TextUtils.isEmpty(initiator) && !initiator.equalsIgnoreCase(myName)){
                    opponentName = initiator;
                    get_view().setOpponent(opponentName);
                    get_view().startGame(false);
                    pubnubService.publishToPubnubChannel( "{ game : 'create', initiator: '" + opponentName + "', acceptor : '" + myName + "'}");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void switchToBoardView() {
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_BOARD)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_BOARD);
    }

    public void sendString(String s) {
        if (!TextUtils.isEmpty(s)) {
            pubnubService.publishToPubnubChannel(s);
        }
    }

    public PubnubChessView get_view() {
        return _view;
    }

}
