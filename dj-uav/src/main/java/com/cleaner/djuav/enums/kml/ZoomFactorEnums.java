package com.cleaner.djuav.enums.kml;

public enum ZoomFactorEnums {
    DEFAULT(24);

    int val;

    ZoomFactorEnums(int value) {
        this.val = value;
    }

    public int getVal() {
        return val;
    }
}
