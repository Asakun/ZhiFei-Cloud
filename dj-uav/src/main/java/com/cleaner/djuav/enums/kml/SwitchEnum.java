package com.cleaner.djuav.enums.kml;

public enum SwitchEnum {
    DISABLE(0),

    ENABLE(1)
    ;

    private final int state;

    SwitchEnum(int state) {
        this.state = state;
    }

    public int getState() {
        return state;
    }
}
