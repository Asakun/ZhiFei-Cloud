package com.dji.sample.control.model.param;

import com.dji.sdk.cloudapi.control.CommanderFlightModeEnum;
import com.dji.sdk.cloudapi.control.CommanderModeLostActionEnum;
import com.dji.sdk.cloudapi.device.ExitWaylineWhenRcLostEnum;
import com.dji.sdk.cloudapi.device.RcLostActionEnum;
import com.dji.sdk.cloudapi.device.SwitchActionEnum;
import com.dji.sdk.cloudapi.wayline.OutOfControlActionEnum;
import com.dji.sdk.cloudapi.wayline.RthModeEnum;
import com.dji.sdk.cloudapi.wayline.SimulateMission;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author sean
 * @version 1.4
 * @date 2023/3/1
 */
@Data
public class TakeoffToPointParam {

    private String flightId;

    @Range(min = -180, max = 180)
    @NotNull
    private Double targetLongitude;

    @Range(min = -90, max = 90)
    @NotNull
    private Double targetLatitude;

    @Range(min = 2, max = 10000)
    @NotNull
    private Double targetHeight;

    @Range(min = 2, max = 1500)
    @NotNull
    private Double securityTakeoffHeight;

    @Range(min = 2, max = 1500)
    @NotNull
    private Integer rthAltitude;

    @NotNull
    private RcLostActionEnum rcLostAction;

    @NotNull
    private ExitWaylineWhenRcLostEnum exitWaylineWhenRcLost;

    @Range(min = 1, max = 15)
    @NotNull
    private Integer maxSpeed;

    private RthModeEnum rthMode;

    private CommanderModeLostActionEnum commanderModeLostAction;

    private CommanderFlightModeEnum commanderFlightMode;

    @Min(2)
    @Max(3000)
    private Float commanderFlightHeight;

    private SimulateMission simulateMission;

    private SwitchActionEnum flightSafetyAdvanceCheck;

    public OutOfControlActionEnum RcLostAction2OutOfControlAction() {
        if (rcLostAction != null) {
            switch (rcLostAction) {
                case HOVER:
                    return OutOfControlActionEnum.HOVERING;
                case LAND:
                    return OutOfControlActionEnum.LANDING;
                case RETURN_HOME:
                    return OutOfControlActionEnum.RETURN_TO_HOME;
            }
        }
        return null;
    }
}
