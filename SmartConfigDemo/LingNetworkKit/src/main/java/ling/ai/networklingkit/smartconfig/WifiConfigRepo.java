package ling.ai.networklingkit.smartconfig;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import ling.ai.networklingkit.exception.WifiConfigException;

/**
 * Created by cuiqiang on 2017/9/18.
 */

public class WifiConfigRepo {

    private static final String TAG = "Ling";
    private Context mContext;
    private WifiManager wifiManager;
    private Semaphore wifiEnabledSemaphore = new Semaphore(0);
    private Semaphore wifiScanSemaphore = new Semaphore(0);
    private Semaphore wifiConnectSemaphore = new Semaphore(0);
    private Pattern HEX_PATTERN = Pattern.compile("[0-9A-Fa-f]+");

    public WifiConfigRepo(Context context){
        this.mContext = context;
        this.wifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
    }

    public void connect(String ssid,String pwd){
         if (!changeWifiState(true)){
            Log.d(TAG,"wifiRepo, can not enable Wi-Fi");
         }
         try{
             //这句话主要用于扫描Wi-Fi，更新列表
             findWifiBySsid(ssid, true);
             List<ScanResult> scanResults = wifiManager.getScanResults();
             if (null != scanResults) {
                 for (ScanResult scanResult : scanResults) {
                     Log.d(TAG, "wifiRepo, scanResult " + scanResult);
                 }
             }
         }catch (Exception ex){
             ex.printStackTrace();
         }
         Log.d(TAG, "wifiRepo, Wi-Fi enabled");

         //尝试连接显示Wi-Fi
         try{
             connectWifi(ssid, pwd, false);
             Log.d(TAG,"wifiRepo, connect to $ssid success");
             return;
         }catch (Exception ex){
             ex.printStackTrace();
         }
        //连接出错，如果是密码错误，系统会保持当前的错误连接，不会再连接之前连接过的Wi-Fi
        //这个时候，需要断开当前连接，让系统寻找可用的连接，最大可能的保持网络畅通

        //尝试连接隐藏Wi-Fi
        try {
            connectWifi(ssid, pwd,true);
            return;
        } catch (Exception ex) {
            //todo:因为sdk版本问题，暂时不支持 addSuppressed，所以先使用 errorLog
            ex.printStackTrace();
        }
        //todo:可以考虑要不要删除当前错误的配置
        Log.d(TAG,"wifiRepo, disconnect and then reconnect");
        wifiManager.disconnect();
        wifiManager.reconnect();

        //没有扫描到Wi-Fi
        if (findWifiBySsid(ssid,false).isEmpty()) {
            //如果最终没有连接成功，可能原因有两种：要不是 Wi-Fi不存在，要不就是密码错误
            //这里只需要处理Wi-Fi不存在的情况。如果Wi-Fi存在，前面已经处理了
            Log.d(TAG,"没有扫描到Wi-Fi");
        }
    }

    private boolean changeWifiState(boolean enabled) {
        if (enabled ==  wifiManager.isWifiEnabled()){
            return true;
        }else{
            //刚注册广播的时候，就会产生一次回调，值为注册前的最后一次（最新）的广播
            //所以需要过滤第一次的接收值
            BroadcastReceiver  wifiChangedReceiver = new BroadcastReceiver(){
                int count = 0;
                @Override
                public void onReceive(Context context, Intent intent) {
                    count++;
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                    Log.d(TAG,"wifiRepo, wifi state changed: "+wifiState);
                    if (count!=1){
                        switch(wifiState){
                             case WifiManager.WIFI_STATE_ENABLED:{
                                break;
                             }
                             case WifiManager.WIFI_STATE_DISABLED :{
                                wifiEnabledSemaphore.release();
                                break;
                             }
                             default:
                                break;
                        }
                    }
                }
            };
            mContext.registerReceiver(wifiChangedReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
            wifiManager.setWifiEnabled(enabled);
            try {
                wifiEnabledSemaphore.tryAcquire(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mContext.unregisterReceiver(wifiChangedReceiver);
            }
            return enabled == wifiManager.isWifiEnabled();
        }
    }

    /**
     * 激活所有配置过的网络，以便系统自动连接
     */
    private void enableAllConfiguredNetworks(){
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks!=null) {
            for (WifiConfiguration configuredNetwork : configuredNetworks) {
                wifiManager.enableNetwork(configuredNetwork.networkId, false);
            }
        }
    }

    private List<ScanResult> findWifiBySsid(String ssid, boolean refreshIfNotFound) {
        List<ScanResult> wifiList = new ArrayList();
        //先读取当前的扫描列表中是否包含给定的ssid
        List<ScanResult> resultList = wifiManager.getScanResults();
        if (resultList!=null || !wifiList.isEmpty()) {
            for (int i = 0; i < resultList.size(); i++) {
                if (!ssid.equals(resultList.get(i).SSID)) {
                    resultList.remove(i);
                }
            }
            wifiList.addAll(resultList);
        }
        //如果Wi-Fi列表中没有找到指定的ssid，则重新扫描一遍
        if (wifiList.isEmpty() && refreshIfNotFound) {
            Log.d(TAG, "wifiRepo, can't find specific ssid, startReceiver scan");
            BroadcastReceiver scanReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    wifiScanSemaphore.release();
                }
            };
            mContext.registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            wifiManager.startScan();
            Log.d(TAG, "wifiRepo, wait scan result");
            try {
                boolean b = wifiScanSemaphore.tryAcquire(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mContext.unregisterReceiver(scanReceiver);
            }
            //先读取当前的扫描列表中是否包含给定的ssid
            List<ScanResult> resultLists = wifiManager.getScanResults();
            for (int i = 0; i < resultLists.size(); i++) {
                if (!ssid.equals(resultLists.get(i).SSID)){
                    resultLists.remove(i);
                }
            }
            wifiList.addAll(resultLists);
        }
        return wifiList;
    }

    private void connectWifi(String ssid, String pwd,ScanResult scanResult,boolean isHidden) throws WifiConfigException {
        Log.d(TAG,"connectWifi2");
        WifiConfiguration config = generateNewConfig(ssid, scanResult.BSSID, isHidden);
        EncryptionType encryptionType  = EncryptionType.parse(scanResult.capabilities);
        config = updateConfig(config, pwd, encryptionType);
        try {
            connectNetwork(config);
            //连接成功，激活以前（连接并保存）的网络配置
            enableAllConfiguredNetworks();
            return;
        }catch (Throwable ex){
            throw new WifiConfigException("connect ssid [bssid: {scanResult.BSSID}] failed", ex);
        }
    }

    private void connectWifi(String ssid, String pwd,boolean isHidden) throws WifiConfigException {
        Log.d(TAG,"connectWifi");
        String mSsid;
        if (isHidden){
            mSsid = "";
        }else{
            mSsid = ssid;
        }
        List<ScanResult> wifiBySsid = findWifiBySsid(mSsid, true);
        if (null != wifiBySsid) {
            for (ScanResult scanResult : wifiBySsid) {
                // Log.d(TAG,"scanResult");
                connectWifi(ssid, pwd, scanResult, isHidden);
                return;
            }
        }
        //不管连接是否成功，都应该激活以前（连接并保存）的网络配置
        enableAllConfiguredNetworks();
        throw new WifiConfigException("list");
    }

    private boolean connectNetwork(final WifiConfiguration config) {
        Log.d(TAG,"connectNetwork");
       //连接前先查找原有的配置，判断以前是否连接过该Wi-Fi
        //一定要通过config.SSID来查找，因为有双引号的区别
        wifiManager.disconnect();
        WifiConfiguration wifiConfiguration = findExistedConfigBySsid(config.SSID);
        if (wifiConfiguration!= null){
            int networkId = wifiConfiguration.networkId;
            wifiManager.removeNetwork(networkId);
            wifiManager.saveConfiguration();
        }
        int networkId = wifiManager.addNetwork(config);
        if (networkId == -1){
            //添加网络失败
            Log.d(TAG,"添加网络失败");
        }
        //激活配置并保存，此时不需要关闭其他已保存的配置
        boolean result = wifiManager.enableNetwork(networkId, false)
                && wifiManager.saveConfiguration();

       WifiConfiguration wifiConfigurationAgain = findExistedConfigBySsid(config.SSID);
       if (wifiConfigurationAgain!= null) {
           //考虑到 saveConfiguration() 操作会改变 networkId，所以这里再重新获取一遍
           networkId = wifiConfigurationAgain.networkId;
       }else{
           //保存网络失败
           networkId = -1;
           Log.d(TAG,"保存网络失败");
       }
       if (result){
           //连接时先关闭其他的网络配置
           //这里使用reassociate()方法，表示不管当前是否连接着Wi-Fi，都强制重连
           result = wifiManager.enableNetwork(networkId, true)
                   && wifiManager.reassociate();
       }
       if (!result){
           //如果操作都不成功，则直接返回
           Log.d(TAG,"wifiRepo, configure or connect network failed");
       }
        //等待网络连接
        //刚注册广播的时候，就会产生一次回调，值为注册前的最后一次（最新）的广播
        //在这样的情况下会产生错误状态：在此次连接前，已经连接上了Wi-Fi，产生了 COMPLETED 广播，然后此时注册广播，就会马上收到该广播值，这个时候就退出了接收，但这个广播是假的
        //所以需要过滤第一次的接收值
        BroadcastReceiver receiver =new BroadcastReceiver(){
            int count = 0;
            @Override
            public void onReceive(Context context, Intent intent) {
                count++;
                SupplicantState supplicantState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                if (count != 1) {
                    if ((connectionInfo.getSupplicantState() == SupplicantState.DISCONNECTED
                            || connectionInfo.getSupplicantState() == SupplicantState.COMPLETED
                            || connectionInfo.getSupplicantState() == SupplicantState.INACTIVE)
                            && connectionInfo.getSSID().equals(config.SSID)) {
                        wifiConnectSemaphore.release();
                    }
                }
            }
        };
        mContext.registerReceiver(receiver,new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
        try {
            boolean acquire = wifiConnectSemaphore.tryAcquire(25, TimeUnit.SECONDS);
            result = wifiManager.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED;
            //当等待超时了，但如果这个时候连接成功了，因为我们的目的是联网，所以就不抛出超时异常，走联网成功的结果
            if (!acquire && !result) {
                Log.d(TAG,"connect network timeout");
            } else if (!result) {
                Log.d(TAG,"Wi-Fi password error");
            }
        }catch (Exception ex){} finally {
            mContext.unregisterReceiver(receiver);
        }
        //等待获取ip地址
        int tryCount = 0;
        int maxTryCount = 100;
        WifiInfo connectionInfo = wifiManager.getConnectionInfo();
        if (null!=connectionInfo) {
            while (connectionInfo.getIpAddress() == 0 && tryCount++ < maxTryCount) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }


    /**
     * bssid一旦被设置，如果将要连接的Wi-Fi能够获取到bssid，则ssid配置将无效
     * 备注：bssid暂时不使用，留待后用
     *
     * @param bssid When set, this network configuration entry should only be used when
     * associating with the AP having the specified BSSID.
     */
    private WifiConfiguration generateNewConfig(String ssid, String bssid, boolean isHidden) {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.allowedAuthAlgorithms.clear();
        wifiConfiguration.allowedGroupCiphers.clear();
        wifiConfiguration.allowedKeyManagement.clear();
        wifiConfiguration.allowedPairwiseCiphers.clear();
        wifiConfiguration.allowedProtocols.clear();
        wifiConfiguration.SSID = surroundQuotes(ssid);
        //先不指定bssid，因为这样，如果有多个路由器是同样的Wi-Fi的话，这个时候连接的路由器断开了，不会自动连接其它路由器
        //BSSID = bssid
        wifiConfiguration.hiddenSSID = isHidden;
        wifiConfiguration.status = WifiConfiguration.Status.ENABLED;
        return wifiConfiguration;
    }

    private String surroundQuotes(String ssid) {
        if(TextUtils.isEmpty(ssid)){
           return ssid;
        }else{
           return '\"' + ssid + '\"';
        }
    }

    /**
     *
     * @param wifiConfiguration
     * @param pwd
     * @param encryptionType
     * @return
     */
    private WifiConfiguration updateConfig(WifiConfiguration wifiConfiguration, String pwd, EncryptionType encryptionType) {
        switch(encryptionType){
                 case WEP :{
                     String password;
                     if (isHexOfLength(pwd, 10, 26, 32)) {
                         password = pwd;
                     } else {
                         password = surroundQuotes(pwd);
                     }
                     wifiConfiguration.wepKeys[0] = password;
                     wifiConfiguration.wepTxKeyIndex = 0;
                     wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                     wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                     wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                     wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                     wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                     wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                     break;
                 }
                 case WPA:{
                     String password;
                     if (isHexOfLength(pwd, 64)) {
                         password = pwd;
                     } else {
                         password = surroundQuotes(pwd);
                     }
                     wifiConfiguration.preSharedKey = password;
                     wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                     wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA); // For WPA
                     wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN); // For WPA2
                     wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                     wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
                     wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                     wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                     wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                     wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                     break;
                 }
                 case NO_PASS:{
                     wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    break;
                 }
                 default:
                    break;
            }
            return wifiConfiguration;
    }

    /**
     * 通过ssid查找之前是否配置过该网络
     * 有，则返回该配置；无，则返回null
     *
     * 注意：一定要传递config.SSID的参数来查找，因为有双引号的区别，且始终会返回null
     */
    private WifiConfiguration findExistedConfigBySsid(String ssid){
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (null != configuredNetworks) {
            for (WifiConfiguration configuredNetwork : configuredNetworks) {
                String configuredSSID = configuredNetwork.SSID;
                if (!TextUtils.isEmpty(configuredSSID)) {
                    if (ssid.equals(configuredSSID)) {
                        return configuredNetwork;
                    }
                }
            }
        }
        return null;
    }

    private boolean isHexOfLength(String value,int... allowedLengths){
        int count = 0;
        boolean result;
        while(true) {
            if(count >= allowedLengths.length) {
                result = false;
                break;
            }
            int element = allowedLengths[count];
            if(value.length() == element) {
                result = true;
                break;
            }
            ++count;
        }
        return result && HEX_PATTERN.matcher(value).matches();
    }

    enum EncryptionType {
        WEP,
        WPA,
        NO_PASS;

        public static EncryptionType parse(String capabilities){
            if (capabilities.toUpperCase().contains("WEP")) {
                return WEP;
            } else if (capabilities.toUpperCase().contains("PSK") || capabilities.toUpperCase().contains("EAP")) {
                return WPA;
            }
            return NO_PASS;
        }
    }
}
