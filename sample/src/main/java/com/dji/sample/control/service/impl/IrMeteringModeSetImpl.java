package com.dji.sample.control.service.impl;

import com.dji.sample.control.model.param.DronePayloadParam;

import java.util.Objects;

/**
 * @author dyw
 * @version 1.4
 * @date 2025/9/26
 */
public class IrMeteringModeSetImpl extends PayloadCommandsHandler {

    public IrMeteringModeSetImpl(DronePayloadParam param) {
        super(param);
    }

    @Override
    public boolean valid() {
        return Objects.nonNull(param.getMode());
    }

    //TODO osdCamera state check
}
