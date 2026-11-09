package com.dji.sample.control.model.param;

import com.dji.sdk.cloudapi.control.*;
import com.dji.sdk.cloudapi.device.CameraModeEnum;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;

/**
 * @author dyw
 * @version 1.4
 * @date 2025/9/26
 */
@Data
public class DronePayloadParam {

    @Pattern(regexp = "(\\d+|)-(\\d+|)-\\d+")
    @NotNull
    private String payloadIndex;

    private CameraTypeEnum cameraType;

    @Range(min = 2, max = 200)
    private Float zoomFactor;

    private CameraModeEnum cameraMode;

    /**
     * true: Lock the gimbal, the gimbal and the drone rotate together.
     * false: Only the gimbal rotates, but the drone does not.
     */
    private Boolean locked;

    private Double pitchSpeed;

    /**
     * Only valid when locked is false.
     */
    private Double yawSpeed;

    /**
     * upper left corner as center point
     */
    @Range(min = 0, max = 1)
    private Double x;

    @Range(min = 0, max = 1)
    private Double y;

    private GimbalResetModeEnum resetMode;

    private Double width;

    private Double height;

    private Double latitude;

    private Double longitude;

    private Boolean enable;

    private List<LensStorageSettingsEnum> photoStorageSettings;

    private List<LensStorageSettingsEnum> videoStorageSettings;

    private ExposureValueEnum exposureValue;

    private ExposureModeEnum exposureMode;

    private FocusModeEnum focusMode;

    private Integer focusValue;

    private MeteringModeEnum mode;

    public void checkPayloadIndex() {
        String[] split = payloadIndex.split("-");
        for (int i = 0; i < split.length; i++) {
            if (split[i].isBlank()) {
                split[i] = "0";
            }
        }
        payloadIndex = String.join("-", split);
    }
}
