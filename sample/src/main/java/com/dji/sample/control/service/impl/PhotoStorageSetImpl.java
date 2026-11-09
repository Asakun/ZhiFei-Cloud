package com.dji.sample.control.service.impl;

import com.dji.sample.control.model.param.DronePayloadParam;
import com.dji.sdk.cloudapi.device.CameraStateEnum;

import java.util.Objects;

/**
 * @author dyw
 * @version 1.4
 * @date 2025/9/26
 */
public class PhotoStorageSetImpl extends PayloadCommandsHandler {

    public PhotoStorageSetImpl(DronePayloadParam param) {
        super(param);
    }

    @Override
    public boolean valid() {
        return Objects.nonNull(param.getPhotoStorageSettings()) && !param.getPhotoStorageSettings().isEmpty();
    }

    @Override
    public boolean canPublish(String deviceSn) {
        super.canPublish(deviceSn);
        return CameraStateEnum.IDLE == osdCamera.getPhotoState();
    }
}
