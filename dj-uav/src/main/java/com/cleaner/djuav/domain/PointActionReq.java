package com.cleaner.djuav.domain;

import com.cleaner.djuav.enums.kml.ActionActuatorFuncEnums;
import com.cleaner.djuav.enums.kml.GimbalHeadingYawBaseEnums;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;

@Data
public class PointActionReq implements Serializable {

    /**
     * 动作编号
     */
    private Integer actionIndex;

    /**
     * 动作名称
     */
    private ActionActuatorFuncEnums actionFunc;

    /**
     * 飞行器悬停等待时间
     */
    private Double hoverTime;

    /**
     * 定时拍摄最小间隔，单位秒，必须为有限正数。
     */
    private Double minShootInterval;

    /**
     * 飞行器目标偏航角
     */
    private Double aircraftHeading;

    /**
     * 是否使用全局拍照模式 0：不使用 1：使用
     */
    private Integer useGlobalImageFormat;

    /**
     * 拍照模式（字典）
     */
    private String imageFormat;

    /**
     * 云台偏航角基准
     */
    private GimbalHeadingYawBaseEnums gimbalHeadingYawBase=GimbalHeadingYawBaseEnums.NORTH;

    /**
     * 云台偏航角
     */
    private Double gimbalYawRotateAngle;

    /**
     * 云台俯仰角
     */
    private Double gimbalPitchRotateAngle;

    /**
     * 云台俯转动时间
     */
    private Integer gimbalRotateTime=10;

    /**
     * 变焦焦距
     */
    private Double zoom;

    /**
     * 对焦模式（setFocusType）
     */
    private String cameraFocusType;

    /**
     * 是否点对焦（focus）
     */
    private Integer isPointFocus;

    /**
     * 是否无限远对焦（focus）
     */
    private Integer isInfiniteFocus;

    /**
     * 对焦区域X坐标（focus）
     */
    private Double focusX;

    /**
     * 对焦区域Y坐标（focus）
     */
    private Double focusY;

    /**
     * 对焦区域宽度（focus）
     */
    private Double focusRegionWidth;

    /**
     * 对焦区域高度（focus）
     */
    private Double focusRegionHeight;

    private String fileSuffix;

    private String directoryName;

    public String getActionFunc() {
        if (actionFunc == null) {return null;}
        return actionFunc.getValue();
    }

    public void setActionFunc(String key) {
        this.actionFunc = Arrays.stream(ActionActuatorFuncEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("ActionActuatorFuncEnums not found:"+key));
    }

    public String getGimbalHeadingYawBase() {
        return gimbalHeadingYawBase.getValue();
    }

    public void setGimbalHeadingYawBase(String value) {
        this.gimbalHeadingYawBase = Arrays.stream(GimbalHeadingYawBaseEnums.values()).filter(format -> format.getValue().equals(value)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("GimbalHeadingYawBaseEnums not found:"+value));
    }
}
