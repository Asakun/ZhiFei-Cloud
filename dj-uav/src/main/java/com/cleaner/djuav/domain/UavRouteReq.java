package com.cleaner.djuav.domain;

import com.cleaner.djuav.enums.kml.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * Author:Cleaner
 * Date: 2024/12/22 10:46
 **/
@Data
public class UavRouteReq implements Serializable {

    /**
     * 航线类型
     */
    private TemplateTypeEnums templateType;

    /**
     * 无人机类型
     */
    private DroneEnumValueEnums droneType;

    /**
     * 无人机子类型
     */
    private SubDroneEnumValueEnums subDroneType;

    /**
     * 负载类型
     */
    private PayloadTypeEnums payloadType;

    /**
     * 负载子类型
     */
    private PayloadSubEnumValueEnums payloadSubType;

    /**
     * 负载挂载位置
     */
    private PayloadPositionIndexEnums payloadPosition;

    /**
     * 航线结束动作
     */
    private FinishActionEnums finishAction;

    /**
     * 失控动作
     */
    private ExecuteRCLostActionEnums exitOnRcLostAction;

    /**
     * 参考起飞点
     */
    private String takeOffRefPoint;

    /**
     * 参考起飞点到地面到距离
     */
    private Double takeOffRefPointAGLHeight = 0d;

    /**
     * 全局航点转弯模式
     * waypoint only
     */
    private WaypointTurnReq waypointTurnReq;

    /**
     * 航线高度模式
     */
    private HeightModeEnums heightMode = HeightModeEnums.RELATIVE_TO_START_POINT;

    /**
     * 全局航线高度
     */
    private Double globalHeight;

    /**
     * 全局偏航角模式
     * waypoint only
     */
    private WaypointHeadingReq waypointHeadingReq;

    /**
     * 云台俯仰角控制模式
     * waypoint only
     */
    private GimbalPitchModeEnums gimbalPitchMode;

    /**
     * 全局航线飞行速度
     */
    private Double autoFlightSpeed;

    /**
     * 负载图片存储类型
     */
    private String imageFormat;

    /**
     * 避让限飞区
     */
    private SwitchEnum waylineAvoidLimitAreaMode = SwitchEnum.DISABLE;

    /**
     * 建图航拍、倾斜摄影、航带飞行模板参数
     */
    private MappingTypeReq mappingTypeReq;

    /**
     * 航点列表
     * waypoint only
     */
    private List<RoutePointReq> routePointList;

    /**
     * 航线初始动作列表
     */
    private List<PointActionReq> startActionList;

    public String getTemplateType() {
        if (templateType == null) {return null;}
        return templateType.getValue();
    }

    public void setTemplateType(String key) {
        this.templateType = Arrays.stream(TemplateTypeEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("TemplateTypeEnums not found:"+key));
    }

    public Integer getDroneType() {
        if (droneType == null) {return null;}
        return droneType.getValue();
    }

    public void setDroneType(Integer key) {
        this.droneType = Arrays.stream(DroneEnumValueEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("DroneEnumValueEnums not found:"+key));
    }

    public Integer getSubDroneType() {
        if (subDroneType == null) {return null;}
        return subDroneType.getValue();
    }

    public void setSubDroneType(Integer key) {
        this.subDroneType = Arrays.stream(SubDroneEnumValueEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("SubDroneEnumValueEnums not found:"+key));
    }

    public Integer getPayloadType() {
        if (payloadType == null) {return null;}
        return payloadType.getValue();
    }

    public void setPayloadType(Integer key) {
        this.payloadType = Arrays.stream(PayloadTypeEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("PayloadTypeEnums not found:"+key));
    }

    public Integer getPayloadSubType() {
        if (payloadSubType == null) {return null;}
        return payloadSubType.getValue();
    }

    public void setPayloadSubType(Integer key) {
        this.payloadSubType = Arrays.stream(PayloadSubEnumValueEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("PayloadSubEnumValueEnums not found:"+key));
    }

    public Integer getPayloadPosition() {
        if (payloadPosition == null) {return null;}
        return payloadPosition.getValue();
    }

    public void setPayloadPosition(Integer key) {
        this.payloadPosition = Arrays.stream(PayloadPositionIndexEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("PayloadPositionIndexEnums not found:"+key));
    }

    public String getExitOnRcLostAction() {
        if (exitOnRcLostAction == null) {return null;}
        return exitOnRcLostAction.getValue();
    }

    public void setExitOnRcLostAction(String key) {
        this.exitOnRcLostAction = Arrays.stream(ExecuteRCLostActionEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("ExecuteRCLostActionEnums not found:"+key));
    }

    public String getFinishAction() {
        if (finishAction == null) {return null;}
        return finishAction.getValue();
    }

    public void setFinishAction(String key) {
        this.finishAction = Arrays.stream(FinishActionEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("FinishActionEnums not found:"+key));
    }

    public String getGimbalPitchMode() {
        if (gimbalPitchMode == null) {return null;}
        return gimbalPitchMode.getValue();
    }

    public void setGimbalPitchMode(String key) {
        this.gimbalPitchMode = Arrays.stream(GimbalPitchModeEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("GimbalPitchModeEnums not found:"+key));
    }

    public String getHeightMode() {
        if (heightMode == null) {return null;}
        return heightMode.getValue();
    }

    public void setHeightMode(String key) {
        this.heightMode = Arrays.stream(HeightModeEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("HeightModeEnums not found:"+key));
    }

    public Integer getWaylineAvoidLimitAreaMode() {
        return waylineAvoidLimitAreaMode.getState();
    }

    public void setWaylineAvoidLimitAreaMode(Integer state) {
        this.waylineAvoidLimitAreaMode = Arrays.stream(SwitchEnum.values()).filter(format -> format.getState() == state).findAny()
                .orElseThrow(() -> new IllegalArgumentException("SwitchEnum not found:"+state));
    }
}
