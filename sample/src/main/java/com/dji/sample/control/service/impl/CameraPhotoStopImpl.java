package com.dji.sample.control.service.impl;

import com.dji.sample.control.model.param.DronePayloadParam;
import com.dji.sdk.cloudapi.device.CameraStateEnum;

/**
 * @author dyw
 * @version 1.4
 * @date 2025/9/26
 */
public class CameraPhotoStopImpl extends PayloadCommandsHandler {

    public CameraPhotoStopImpl(DronePayloadParam param) {
        super(param);
    }

    @Override
    public boolean canPublish(String deviceSn) {
        super.canPublish(deviceSn);
        return CameraStateEnum.WORKING == osdCamera.getPhotoState();
    }
}
