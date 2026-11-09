package com.dji.sample.control.service.impl;

import com.dji.sample.control.model.param.DronePayloadParam;

import java.util.Objects;

public class CameraScreenDragImpl extends PayloadCommandsHandler{

    public CameraScreenDragImpl(DronePayloadParam param) {
        super(param);
    }

    @Override
    public boolean valid() {
        return Objects.nonNull(param.getPitchSpeed()) && Objects.nonNull(param.getYawSpeed())
                && Objects.nonNull(param.getLocked());
    }

    //TODO osdCamera state check
}
