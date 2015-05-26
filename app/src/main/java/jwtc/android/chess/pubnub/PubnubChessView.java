package jwtc.android.chess.pubnub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import jwtc.android.chess.ChessViewBase;
import jwtc.android.chess.R;
import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardMembers;

public class PubnubChessView extends ChessViewBase {
    private JNI _jni;

    public JNI getJni(){
        return _jni;
    }
    private TextView _tvPlayerTop, _tvPlayerBottom, _tvClockTop, _tvClockBottom, _tvBoardNum, _tvLastMove;

    private Button _butConfirmMove, _butCancelMove;
    private ViewSwitcher _viewSwitchConfirm;
    private String _opponent;
    private String _whitePlayer;
    private String _blackPlayer;
    private String _me;
    private int m_iFrom, _iWhiteRemaining, _iBlackRemaining, _iGameNum, _iTurn, m_iTo;
    private PubnubChessActivity _parent;
    private boolean  _bHandleClick, _bOngoingGame, _bForceFlipBoard, _bConfirmMove;
    private Timer _timer;
    private static final int MSG_TOP_TIME = 1, MSG_BOTTOM_TIME = 2;
    public static final int VIEW_NONE = 0, VIEW_PLAY = 1, VIEW_WATCH = 2, VIEW_EXAMINE = 3, VIEW_PUZZLE = 4, VIEW_ENDGAME = 5;
    protected int _viewMode;

    protected Handler m_timerHandler = new Handler(){
        /** Gets called on every message that is received */
        // @Override
        public void handleMessage(Message msg) {
            if(msg.what == MSG_TOP_TIME){
                _tvClockTop.setText(parseTime(msg.getData().getInt("ticks")));
            }
            else {
                _tvClockBottom.setText(parseTime(msg.getData().getInt("ticks")));
            }
        }
    };

    public PubnubChessView(Activity activity) {
        super(activity);

        _jni = new JNI();
        _jni.reset();

        _parent = (PubnubChessActivity)activity;

        m_iFrom = -1;
        m_iTo = -1;

        _bHandleClick = false;
        _viewMode = VIEW_NONE;
        _bOngoingGame = false;
        _bForceFlipBoard = false;
        _opponent = "";
        _iTurn = BoardConstants.WHITE;
        _iWhiteRemaining = _iBlackRemaining = 0;
        _bConfirmMove = false;

        _tvPlayerTop = (TextView)_activity.findViewById(R.id.TextViewTop);
        _tvPlayerBottom = (TextView)_activity.findViewById(R.id.TextViewBottom);

        _tvClockTop = (TextView)_activity.findViewById(R.id.TextViewClockTop);
        _tvClockBottom = (TextView)_activity.findViewById(R.id.TextViewClockBottom);

        _tvBoardNum = (TextView)_activity.findViewById(R.id.TextViewICSBoardNum);
        //_tvViewMode = (TextView)_activity.findViewById(R.id.TextViewICSBoardViewMode);
        _tvLastMove = (TextView)_activity.findViewById(R.id.TextViewICSBoardLastMove);

        _butCancelMove = (Button)_activity.findViewById(R.id.ButtonPubnubCancelMove);
        _butCancelMove.setOnClickListener(new View.OnClickListener(){
            public void onClick(View arg0) {
                m_iFrom = -1;
                m_iTo = -1;
                _bHandleClick = true;
                _jni.undo();
                paint();
                // switch back
                _viewSwitchConfirm.setDisplayedChild(0);
            }
        });
        _butConfirmMove = (Button)_activity.findViewById(R.id.ButtonPubnubConfirmMove);
        _butConfirmMove.setOnClickListener(new View.OnClickListener(){
            public void onClick(View arg0) {

                _tvLastMove.setText("...");
                String sMove = Pos.toString(m_iFrom) + "-" + Pos.toString(m_iTo);
                _parent.sendString("{ game : 'continue', user : '" + _me + "', move : '" + sMove + "'}");
                m_iFrom = -1;
                // switch back
                _viewSwitchConfirm.setDisplayedChild(0);
            }
        });

        _viewSwitchConfirm = (ViewSwitcher)_activity.findViewById(R.id.ViewSitcherConfirmAndText);

        _timer = new Timer(true);
        _timer.schedule(new TimerTask(){
            @Override public void run() {
                int ticks = 0;

                if(false == _bOngoingGame)
                    return;
                if(_iTurn == BoardConstants.WHITE){
                    _iWhiteRemaining--;
                    ticks = _iWhiteRemaining;
                } else {
                    _iBlackRemaining--;
                    ticks = _iBlackRemaining;
                }

                if(ticks >= 0){
                    Message msg = new Message();

                    if(_flippedBoard){
                        msg.what = _iTurn == BoardConstants.WHITE ? MSG_TOP_TIME : MSG_BOTTOM_TIME;
                    } else {
                        msg.what = _iTurn == BoardConstants.WHITE ? MSG_BOTTOM_TIME : MSG_TOP_TIME;
                    }

                    Bundle bun = new Bundle();
                    bun.putInt("ticks", ticks);
                    msg.setData(bun);
                    m_timerHandler.sendMessage(msg);
                }
            }}, 1000, 1000);

        View.OnClickListener ocl = new View.OnClickListener() {
            public void onClick(View arg0) {
                handleClick(getFieldIndex(getIndexOfButton(arg0)));
            }
        };

        init(ocl);
    }

    public void init(){
        Log.i("init", "=========");

        m_iFrom = -1;
        m_iTo = -1;

        _bHandleClick = false;
        _bOngoingGame = false;
        _opponent = "";
        _flippedBoard = false;

        paint();
    }

    public void setViewMode(final int iMode){
        _viewMode = iMode;
        updateViewMode();
    }

    public void updateViewMode(){
        switch(_viewMode){
            case VIEW_NONE: Log.i("ICSChessView", "Idle"); break;
            case VIEW_PLAY: Log.i("ICSChessView", "Play"); break;
            case VIEW_WATCH: Log.i("ICSChessView", "Watch"); break;
            case VIEW_EXAMINE: Log.i("ICSChessView", "Examine"); break;
            case VIEW_PUZZLE: Log.i("ICSChessView", "Puzzle"); break;
            case VIEW_ENDGAME: Log.i("ICSChessView", "Endgame"); break;
            default: Log.i("ICSChessView", "X");
        }
    }

    public boolean isUserPlaying(){
        return _viewMode == VIEW_PLAY;
    }

    public void setConfirmMove(boolean b){
        _bConfirmMove = b;
    }

    public void paintMove(int from, int to){
        m_iTo = to;
        m_iFrom = from;
        _jni.requestMove(from, to);
        _bHandleClick = true;
        paint();
        m_iFrom = -1;
        m_iTo = -1;
    }

    public void startGame(boolean iStart){
        _jni.newGame();
        _iWhiteRemaining = 600;
        _iBlackRemaining = 600;
        if(iStart){
            _flippedBoard = false;
            _whitePlayer = _me;
            _blackPlayer = _opponent;
        }else{
            _flippedBoard = true;
            _whitePlayer = _opponent;
            _blackPlayer = _me;
        }

        if(_flippedBoard){
            _tvPlayerTop.setText(_me);
            _tvPlayerBottom.setText(_opponent);
            _tvClockTop.setText(parseTime(_iWhiteRemaining));
            _tvClockBottom.setText(parseTime(_iBlackRemaining));
        } else {
            _tvPlayerTop.setText(_me);
            _tvPlayerBottom.setText(_opponent);
            _tvClockTop.setText(parseTime(_iBlackRemaining));
            _tvClockBottom.setText(parseTime(_iWhiteRemaining));
        }
        _bHandleClick = true;
        setConfirmMove(true);
        setViewMode(PubnubChessView.VIEW_PLAY);
        paint();
    }

    private String parseTime(int sec){
        return String.format("%d:%02d", (int)(Math.floor(sec/60)), sec % 60);
    }

    private void paint(){
        paintBoard(_jni, new int[]{m_iFrom, m_iTo}, null);
    }

    public void handleClick(int index){
        if(_bHandleClick){
            m_iTo = -1;
            if(m_iFrom == -1)
            {
                if(_jni.pieceAt(_jni.getTurn(), index) == BoardConstants.FIELD)
                {
                    return;
                }
                m_iFrom = index;
                paint();
            }
            else
            {
                boolean isCastle = false;

                if(_jni.isAmbiguousCastle(m_iFrom, index) != 0){ // in case of Fischer

                    isCastle = true;

                } else if(index == m_iFrom){
                    m_iFrom = -1;
                    return;
                }
                // if valid move
                // collect legal moves if pref is set
                boolean isValid = false;
                int move = -1;
                try{
                    // via try catch because of empty or mem error results in exception

                    if(_jni.isEnded() == 0){
                        synchronized(this) {
                            int size = _jni.getMoveArraySize();
                            //Log.i("paintBoard", "# " + size);

                            boolean isPromotion = false;

                            for(int i = 0; i < size; i++){
                                move = _jni.getMoveArrayAt(i);
                                if(Move.getFrom(move) == m_iFrom){
                                    if(Move.getTo(move) == index){
                                        isValid = true;

                                        // check if it is promotion
                                        if(		_jni.pieceAt(BoardConstants.WHITE, m_iFrom) == BoardConstants.PAWN &&
                                                BoardMembers.ROW_TURN[BoardConstants.WHITE][m_iFrom] == 6 &&
                                                BoardMembers.ROW_TURN[BoardConstants.WHITE][index] == 7
                                                ||
                                                _jni.pieceAt(BoardConstants.BLACK, m_iFrom) == BoardConstants.PAWN &&
                                                        BoardMembers.ROW_TURN[BoardConstants.BLACK][m_iFrom] == 6 &&
                                                        BoardMembers.ROW_TURN[BoardConstants.BLACK][index] == 7){

                                            isPromotion = true;

                                        }

                                        break;
                                    }
                                }
                            }

                            if(isPromotion){
                                final String[] items = _parent.getResources().getStringArray(R.array.promotionpieces);
                                final int finalIndex = index;

                                AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
                                builder.setTitle(R.string.title_pick_promo);
                                builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int item) {
                                        dialog.dismiss();
                                        _jni.setPromo(4 - item);
                                        String[] arrPromos = {"q", "r", "b", "n"};
                                        _parent.sendString("promote " + arrPromos[item]);
                                        int move, size = _jni.getMoveArraySize();
                                        for(int i = 0; i < size; i++){
                                            move = _jni.getMoveArrayAt(i);
                                            if(Move.getFrom(move) == m_iFrom){
                                                if(Move.getTo(move) == finalIndex && Move.getPromotionPiece(move) == (4-item)){

                                                    continueMove(finalIndex, true, move);
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                });
                                AlertDialog alert = builder.create();
                                alert.show();

                                return ;
                            }

                            if(isCastle){

                                final int finalIndex = index;

                                AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
                                builder.setTitle(R.string.title_castle);
                                builder.setPositiveButton(R.string.alert_yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int item) {
                                        dialog.dismiss();

                                        int move, size = _jni.getMoveArraySize();
                                        for(int i = 0; i < size; i++){
                                            move = _jni.getMoveArrayAt(i);
                                            if(Move.getFrom(move) == m_iFrom){
                                                if(Move.getTo(move) == finalIndex && (Move.isOO(move) || Move.isOOO(move))){
                                                    continueMove(finalIndex, true, move);
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                });
                                builder.setNegativeButton(R.string.alert_no, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int item) {
                                        dialog.dismiss();
                                        if(m_iFrom != finalIndex){
                                            int move, size = _jni.getMoveArraySize();
                                            for(int i = 0; i < size; i++){
                                                move = _jni.getMoveArrayAt(i);
                                                if(Move.getTo(move) == finalIndex && (false == Move.isOO(move)) && (false == Move.isOOO(move))){
                                                    continueMove(finalIndex, true, move);
                                                    return;
                                                }
                                            }
                                        } else {
                                            m_iFrom = -1;
                                        }
                                    }
                                });
                                AlertDialog alert = builder.create();
                                alert.show();

                                return;
                            }

                        }
                    }
                } catch(Exception e){
                    System.gc();
                }

                continueMove(index, isValid, move);
            }
        }
    }
    private void continueMove(int index, boolean isValid, int move){
        if(isValid){
            _bHandleClick = false;
            // if confirm and is playing, first let user confirm
            if(_bConfirmMove && isUserPlaying()){

                _tvLastMove.setText("");
                //
                m_iTo = index;
                _viewSwitchConfirm.setDisplayedChild(1);

                _jni.move(move);
                paint();

            } else {
                //_tvLastMove.setText("...");
                // test and make move if valid move
                //
                String sMove = "";
                if(Move.isOO(move)){
                    sMove = "0-0";
                } else if(Move.isOOO(move)){
                    sMove = "0-0-0";
                } else {
                    sMove = Pos.toString(m_iFrom) + "-" + Pos.toString(index);
                }
                //_jni.requestMove(m_iFrom, index);
                _parent.sendString("{ game : 'continue', move : '" + sMove + "'}");
                m_iTo = index;
                paint();
                m_iFrom = -1;
            }
        } else {
            m_iFrom = -1;
            // show that move is invalid
            _tvLastMove.setText("invalid");
        }
    }

    public void setMe(String _me) {
        this._me = _me;
    }

    public void setOpponent(String _opponent) {
        this._opponent = _opponent;
    }
}
