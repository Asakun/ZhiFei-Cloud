package com.dji.sdk.cloudapi.control;

import com.dji.sdk.cloudapi.device.PayloadIndex;
import com.dji.sdk.common.BaseModel;

import javax.validation.constraints.NotNull;

public class CameraScreenDragRequest extends BaseModel {

    @NotNull
    private PayloadIndex payloadIndex;

    /**
     * true: Lock the gimbal, the gimbal and the drone rotate together.
     * false: Only the gimbal rotates, but the drone does not.
     */
    @NotNull
    private Boolean locked;

    private Double pitchSpeed;

    private Double yawSpeed;

    public CameraScreenDragRequest() {
    }

    @Override
    public String toString() {
        return "CameraScreenDrag{" +
                "payloadIndex=" + payloadIndex +
                ", locked=" + locked +
                ", pitchSpeed=" + pitchSpeed +
                ", yawSpeed=" + yawSpeed +
                '}';
    }

    public PayloadIndex getPayloadIndex() {
        return payloadIndex;
    }

    public CameraScreenDragRequest setPayloadIndex(PayloadIndex payloadIndex) {
        this.payloadIndex = payloadIndex;
        return this;
    }

    public Boolean getLocked() {
        return locked;
    }

    public CameraScreenDragRequest setLocked(Boolean locked) {
        this.locked = locked;
        return this;
    }

    public Double getPitchSpeed() {
        return pitchSpeed;
    }

    public CameraScreenDragRequest setPitchSpeed(Double pitchSpeed) {
        this.pitchSpeed = pitchSpeed;
        return this;
    }

    public Double getYawSpeed() {
        return yawSpeed;
    }

    public CameraScreenDragRequest setYawSpeed(Double yawSpeed) {
        this.yawSpeed = yawSpeed;
        return this;
    }
}
