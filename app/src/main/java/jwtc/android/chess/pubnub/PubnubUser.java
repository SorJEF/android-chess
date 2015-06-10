package jwtc.android.chess.pubnub;

import android.os.Parcel;
import android.os.Parcelable;

public class PubnubUser implements Parcelable{

    private String name;
    private String status;

    public PubnubUser(){
    }

    private PubnubUser(Parcel in) {
        name = in.readString();
        status = in.readString();
    }

    public static final Creator<PubnubUser> CREATOR =
            new Creator<PubnubUser>() {
                @Override
                public PubnubUser createFromParcel(Parcel source) {
                    return new PubnubUser(source);
                }
                @Override
                public PubnubUser[] newArray(int size) {
                    return new PubnubUser[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(name);
        parcel.writeString(status);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if(o == null) return false;
        PubnubUser user = (PubnubUser) o;
        return user.getName().equalsIgnoreCase(name);
    }
}
