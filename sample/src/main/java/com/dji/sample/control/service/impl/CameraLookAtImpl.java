package com.dji.sample.control.service.impl;

import com.dji.sample.control.model.param.DronePayloadParam;

import java.util.Objects;

public class CameraLookAtImpl extends PayloadCommandsHandler{

    public CameraLookAtImpl(DronePayloadParam param) {
        super(param);
    }

    @Override
    public boolean valid() {
        return Objects.nonNull(param.getLatitude()) && Objects.nonNull(param.getLongitude())
                && Objects.nonNull(param.getLocked()) && Objects.nonNull(param.getHeight());
    }

    //TODO osdCamera state check
}
