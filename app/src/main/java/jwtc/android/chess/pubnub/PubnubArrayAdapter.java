package jwtc.android.chess.pubnub;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

import jwtc.android.chess.R;

public class PubnubArrayAdapter extends ArrayAdapter<PubnubUser>{

    private Context context;
    private List<PubnubUser> users;

    public PubnubArrayAdapter(Context context, List<PubnubUser> users) {
        super(context, R.layout.pubnub_list_item, users);
        this.context = context;
        this.users = users;
    }

    public int getCount() {
        if (users != null)
            return users.size();
        return 0;
    }

    public PubnubUser getUser(int position) {
        if (users != null)
            return users.get(position);
        return null;
    }

    public long getUserId(int position) {
        if (users != null)
            return users.get(position).hashCode();
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.pubnub_list_item, parent, false);
        TextView userName = (TextView) rowView.findViewById(R.id.pubnubUserName);
        Button playBtn = (Button) rowView.findViewById(R.id.pubnubPlayBtn);
        PubnubUser user = users.get(position);
        userName.setText(user.getName());
        if(!user.getStatus().equals("waiting")){
            playBtn.setVisibility(View.INVISIBLE);
        }
        return rowView;
    }

    public List<PubnubUser> getUserList() {
        return users;
    }

    public void setUserList(List<PubnubUser> users) {
        this.users = users;
    }

}
