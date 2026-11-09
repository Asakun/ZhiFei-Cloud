package com.dji.sample.control.service.impl;

import com.dji.sample.control.model.param.DronePayloadParam;

import java.util.Objects;

public class CameraFrameZoomImpl extends PayloadCommandsHandler{

    public CameraFrameZoomImpl(DronePayloadParam param) {
        super(param);
    }

    @Override
    public boolean valid() {
        return Objects.nonNull(param.getLocked()) && Objects.nonNull(param.getCameraType())
                && Objects.nonNull(param.getX()) && Objects.nonNull(param.getY())
                && Objects.nonNull(param.getWidth()) && Objects.nonNull(param.getHeight());
    }

    //TODO osdCamera state check
}
