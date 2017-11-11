package ling.ai.networklingkit.smartconfig;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import ling.ai.networklingkit.WifiConfigManager;
import ling.ai.networklingkit.entity.WifiEntity;
import ling.ai.networklingkit.utils.SystemProps;


/**
 * Created by cuiqiang on 2017/9/19.
 */

public class SmartConfigRepos {

    private final static String TAG = "smartconfig";

    private Context mContext;
    private String SMARTCONFIG_STOP = "0";
    private String SMARTCONFIG_START = "1";
    private String WIFI_MESSAGE = "wifiMessage";
    private String SMARTCONFIG = "sys.start.smartconfig";
    private String SMARTCONFIG_MESSAGE = "smartconfigMessage";
    private String SMARTCONFIG_WIFI_MESSAGE = "ai.ling.luka.WIFI_MESSAGE";
    private String SMARTCONFIG_STATE_CHANGED_ACTION = "ai.ling.luka.SMARTCONFIG_MESSAGE";
    private Semaphore smartConfigSemaphore = new Semaphore(0);

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "收到消息");
            String result = intent.getStringExtra(WIFI_MESSAGE);
            if (!TextUtils.isEmpty(result)) {
                Log.d(TAG, "收到消息 " + result);
                parseResult(result);
            }
        }
    };

    public SmartConfigRepos(Context context) {
        mContext = context;// TODO: 2017/10/18 换成application的
        SystemProps.getInstance().init();
    }

    /**
     * 注册接收广播
     */
    public void startReceiver() {
        mContext.registerReceiver(receiver, new IntentFilter(SMARTCONFIG_WIFI_MESSAGE));
        Log.d(TAG, "注册网络成功");
        SystemProps.getInstance().set(SMARTCONFIG, SMARTCONFIG_START);
    }

    /**
     * 停止接收广播
     */
    public void stop(boolean needWait) {
        if (SystemProps.getInstance().get(SMARTCONFIG, "").equals(SMARTCONFIG_STOP)) {
            return;
        }
        mContext.unregisterReceiver(receiver);
        if (needWait) {
            BroadcastReceiver stopReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String result = intent.getStringExtra(SMARTCONFIG_MESSAGE);
                    smartConfigSemaphore.release();
                    Log.d(TAG, "result************* "+result);
                }
            };
            mContext.registerReceiver(stopReceiver, new IntentFilter(SMARTCONFIG_STATE_CHANGED_ACTION));
            SystemProps.getInstance().set(SMARTCONFIG, SMARTCONFIG_STOP);
            try {
                smartConfigSemaphore.tryAcquire(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mContext.unregisterReceiver(stopReceiver);
            }
        } else {
            SystemProps.getInstance().set(SMARTCONFIG, SMARTCONFIG_STOP);
        }
    }

    /**
     * 解析字符串
     *
     * @param result
     */
    private void parseOldResult(String result) {
        unregisterReceiver();
        Log.d(TAG, "解析result");
        Gson gson = new Gson();
        WifiEntity wifiEntity = gson.fromJson(result, WifiEntity.class);
        String[] wifi = wifiEntity.getWifi().split("WIFI:S:");
        String[] ssidInfo = wifi[1].split(";P:");
        String ssid = ssidInfo[0];
        String psw = ssidInfo[1].split(";;")[0];
        Log.d(TAG, "ssid " + ssid + " psw " + psw);
        String  capabilities = "";
        if (!TextUtils.isEmpty(ssid) && !TextUtils.isEmpty(psw)) {
             connectWifiAPI23(ssid,psw,capabilities);
        }
    }

    /**
     * 透传版本的解析方式
     *
     * @param result
     */
    private void parseResult(String result){
        // LingX|lingx2017|[WPA2-PSK-CCMP][WPS][ESS]|
        String[] resultSplit = result.split("\\|");
        String ssid = resultSplit[0];
        String password = resultSplit[1];
        String capabilities = resultSplit[2];
        Log.d(TAG,"ssid/psw/cap " + ssid + " / " + password + " / " + capabilities);
        if (!TextUtils.isEmpty(ssid) && !TextUtils.isEmpty(password) && !TextUtils.isEmpty(capabilities)) {
            connectWifiAPI23(ssid,password,capabilities);
        }
    }

    /**
     * 连接网络
     *
     * @param ssid      wifi名称
     * @param password  wifi密码
     */
    private void connectWifiAPI23(String ssid, String password,String capabilities) {
        Log.d(TAG, "连接网络");
        WifiConfigManager.getInstance().connectWifi(ssid, password,capabilities);
    }

    private void unregisterReceiver(){
        mContext.unregisterReceiver(receiver);
    }

}
