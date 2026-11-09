package com.dji.sdk.cloudapi.device;

public class CameraWatermarkSettings {

    private SwitchActionEnum globalEnable;

    private SwitchActionEnum droneTypeEnable;

    private SwitchActionEnum droneSnEnable;

    private SwitchActionEnum datetimeEnable;

    private SwitchActionEnum gpsEnable;

    private SwitchActionEnum userCustomStringEnable;

    private String userCustomString;

    private CameraLayoutEnum layout;

    public CameraWatermarkSettings() {
    }

    @Override
    public String toString() {
        return "CameraWatermarkSettings{" +
                "globalEnable=" + globalEnable +
                ", droneTypeEnable=" + droneTypeEnable +
                ", droneSnEnable=" + droneSnEnable +
                ", datetimeEnable=" + datetimeEnable +
                ", gpsEnable=" + gpsEnable +
                ", userCustomStringEnable=" + userCustomStringEnable +
                ", userCustomString='" + userCustomString + '\'' +
                ", layout=" + layout +
                '}';
    }

    public SwitchActionEnum getGlobalEnable() {
        return globalEnable;
    }

    public void setGlobalEnable(SwitchActionEnum globalEnable) {
        this.globalEnable = globalEnable;
    }

    public SwitchActionEnum getDroneTypeEnable() {
        return droneTypeEnable;
    }

    public void setDroneTypeEnable(SwitchActionEnum droneTypeEnable) {
        this.droneTypeEnable = droneTypeEnable;
    }

    public SwitchActionEnum getDroneSnEnable() {
        return droneSnEnable;
    }

    public void setDroneSnEnable(SwitchActionEnum droneSnEnable) {
        this.droneSnEnable = droneSnEnable;
    }

    public SwitchActionEnum getDatetimeEnable() {
        return datetimeEnable;
    }

    public void setDatetimeEnable(SwitchActionEnum datetimeEnable) {
        this.datetimeEnable = datetimeEnable;
    }

    public SwitchActionEnum getGpsEnable() {
        return gpsEnable;
    }

    public void setGpsEnable(SwitchActionEnum gpsEnable) {
        this.gpsEnable = gpsEnable;
    }

    public SwitchActionEnum getUserCustomStringEnable() {
        return userCustomStringEnable;
    }

    public void setUserCustomStringEnable(SwitchActionEnum userCustomStringEnable) {
        this.userCustomStringEnable = userCustomStringEnable;
    }

    public String getUserCustomString() {
        return userCustomString;
    }

    public void setUserCustomString(String userCustomString) {
        this.userCustomString = userCustomString;
    }

    public CameraLayoutEnum getLayout() {
        return layout;
    }

    public void setLayout(CameraLayoutEnum layout) {
        this.layout = layout;
    }
}
