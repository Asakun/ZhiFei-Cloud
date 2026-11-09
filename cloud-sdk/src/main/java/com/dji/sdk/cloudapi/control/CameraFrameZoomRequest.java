package com.dji.sdk.cloudapi.control;

import com.dji.sdk.cloudapi.device.PayloadIndex;
import com.dji.sdk.common.BaseModel;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class CameraFrameZoomRequest extends BaseModel {

    @NotNull
    private PayloadIndex payloadIndex;

    @NotNull
    private CameraTypeEnum cameraType;

    /**
     * true: Lock the gimbal, the gimbal and the drone rotate together.
     * false: Only the gimbal rotates, but the drone does not.
     */
    @NotNull
    private Boolean locked;
    /**
     * upper left corner as center point
     */
    @NotNull
    @Min(0)
    @Max(1)
    private Float x;

    @NotNull
    @Min(0)
    @Max(1)
    private Float y;

    @NotNull
    @Min(0)
    @Max(1)
    private Float width;

    @NotNull
    @Min(0)
    @Max(1)
    private Float height;

    public CameraFrameZoomRequest() {
    }

    @Override
    public String toString() {
        return "CameraFrameZoomRequest{" +
                "payloadIndex=" + payloadIndex +
                ", cameraType=" + cameraType +
                ", locked=" + locked +
                ", x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                '}';
    }

    public PayloadIndex getPayloadIndex() {
        return payloadIndex;
    }

    public CameraFrameZoomRequest setPayloadIndex(PayloadIndex payloadIndex) {
        this.payloadIndex = payloadIndex;
        return this;
    }

    public CameraTypeEnum getCameraType() {
        return cameraType;
    }

    public CameraFrameZoomRequest setCameraType(CameraTypeEnum cameraType) {
        this.cameraType = cameraType;
        return this;
    }

    public Boolean getLocked() {
        return locked;
    }

    public CameraFrameZoomRequest setLocked(Boolean locked) {
        this.locked = locked;
        return this;
    }

    public Float getX() {
        return x;
    }

    public CameraFrameZoomRequest setX(Float x) {
        this.x = x;
        return this;
    }

    public Float getY() {
        return y;
    }

    public CameraFrameZoomRequest setY(Float y) {
        this.y = y;
        return this;
    }

    public Float getWidth() {
        return width;
    }

    public CameraFrameZoomRequest setWidth(Float width) {
        this.width = width;
        return this;
    }

    public Float getHeight() {
        return height;
    }

    public CameraFrameZoomRequest setHeight(Float height) {
        this.height = height;
        return this;
    }
}
