package com.cleaner.djuav.enums.kml;

/**
 * wpml:actionActuatorFunc	 动作类型
 */
public enum ActionActuatorFuncEnums {

    TAKE_PHOTO("takePhoto", "单拍"),
    START_RECORD("startRecord", "开始录像"),
    STOP_RECORD("stopRecord", "结束录像"),
    FOCUS("focus", "对焦"),
    SET_FOCUS_TYPE("setFocusType", "设置对焦模式"),
    ZOOM("zoom", "变焦"),
    CUSTOM_DIRNAME("customDirName", "创建新文件夹"),
    GIMBAL_ROTATE("gimbalRotate", "旋转云台"),
    ROTATE_YAW("rotateYaw", "飞行器偏航"),
    HOVER("hover", "悬停等待"),
    //todo 未实现
    GIMBAL_EVENLY_ROTATE("gimbalEvenlyRotate", "航段间均匀转动云台pitch角"),

    GIMBAL_ANGEL_LOCK("gimbalAngleLock", "锁云台"),
    GIMBAL_ANGEL_UNLOCK("gimbalAngleUnlock", "解锁云台"),
    START_TIME_LAPSE("startTimeLapse", "开始定时拍摄"),
    STOP_TIME_LAPSE("stopTimeLapse", "停止定时拍摄"),
    ORIENTED_SHOOT("orientedShoot", "定向拍照动作"),
    PANO_SHOT("panoShot", "全景拍照动作"),
    //特殊机型 L1负载
    RECORD_POINT_CLOUD("recordPointCloud", "点云录制操作");

    private String value;
    private String description;

    ActionActuatorFuncEnums(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
