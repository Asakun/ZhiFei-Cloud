package com.dji.sdk.cloudapi.device;

public class HideGeoCagingStatus {
    private Integer state;

    public HideGeoCagingStatus() {
    }

    @Override
    public String toString() {
        return "HideGeoCagingStatus{" +
                "state=" + state +
                '}';
    }

    public Integer getState() {
        return state;
    }

    public void setState(Integer state) {
        this.state = state;
    }
}
