package ling.ai.networklingkit.entity;

/**
 * Created by cuiqiang on 2017/9/19.
 */

public class WifiEntity {


    /**
     * encoded_uid :
     * wifi : WIFI:S:LingX;P:lingx2017;;
     */

    private String encoded_uid;
    private String wifi;

    public String getEncoded_uid() {
        return encoded_uid;
    }

    public void setEncoded_uid(String encoded_uid) {
        this.encoded_uid = encoded_uid;
    }

    public String getWifi() {
        return wifi;
    }

    public void setWifi(String wifi) {
        this.wifi = wifi;
    }

    @Override
    public String toString() {
        return "WifiEntity{" +
                "encoded_uid='" + encoded_uid + '\'' +
                ", wifi='" + wifi + '\'' +
                '}';
    }
}
