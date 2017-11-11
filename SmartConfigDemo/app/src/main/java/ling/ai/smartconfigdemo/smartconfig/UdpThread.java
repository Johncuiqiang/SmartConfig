package ling.ai.smartconfigdemo.smartconfig;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.Charset;

/**
 * Created by cuiqiang on 2017/9/19.
 */

public class UdpThread extends Thread {

    private final static String TAG = "Ling";
    private static final int WHAT_DATA = 1;
    private static final int WHAT_RUN = 2;
    private static final String KEY_DATA = "key_data";
    private static final String KEY_RUN = "key_run";

    private static final int LOCAL_PORT = 4560;
    private static final int REMOTE_PORT = 80;

    private Handler mUdpHandler;
    private DatagramSocket mUdpSocket = null;
    private static final String MUTICAST_PREFIX = "224.7";
    private boolean mRunFlag = false;
    private static final char[] CRC8_TABLE = {
            0, 94, 188, 226, 97, 63, 221, 131, 194, 156, 126, 32, 163, 253, 31, 65,
            157, 195, 33, 127, 252, 162, 64, 30, 95, 1, 227, 189, 62, 96, 130, 220,
            35, 125, 159, 193, 66, 28, 254, 160, 225, 191, 93, 3, 128, 222, 60, 98,
            190, 224, 2, 92, 223, 129, 99, 61, 124, 34, 192, 158, 29, 67, 161, 255,
            70, 24, 250, 164, 39, 121, 155, 197, 132, 218, 56, 102, 229, 187, 89, 7,
            219, 133, 103, 57, 186, 228, 6, 88, 25, 71, 165, 251, 120, 38, 196, 154,
            101, 59, 217, 135, 4, 90, 184, 230, 167, 249, 27, 69, 198, 152, 122, 36,
            248, 166, 68, 26, 153, 199, 37, 123, 58, 100, 134, 216, 91, 5, 231, 185,
            140, 210, 48, 110, 237, 179, 81, 15, 78, 16, 242, 172, 47, 113, 147, 205,
            17, 79, 173, 243, 112, 46, 204, 146, 211, 141, 111, 49, 178, 236, 14, 80,
            175, 241, 19, 77, 206, 144, 114, 44, 109, 51, 209, 143, 12, 82, 176, 238,
            50, 108, 142, 208, 83, 13, 239, 177, 240, 174, 76, 18, 145, 207, 45, 115,
            202, 148, 118, 40, 171, 245, 23, 73, 8, 86, 180, 234, 105, 55, 213, 139,
            87, 9, 235, 181, 54, 104, 138, 212, 149, 203, 41, 119, 244, 170, 72, 22,
            233, 183, 85, 11, 136, 214, 52, 106, 43, 117, 151, 201, 74, 20, 246, 168,
            116, 42, 200, 150, 21, 75, 169, 247, 182, 232, 10, 84, 215, 137, 107, 53
    };

    private char[] makeCRC8(byte[] p) {
        char crc8 = 0;
        char[] retval = new char[p.length + 1];
        for (int i = 0; i < p.length; i++) {
            retval[i + 1] = (char) p[i];
        }
        for (byte aP : p) {
            crc8 = CRC8_TABLE[(crc8 ^ aP) & 0xFF];
        }

        retval[0] = crc8;
        return retval;
    }

    public void send(String data) {
        if (mUdpHandler == null) {
            return;
        }
        Bundle bundle = new Bundle();

        bundle.putString(KEY_DATA, data);
        Message message = mUdpHandler.obtainMessage();
        message.what = WHAT_DATA;
        message.setData(bundle);
        mUdpHandler.sendMessage(message);
    }

    @Override
    public void run() {
        connect();
        Looper.prepare();
        mUdpHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case WHAT_DATA: {
                        if (mUdpSocket != null && !mUdpSocket.isClosed()) {
                            Log.d(TAG,"发送信息");
                            sendData(getBytesForSmartConfig(msg.getData().getString(KEY_DATA)));
                        }
                        break;
                    }
                    case WHAT_RUN: {
                        mRunFlag = msg.getData().getBoolean(KEY_RUN);
                        break;
                    }
                    default: {
                    }
                }
            }
        };
        Looper.loop();
    }

    private void connect() {
        try {
            mUdpSocket = new DatagramSocket(LOCAL_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void sendData(byte[] data) {
        try {
            char[] encBuf = makeCRC8(data);
            byte[] dummybuf = new byte[256];
            int delayms = 5;

            DatagramPacket mUdpSendPacket = new DatagramPacket(dummybuf, dummybuf.length);
            mUdpSendPacket.setData(dummybuf);
            mUdpSendPacket.setPort(REMOTE_PORT);
            mUdpSendPacket.setAddress(InetAddress.getByName("224.70.0.0"));

            do {
                for (int i = 0; i < encBuf.length; i++) {
                    if (i > 255) {
                        mUdpSendPacket.setAddress(InetAddress.getByName(MUTICAST_PREFIX + 1 + "." + (i - 256) + "." + (encBuf[i] & 0xFF)));
                        //Log.d(TAG, "ipaddr = " + MUTICAST_PREFIX + 1 + "." + (i - 256) + "." + (encBuf[i] & 0xFF));
                    } else {
                        mUdpSendPacket.setAddress(InetAddress.getByName(MUTICAST_PREFIX + 0 + "." + i + "." + (encBuf[i] & 0xFF)));
                       // Log.d(TAG, "ipaddr = " + MUTICAST_PREFIX + 0 + "." + i + "." + (encBuf[i] & 0xFF));
                    }
                    mUdpSendPacket.setLength(1);
                    mUdpSocket.send(mUdpSendPacket);
                    Thread.sleep(delayms);
                }
                Thread.sleep(200);
            } while (mRunFlag);
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    public void setRunFlag(boolean run) {
        mRunFlag = run;
        if (mUdpSocket != null && !run && !mUdpSocket.isClosed()) {
            mUdpSocket.close();
        }
    }

    private byte[] getBytesForSmartConfig(String... values) {
        StringBuilder combinedStrings = new StringBuilder();
        for (String value : values) {
            combinedStrings.append(value);
        }
        return combinedStrings.toString().getBytes(Charset.forName("UTF-8"));
    }
}
