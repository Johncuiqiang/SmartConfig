package ling.ai.networklingkit.exception;

/**
 * Created by cuiqiang on 2017/9/18.
 */

public class WifiConfigException extends Exception{

    public WifiConfigException(String message){
        super(message);
    }

    public WifiConfigException(String message,Throwable throwable){
        super(message, throwable);
    }
}
