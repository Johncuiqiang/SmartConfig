package ling.ai.smartconfigdemo;

import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import java.util.List;

import ling.ai.networklingkit.WifiConfigManager;
import ling.ai.smartconfigdemo.smartconfig.UdpThread;

/**
 * @author cuiqiang
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Ling";

    private View mBtnConnect;
    private View mBtnSend;
    private View mBtnStopSend;
    private UdpThread mUdpThread;
    private EditText mEtSsid;
    private EditText mEtPsw;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
    }

    private void initView() {
        mBtnSend = findViewById(R.id.btn_send);
        mBtnConnect = findViewById(R.id.btn_connect);
        mBtnStopSend = findViewById(R.id.btn_stop);
        mEtSsid = (EditText) findViewById(R.id.et_ssid);
        mEtPsw = (EditText) findViewById(R.id.et_psw);
        WifiConfigManager.getInstance().init(MainActivity.this);
        mUdpThread = new UdpThread();
        mUdpThread.start();

    }

    private void initData() {
        mBtnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//               WifiRepo.INSTANCE.init(MainActivity.this);
//               WifiRepo.INSTANCE.connect("LingX","lingx2017");
                WifiConfigManager.getInstance().useSmartConfig().startReceiver();

            }
        });
        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mUdpThread.setRunFlag(true);
                String ssid = mEtSsid.getText().toString().trim();
                String psw = mEtPsw.getText().toString().trim();
                List<ScanResult> list = WifiConfigManager.getInstance().getScanResult(ssid);
                String data = ssid+"|"+psw+"|"+list.get(0).capabilities+"|";
                mUdpThread.send(data);
                Log.d("smartconfig","data "+data);
            }
        });
        mBtnStopSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mUdpThread.setRunFlag(false);
            }
        });

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUdpThread.setRunFlag(false);
        WifiConfigManager.getInstance().useSmartConfig().stop(false);
    }

}
