package ling.ai.networklingkit;

import android.content.Context;
import android.net.wifi.ScanResult;

import java.util.List;

import ling.ai.networklingkit.smartconfig.NewWifiConfigRepo;
import ling.ai.networklingkit.smartconfig.SmartConfigRepos;

/**
 * Created by cuiqiang on 2017/9/18.
 * @author cuiqiang
 */

public class WifiConfigManager {

    private static WifiConfigManager INSTANCE;

    private Context mContext;
    private NewWifiConfigRepo mWifiConfigRepo = null;
    private SmartConfigRepos mSmartConfigRepos = null;

    public static WifiConfigManager getInstance(){
        synchronized (WifiConfigManager.class){
            if (null == INSTANCE){
                INSTANCE =new WifiConfigManager();
            }
        }
        return INSTANCE;
    }

    public void init(Context context){
        mContext = context;
    }

    public void useBluetooth(){

    }

    public SmartConfigRepos useSmartConfig(){
        if (null == mSmartConfigRepos) {
            mSmartConfigRepos = new SmartConfigRepos(mContext);
        }
        return mSmartConfigRepos;
    }

    /**
     * 连接网络
     *
     * @param ssid          wifi名称
     * @param password      wifi密码
     * @param capabilities  加密方式
     */
    public void connectWifi(String ssid,String password,String capabilities){
        if (null == mWifiConfigRepo ) {
            mWifiConfigRepo = new NewWifiConfigRepo(mContext);
        }
        mWifiConfigRepo.connect(ssid,password,capabilities);
    }


    /**
     * 得到扫描结果
     *
     * @param ssid wifi名称
     */
    public  List<ScanResult> getScanResult(String ssid){
        if (null == mWifiConfigRepo) {
            mWifiConfigRepo = new NewWifiConfigRepo(mContext);
        }
        List<ScanResult> list = mWifiConfigRepo.findWifiBySsid(ssid, true);
        return list;
    }

}
