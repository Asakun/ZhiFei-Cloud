package com.cleaner.djuav.domain;

import com.cleaner.djuav.enums.kml.CollectionMethodEnums;
import com.cleaner.djuav.enums.kml.ShootTypeEnums;
import com.cleaner.djuav.enums.kml.SwitchEnum;
import com.cleaner.djuav.util.model.WayPoint;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * Author:Cleaner
 * Date: 2024/12/22 10:46
 **/
@Data
public class MappingTypeReq implements Serializable {

    /**
     * 采集方式
     */
    private CollectionMethodEnums collectionMethod;

    /**
     * 倾斜摄影角度
     */
    private Integer inclinedGimbalPitch = -45;

    /**
     * 镜头类型
     * 未启用(涉及到激光)
     */
    private String lensType = "camera";

    /**
     * 航向重叠率
     */
    private Integer overlapH;

    /**
     * 旁向重叠率
     */
    private Integer overlapW;

    /**
     * 是否开启高程优化
     */
    private SwitchEnum elevationOptimizeEnable = SwitchEnum.ENABLE;

    /**
     * 拍照模式
     */
    private ShootTypeEnums shootType;

    /**
     * 航线方向
     */
    private String direction;

    /**
     * 测区外扩距离
     */
    private String margin = "0";

    /**
     * 测区多边形坐标  经度,纬度,高度
     */
    private List<CoordinatePointReq> coordinates;

    private List<WayPoint> simRoutePointList;

    public String getCollectionMethod() {
        return collectionMethod.getValue();
    }

    public void setCollectionMethod(String key) {
        this.collectionMethod = Arrays.stream(CollectionMethodEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("CollectionMethodEnums not found:"+key));
    }

    public String getShootType() {
        return shootType.getValue();
    }

    public void setShootType(String key) {
        this.shootType = Arrays.stream(ShootTypeEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("ShootTypeEnums not found:"+key));
    }

    public Integer getElevationOptimizeEnable() {
        return elevationOptimizeEnable.getState();
    }

    public void setElevationOptimizeEnable(Integer state) {
        this.elevationOptimizeEnable = Arrays.stream(SwitchEnum.values()).filter(format -> format.getState() == state).findAny()
                .orElseThrow(() -> new IllegalArgumentException("SwitchEnum not found:"+state));
    }
}
