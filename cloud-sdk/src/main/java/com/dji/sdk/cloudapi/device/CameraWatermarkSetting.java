package com.dji.sdk.cloudapi.device;

public class CameraWatermarkSetting {
    private CameraWatermarkSettings cameraWatermarkSettings;

    public CameraWatermarkSetting() {}

    @Override
    public String toString() {
        return "CameraWatermarkSettingsState{" +
                "cameraWatermarkSettings=" + cameraWatermarkSettings +
                '}';
    }

    public CameraWatermarkSettings getCameraWatermarkSettings() {
        return cameraWatermarkSettings;
    }

    public CameraWatermarkSetting setCameraWatermarkSettings(CameraWatermarkSettings cameraWatermarkSettings) {
        this.cameraWatermarkSettings = cameraWatermarkSettings;
        return this;
    }
}
