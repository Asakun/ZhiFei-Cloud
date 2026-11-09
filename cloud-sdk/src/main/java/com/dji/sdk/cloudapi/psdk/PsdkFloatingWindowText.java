package com.dji.sdk.cloudapi.psdk;

public class PsdkFloatingWindowText {
    private Integer psdk_index;

    private String value;

    public PsdkFloatingWindowText() {
    }

    @Override
    public String toString() {
        return "PSDKFloatingWindowText{" +
                "psdk_index=" + psdk_index +
                ", value='" + value + '\'' +
                '}';
    }

    public Integer getPsdk_index() {
        return psdk_index;
    }

    public PsdkFloatingWindowText setPsdk_index(Integer psdk_index) {
        this.psdk_index = psdk_index;
        return this;
    }

    public String getValue() {
        return value;
    }

    public PsdkFloatingWindowText setValue(String value) {
        this.value = value;
        return this;
    }
}
