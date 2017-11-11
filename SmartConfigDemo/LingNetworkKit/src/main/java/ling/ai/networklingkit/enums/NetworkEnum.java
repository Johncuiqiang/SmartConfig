package ling.ai.networklingkit.enums;

/**
 * Created by cuiqiang on 2017/9/18.
 */

public enum NetworkEnum {

    WEP("WEP", 0),
    WPA("WPA",1),
    NO_PASSWORD("nopass",2),
    UNKNOWN("", 99);

    private String name;
    private int index;

    NetworkEnum(String name, int index){
        this.name = name;
        this.index = index;
    }

    public static String getName(int index) {
        for (NetworkEnum c : NetworkEnum.values()) {
            if (c.getIndex() == index) {
                return c.name;
            }
        }
        return null;
    }

    public static NetworkEnum getInstance(String name){
        for (NetworkEnum c : NetworkEnum.values()) {
            if (c.getName().equals(name) ) {
                return c;
            }
        }
        return UNKNOWN;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

}
