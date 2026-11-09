package com.dji.sample.control.model.enums;

import com.dji.sample.control.service.impl.*;
import com.dji.sdk.cloudapi.control.PayloadControlMethodEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * @author dyw
 * @version 1.4
 * @date 2025/9/26
 */
public enum PayloadCommandsEnum {

    CAMERA_FRAME_ZOOM(PayloadControlMethodEnum.CAMERA_FRAME_ZOOM, CameraFrameZoomImpl.class),

    CAMERA_MODE_SWitCH(PayloadControlMethodEnum.CAMERA_MODE_SWITCH, CameraModeSwitchImpl.class),

    CAMERA_PHOTO_TAKE(PayloadControlMethodEnum.CAMERA_PHOTO_TAKE, CameraPhotoTakeImpl.class),

    CAMERA_PHOTO_STOP(PayloadControlMethodEnum.CAMERA_PHOTO_STOP, CameraPhotoStopImpl.class),

    CAMERA_RECORDING_START(PayloadControlMethodEnum.CAMERA_RECORDING_START, CameraRecordingStartImpl.class),

    CAMERA_RECORDING_STOP(PayloadControlMethodEnum.CAMERA_RECORDING_STOP, CameraRecordingStopImpl.class),

    CAMERA_SCREEN_DRAG(PayloadControlMethodEnum.CAMERA_SCREEN_DRAG, CameraScreenDragImpl.class),

    CAMERA_AIM(PayloadControlMethodEnum.CAMERA_AIM, CameraAimImpl.class),

    CAMERA_FOCAL_LENGTH_SET(PayloadControlMethodEnum.CAMERA_FOCAL_LENGTH_SET, CameraFocalLengthSetImpl.class),

    GIMBAL_RESET(PayloadControlMethodEnum.GIMBAL_RESET, GimbalResetImpl.class),

    CAMERA_LOOK_AT(PayloadControlMethodEnum.CAMERA_LOOK_AT, CameraLookAtImpl.class),

    CAMERA_SCREEN_SPLIT(PayloadControlMethodEnum.CAMERA_SCREEN_SPLIT, CameraScreenSplitImpl.class),

    PHOTO_STORAGE_SET(PayloadControlMethodEnum.PHOTO_STORAGE_SET, PhotoStorageSetImpl.class),

    VIDEO_STORAGE_SET(PayloadControlMethodEnum.VIDEO_STORAGE_SET, VideoStorageSetImpl.class),

    CAMERA_EXPOSURE_SET(PayloadControlMethodEnum.CAMERA_EXPOSURE_SET, CameraExposureSetImpl.class),

    CAMERA_EXPOSURE_MODE_SET(PayloadControlMethodEnum.CAMERA_EXPOSURE_MODE_SET, CameraExposureModeSetImpl.class),

    CAMERA_FOCUS_MODE_SET(PayloadControlMethodEnum.CAMERA_FOCUS_MODE_SET, CameraFocusModeSetImpl.class),

    CAMERA_FOCUS_VALUE_SET(PayloadControlMethodEnum.CAMERA_FOCUS_VALUE_SET, CameraFocusValueSetImpl.class),

    IR_METERING_MODE_SET(PayloadControlMethodEnum.IR_METERING_MODE_SET, IrMeteringModeSetImpl.class),

    IR_METERING_POINT_SET(PayloadControlMethodEnum.IR_METERING_POINT_SET, IrMeteringPointSetImpl.class),

    IR_METERING_AREA_SET(PayloadControlMethodEnum.IR_METERING_AREA_SET, IrMeteringAreaSetImpl.class),

    CAMERA_POINT_FOCUS_ACTION(PayloadControlMethodEnum.CAMERA_POINT_FOCUS_ACTION, CameraPointFocusActionImpl.class)
    ;

    PayloadControlMethodEnum cmd;

    Class<? extends PayloadCommandsHandler> clazz;

    PayloadCommandsEnum(PayloadControlMethodEnum cmd, Class<? extends PayloadCommandsHandler> clazz) {
        this.cmd = cmd;
        this.clazz = clazz;
    }

    @JsonValue
    public String getMethod() {
        return cmd.getPayloadMethod().getMethod();
    }

    public Class<? extends PayloadCommandsHandler> getClazz() {
        return clazz;
    }

    public PayloadControlMethodEnum getCmd() {
        return cmd;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PayloadCommandsEnum find(String method) {
        return Arrays.stream(values()).filter(methodEnum -> methodEnum.cmd.getPayloadMethod().getMethod().equals(method)).findAny()
                .orElseThrow();
    }
}
