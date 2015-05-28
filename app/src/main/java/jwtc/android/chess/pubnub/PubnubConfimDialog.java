package jwtc.android.chess.pubnub;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import jwtc.android.chess.R;

public class PubnubConfimDialog extends Dialog {

    private PubnubChessActivity _parent;
    private String _sendString;
    private TextView _tvText;

    public PubnubConfimDialog(Context context) {
        super(context);

        _parent = (PubnubChessActivity)context;

        setContentView(R.layout.icsconfirm);

        setCanceledOnTouchOutside(true);

        _tvText = (TextView)findViewById(R.id.TextViewConfirm);

        Button butYes = (Button)findViewById(R.id.ButtonConfirmYes);
        butYes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                try {
                    _parent.sendJsonToPubnub(new JSONObject(_sendString));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                dismiss();
            }
        });
        Button butNo = (Button)findViewById(R.id.ButtonConfirmNo);
        butNo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                dismiss();
            }
        });
    }

    public void setText(String sTitle, String sText){
        setTitle(sTitle);
        _tvText.setText(sText);
    }
    public void setSendString(String s){
        _sendString = s;
    }
}
