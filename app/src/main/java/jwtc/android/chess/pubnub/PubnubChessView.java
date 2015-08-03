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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jwtc.android.chess.ChessViewBase;
import jwtc.android.chess.R;
import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

public class PubnubChessView extends ChessViewBase {
    private JNI jni;
    private static final String LOG_TAG = "PUBNUB";
    private List<PGNEntry> arrPGN;
    private HashMap<String, String> mapPGNHead;
    private TextView tvPlayerTop, tvPlayerBottom, tvClockTop, tvClockBottom, tvLastMove;
    private ViewSwitcher viewSwitchConfirm;
    private String opponent, me, whitePlayer, blackPlayer, gameId;
    private int mFrom, iWhiteRemaining, iBlackRemaining, iTurn, mTo;
    private PubnubChessActivity parent;
    private boolean bHandleClick, bOngoingGame, bConfirmMove;
    private static final int MSG_TOP_TIME = 1, MSG_BOTTOM_TIME = 2;
    public static final int VIEW_NONE = 0, VIEW_PLAY = 1, VIEW_WATCH = 2, VIEW_EXAMINE = 3, VIEW_PUZZLE = 4, VIEW_ENDGAME = 5;
    protected int viewMode;
    private int moveSeq;

    protected Handler mTimerHandler = new Handler() {
        /** Gets called on every message that is received */
        // @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_TOP_TIME) {
                tvClockTop.setText(parseTime(msg.getData().getInt("ticks")));
            } else {
                tvClockBottom.setText(parseTime(msg.getData().getInt("ticks")));
            }
        }
    };

    public PubnubChessView(Activity activity) {
        super(activity);

        jni = new JNI();
        jni.reset();

        parent = (PubnubChessActivity) activity;
        arrPGN = new ArrayList<PGNEntry>();
        mapPGNHead = new HashMap<String, String>();

        mFrom = -1;
        mTo = -1;

        bHandleClick = false;
        viewMode = VIEW_NONE;
        bOngoingGame = false;
        opponent = "";
        iTurn = BoardConstants.WHITE;
        iWhiteRemaining = iBlackRemaining = 0;
        bConfirmMove = false;

        tvPlayerTop = (TextView) _activity.findViewById(R.id.TextViewTop);
        tvPlayerBottom = (TextView) _activity.findViewById(R.id.TextViewBottom);

        tvClockTop = (TextView) _activity.findViewById(R.id.TextViewClockTop);
        tvClockBottom = (TextView) _activity.findViewById(R.id.TextViewClockBottom);

        tvLastMove = (TextView) _activity.findViewById(R.id.TextViewICSBoardLastMove);

        Button butCancelMove = (Button) _activity.findViewById(R.id.ButtonPubnubCancelMove);
        butCancelMove.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                mFrom = -1;
                mTo = -1;
                bHandleClick = true;
                jni.undo();
                paint();
                // switch back
                viewSwitchConfirm.setDisplayedChild(0);
            }
        });
        Button butConfirmMove = (Button) _activity.findViewById(R.id.ButtonPubnubConfirmMove);
        butConfirmMove.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                tvLastMove.setText("...");
                String sMove = Pos.toString(mFrom) + "-" + Pos.toString(mTo);
                try {
                    parent.sendJsonToPubnub(new JSONObject("{ game : 'continue', gameId: '" + gameId + "', moveSeq: '" + moveSeq + "', user : '" + me + "', move : '" + sMove + "'}"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mFrom = -1;
                // switch back
                viewSwitchConfirm.setDisplayedChild(0);
            }
        });

        viewSwitchConfirm = (ViewSwitcher) _activity.findViewById(R.id.ViewSwitcherConfirmAndText);

        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                int ticks;

                if (!bOngoingGame)
                    return;
                if (iTurn == BoardConstants.WHITE) {
                    iWhiteRemaining--;
                    ticks = iWhiteRemaining;
                } else {
                    iBlackRemaining--;
                    ticks = iBlackRemaining;
                }

                if (ticks >= 0) {
                    Message msg = new Message();

                    if (_flippedBoard) {
                        msg.what = iTurn == BoardConstants.WHITE ? MSG_TOP_TIME : MSG_BOTTOM_TIME;
                    } else {
                        msg.what = iTurn == BoardConstants.WHITE ? MSG_BOTTOM_TIME : MSG_TOP_TIME;
                    }

                    Bundle bun = new Bundle();
                    bun.putInt("ticks", ticks);
                    msg.setData(bun);
                    mTimerHandler.sendMessage(msg);
                }
            }
        }, 1000, 1000);

        View.OnClickListener ocl = new View.OnClickListener() {
            public void onClick(View arg0) {
                handleClick(getFieldIndex(getIndexOfButton(arg0)));
            }
        };
        init(ocl);
    }

    public void init() {
        Log.i("init", "=========");
        mFrom = -1;
        mTo = -1;
        moveSeq = 0;
        bHandleClick = false;
        bOngoingGame = false;
        opponent = "";
        _flippedBoard = false;
        paint();
    }

    public void setViewMode(final int iMode) {
        viewMode = iMode;
        updateViewMode();
    }

    public void updateViewMode() {
        switch (viewMode) {
            case VIEW_NONE:
                Log.i("ICSChessView", "Idle");
                break;
            case VIEW_PLAY:
                Log.i("ICSChessView", "Play");
                break;
            case VIEW_WATCH:
                Log.i("ICSChessView", "Watch");
                break;
            case VIEW_EXAMINE:
                Log.i("ICSChessView", "Examine");
                break;
            case VIEW_PUZZLE:
                Log.i("ICSChessView", "Puzzle");
                break;
            case VIEW_ENDGAME:
                Log.i("ICSChessView", "Endgame");
                break;
            default:
                Log.i("ICSChessView", "X");
        }
    }

    public boolean isUserPlaying() {
        return viewMode == VIEW_PLAY;
    }

    public void setConfirmMove(boolean b) {
        bConfirmMove = b;
    }

    private void parseMove(String move) {
        String[] moveArray = move.split("-");
        mFrom = Pos.fromString(moveArray[0]);
        mTo = Pos.fromString(moveArray[1]);
    }

    public void paintMove(String move) {
        Log.d(LOG_TAG, "Paint move: " + move);
        parseMove(move);
        resetImageCache();
        jni.requestMove(mFrom, mTo);
        addPGNEntry(jni.getMyMoveToString(), "", jni.getMove());
        bHandleClick = true;
        int state = jni.getState();
        paint();
        checkGameState(state);
        mFrom = -1;
        mTo = -1;
        moveSeq += 2;
    }

    private void checkGameState(int state) {
        try{
            switch (state) {
                case ChessBoard.MATE:
                    tvPlayerBottom.setText(R.string.state_mate);
                    setPGNHeadProperty("Result", "0-1"); // If this switch execute - that's mean I lose. That's why 0-1, not 1-0.
                    JSONObject pgnMateJson = exportJsonPGN();
                    pgnMateJson.put("winner", opponent);
                    parent.sendJsonToPubnub(pgnMateJson);
                    new AlertDialog.Builder(parent)
                            .setTitle("Oh, that's mate!")
                            .setMessage("Game PGN:")
                            .setMessage(exportFullPGN())
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
                    break;
                case ChessBoard.CHECK:
                    tvPlayerBottom.setText(R.string.state_check);
                    break;
                case ChessBoard.DRAW_50:
                case ChessBoard.DRAW_MATERIAL:
                case ChessBoard.DRAW_REPEAT:
                case ChessBoard.STALEMATE:
                    tvPlayerBottom.setText(R.string.state_draw);
                    setPGNHeadProperty("Result", "1-1");
                    JSONObject pgnDrawJson = exportJsonPGN();
                    pgnDrawJson.put("winner", JSONObject.NULL);
                    parent.sendJsonToPubnub(pgnDrawJson);
                    new AlertDialog.Builder(parent)
                            .setTitle("Oh, that's draw!")
                            .setMessage("Game PGN:")
                            .setMessage(exportFullPGN())
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
                default:
                    break;
            }
        }catch(JSONException e){
        }
    }

    private void initPGNHead() {
        mapPGNHead.clear();
        Date d = Calendar.getInstance().getTime();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
        setPGNHeadProperty("Date", formatter.format(d));
        setPGNHeadProperty("Event", me + " vs. " + opponent);
        setPGNHeadProperty("White", whitePlayer);
        setPGNHeadProperty("Black", blackPlayer);
        arrPGN.clear();
    }

    public void startGame(boolean iStart) {
        jni.newGame();
        iWhiteRemaining = 600;
        iBlackRemaining = 600;
        if (iStart) {
            moveSeq = 1;
            _flippedBoard = false;
            whitePlayer = me;
            blackPlayer = opponent;
        } else {
            moveSeq = 0;
            _flippedBoard = true;
            whitePlayer = opponent;
            blackPlayer = me;
        }
        if (_flippedBoard) {
            tvPlayerTop.setText(whitePlayer);
            tvPlayerBottom.setText(blackPlayer);
            tvClockTop.setText(parseTime(iWhiteRemaining));
            tvClockBottom.setText(parseTime(iBlackRemaining));
        } else {
            tvPlayerTop.setText(blackPlayer);
            tvPlayerBottom.setText(whitePlayer);
            tvClockTop.setText(parseTime(iBlackRemaining));
            tvClockBottom.setText(parseTime(iWhiteRemaining));
        }
        initPGNHead();
        bHandleClick = true;
        setConfirmMove(true);
        setViewMode(PubnubChessView.VIEW_PLAY);
        paint();
    }

    private String parseTime(int sec) {
        return String.format("%d:%02d", (int) (Math.floor(sec / 60)), sec % 60);
    }

    private void paint() {
        paintBoard(jni, new int[]{mFrom, mTo}, null);
    }

    public void addPGNEntry(String sMove, String sAnnotation, int move) {
        arrPGN.add(new PGNEntry(sMove, sAnnotation, move));
    }

    private String exportFullPGN() {
        String[] arrHead = {"Event", "Site", "Date", "Round", "White", "Black", "Result", "EventDate",
                "Variant", "Setup", "FEN", "PlyCount"};
        String s = "", key;
        for (String anArrHead : arrHead) {
            key = anArrHead;
            if (mapPGNHead.containsKey(key))
                s += "[" + key + " \"" + mapPGNHead.get(key) + "\"]\n";
        }
        s += exportMovesPGN();
        s += "\n";
        return s;
    }

    private JSONObject exportJsonPGN() {
        JSONObject pgn = new JSONObject();
        String[] arrHead = {"Event", "Site", "Date", "Round", "White", "Black", "Result", "EventDate", "Variant", "Setup", "FEN", "PlyCount"};
        try {
            for (String key : arrHead) {
                if (mapPGNHead.containsKey(key)) {
                    if(key.equalsIgnoreCase("Result")){
                        pgn.put("gameResult", mapPGNHead.get(key));
                    }else{
                        pgn.put(key.toLowerCase(), mapPGNHead.get(key));
                    }
                }
            }
            JSONArray moves = new JSONArray();
            for (int i = 0; i < arrPGN.size(); i = i + 2) {
                JSONArray tmp = new JSONArray();
                tmp.put(arrPGN.get(i)._sMove);
                if (i + 1 < arrPGN.size()) {
                    tmp.put(arrPGN.get(i + 1)._sMove);
                }
                moves.put(tmp);
            }
            pgn.put("moves", moves);
            pgn.put("game", "end");
            pgn.put("gameId", gameId);
            pgn.put("sendTo", opponent);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Can't convert pgn to json: " + e.toString());
        }
        return pgn;
    }

    private String exportMovesPGN() {
        return exportMovesPGNFromPly(1);
    }

    private String exportMovesPGNFromPly(int iPly) {
        String s = "";
        if (iPly > 0) {
            iPly--;
        }
        if (iPly < 0) {
            iPly = 0;
        }
        for (int i = iPly; i < arrPGN.size(); i++) {
            if ((i - iPly) % 2 == 0)
                s += ((i - iPly) / 2 + 1) + ". ";
            s += arrPGN.get(i)._sMove + " ";
            if (arrPGN.get(i)._sAnnotation.length() > 0)
                s += " {" + arrPGN.get(i)._sAnnotation + "}\n ";
        }
        return s;
    }

    public void handleClick(int index) {
        if (bHandleClick) {
            mTo = -1;
            if (mFrom == -1) {
                if (jni.pieceAt(jni.getTurn(), index) == BoardConstants.FIELD) {
                    return;
                }
                mFrom = index;
                paint();
            } else {
                boolean isCastle = false;
                if (jni.isAmbiguousCastle(mFrom, index) != 0) { // in case of Fischer

                    isCastle = true;
                } else if (index == mFrom) {
                    mFrom = -1;
                    return;
                }
                // if valid move
                // collect legal moves if pref is set
                boolean isValid = false;
                int move = -1;
                try {
                    // via try catch because of empty or mem error results in exception
                    if (jni.isEnded() == 0) {
                        synchronized (this) {
                            int size = jni.getMoveArraySize();
                            for (int i = 0; i < size; i++) {
                                move = jni.getMoveArrayAt(i);
                                if (Move.getFrom(move) == mFrom) {
                                    if (Move.getTo(move) == index) {
                                        isValid = true;
                                        break;
                                    }
                                }
                            }
                            if (isCastle) {
                                final int finalIndex = index;
                                AlertDialog.Builder builder = new AlertDialog.Builder(parent);
                                builder.setTitle(R.string.title_castle);
                                builder.setPositiveButton(R.string.alert_yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int item) {
                                        dialog.dismiss();
                                        int move, size = jni.getMoveArraySize();
                                        for (int i = 0; i < size; i++) {
                                            move = jni.getMoveArrayAt(i);
                                            if (Move.getFrom(move) == mFrom) {
                                                if (Move.getTo(move) == finalIndex && (Move.isOO(move) || Move.isOOO(move))) {
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
                                        if (mFrom != finalIndex) {
                                            int move, size = jni.getMoveArraySize();
                                            for (int i = 0; i < size; i++) {
                                                move = jni.getMoveArrayAt(i);
                                                if (Move.getTo(move) == finalIndex && (!Move.isOO(move)) && (!Move.isOOO(move))) {
                                                    continueMove(finalIndex, true, move);
                                                    return;
                                                }
                                            }
                                        } else {
                                            mFrom = -1;
                                        }
                                    }
                                });
                                AlertDialog alert = builder.create();
                                alert.show();
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.gc();
                }
                continueMove(index, isValid, move);
            }
        }
    }

    private void continueMove(int index, boolean isValid, int move) {
        if (isValid) {
            bHandleClick = false;
            // if confirm and is playing, first let user confirm
            if (bConfirmMove && isUserPlaying()) {
                tvLastMove.setText("");
                mTo = index;
                viewSwitchConfirm.setDisplayedChild(1);
                jni.move(move);
                addPGNEntry(jni.getMyMoveToString(), "", jni.getMyMove());
                paint();
            }
        } else {
            mFrom = -1;
            // show that move is invalid
            tvLastMove.setText("invalid");
        }
    }

    private void setPGNHeadProperty(String sProp, String sValue) {
        mapPGNHead.put(sProp, sValue);
    }

    public void setMe(String _me) {
        this.me = _me;
    }

    public void setOpponent(String _opponent) {
        this.opponent = _opponent;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
}
