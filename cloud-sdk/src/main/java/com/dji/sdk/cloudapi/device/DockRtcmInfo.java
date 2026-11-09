package com.dji.sdk.cloudapi.device;

public class DockRtcmInfo {
    public String mountPoint;

    public String port;

    public String host;

    public DockRtcmDeviceTypeEnum rtcmDeviceType;

    public DockRtcmSourceTypeEnum sourceType;

    @Override
    public String toString() {
        return "DockRtcmInfo{" +
                "mountPoint='" + mountPoint + '\'' +
                ", port='" + port + '\'' +
                ", host='" + host + '\'' +
                ", rtcmDeviceType=" + rtcmDeviceType +
                ", sourceType=" + sourceType +
                '}';
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public DockRtcmDeviceTypeEnum getRtcmDeviceType() {
        return rtcmDeviceType;
    }

    public void setRtcmDeviceType(DockRtcmDeviceTypeEnum rtcmDeviceType) {
        this.rtcmDeviceType = rtcmDeviceType;
    }

    public DockRtcmSourceTypeEnum getSourceType() {
        return sourceType;
    }

    public void setSourceType(DockRtcmSourceTypeEnum sourceType) {
        this.sourceType = sourceType;
    }
}
