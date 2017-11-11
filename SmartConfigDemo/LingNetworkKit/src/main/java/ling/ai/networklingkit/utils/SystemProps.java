package ling.ai.networklingkit.utils;

import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Created by cuiqiang on 2017/9/22.
 */

public class SystemProps {

    private final static String TAG = "Ling";

    private static Class cls;
    private static SystemProps INSTANCE;

    public static SystemProps getInstance() {
        synchronized (SystemProps.class) {
            if (null == INSTANCE) {
                INSTANCE = new SystemProps();
            }
        }
        return INSTANCE;
    }

    public void init() {
        try {
            cls = Class.forName("android.os.SystemProperties");
            Log.d(TAG,"cls"+cls);
        } catch (Exception ex) {
            cls = null;
        }
    }

    /**
     * 得到一个系统属性
     *
     * @param key
     * @param defValue
     * @return
     */
    public  String get(String key, String defValue) {
        Object obj = null;
        try {
            if (null!=cls) {
                Method method = cls.getMethod("get", new Class[]{String.class});
                if (null != method) {
                    Log.d(TAG, "method " + method);
                    obj = method.invoke(cls, new Object[]{key});
                    Log.d(TAG, "obj " + obj);
                }
            }
        } catch (Exception ex) {}
        if (null != obj) {
            String value = (String) obj;
            Log.d(TAG, "得到属性 " + value);
            return value;
        } else {
            Log.d(TAG, "得到属性 " + 0);
            return defValue;
        }

    }

    /**
     * 设置一个系统属性
     */
    public void set(String key, String value) {
        try {
            if (null!=cls) {
                Method method = cls.getMethod("set", new Class[]{String.class, String.class});
                Log.d(TAG, "设置属性");
                if (null != method) {
                    Log.d(TAG, "method " + method);
                    method.invoke(cls, new Object[]{key, value});
                    Log.d(TAG, "设置成功");
                }
            }
        } catch (Exception ex) {}
    }


    /**
     * 同[set]，只不过是在于当前设置值不同的情况下才真正设置
     */
    public void setIfChanged(String key, String value) {
        if (!get(key, "").equals(value)) {
            set(key, value);
        }
    }

    /**
     * 清理掉某一个属性（设置为空字符串）
     */
    public void clear(String key) {
        if (!TextUtils.isEmpty(key)) {
            setIfChanged(key, "");
        }
    }
}
