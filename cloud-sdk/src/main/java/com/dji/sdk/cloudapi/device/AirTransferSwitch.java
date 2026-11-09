package com.dji.sdk.cloudapi.device;


public class AirTransferSwitch {
    private Boolean airTransferEnable;

    public AirTransferSwitch() {}

    @Override
    public String toString() {
        return "AirTransferSwitch{" +
                "airTransferEnable=" + airTransferEnable +
                '}';
    }

    public Boolean getAirTransferEnable() {
        return airTransferEnable;
    }

    public AirTransferSwitch setAirTransferEnable(Boolean airTransferEnable) {
        this.airTransferEnable = airTransferEnable;
        return this;
    }
}
