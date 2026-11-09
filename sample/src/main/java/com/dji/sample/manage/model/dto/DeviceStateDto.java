package com.dji.sample.manage.model.dto;

import com.dji.sdk.cloudapi.device.CameraWatermarkSettings;
import lombok.Data;

@Data
public class DeviceStateDto {
    private Integer RemainingPowerForReturnHome;

    private CameraWatermarkSettings cameraWatermarkSettings;

    private Boolean airTransferEnabled;
}
