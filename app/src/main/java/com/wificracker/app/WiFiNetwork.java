package com.wificracker.app;

import android.os.Parcel;
import android.os.Parcelable;

public class WiFiNetwork implements Parcelable {
    private String ssid;
    private String bssid;
    private int signalStrength;
    private String capabilities;
    private boolean isSelected;

    public WiFiNetwork(String ssid, String bssid, int signalStrength, String capabilities) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.signalStrength = signalStrength;
        this.capabilities = capabilities;
        this.isSelected = false;
    }

    protected WiFiNetwork(Parcel in) {
        ssid = in.readString();
        bssid = in.readString();
        signalStrength = in.readInt();
        capabilities = in.readString();
        isSelected = in.readByte() != 0;
    }

    public static final Creator<WiFiNetwork> CREATOR = new Creator<WiFiNetwork>() {
        @Override
        public WiFiNetwork createFromParcel(Parcel in) {
            return new WiFiNetwork(in);
        }

        @Override
        public WiFiNetwork[] newArray(int size) {
            return new WiFiNetwork[size];
        }
    };

    public String getSsid() {
        return ssid;
    }

    public String getBssid() {
        return bssid;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(ssid);
        dest.writeString(bssid);
        dest.writeInt(signalStrength);
        dest.writeString(capabilities);
        dest.writeByte((byte) (isSelected ? 1 : 0));
    }

    @Override
    public String toString() {
        return ssid + " (" + signalStrength + " dBm)";
    }
} 