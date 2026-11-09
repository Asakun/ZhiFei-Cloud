package com.dji.sample.control.service.impl;

import com.dji.sample.control.model.param.DronePayloadParam;

import java.util.Objects;

/**
 * @author dyw
 * @version 1.4
 * @date 2025/9/26
 */
public class IrMeteringAreaSetImpl extends PayloadCommandsHandler {

    public IrMeteringAreaSetImpl(DronePayloadParam param) {
        super(param);
    }

    @Override
    public boolean valid() {
        return Objects.nonNull(param.getX()) && Objects.nonNull(param.getY())
                && Objects.nonNull(param.getWidth()) && Objects.nonNull(param.getHeight());
    }

    //TODO osdCamera state check
}
