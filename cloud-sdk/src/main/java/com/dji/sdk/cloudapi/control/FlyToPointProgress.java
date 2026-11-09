package com.dji.sdk.cloudapi.control;

import com.dji.sdk.annotations.CloudSDKVersion;
import com.dji.sdk.cloudapi.wayline.WaylineErrorCodeEnum;
import com.dji.sdk.config.version.CloudSDKVersionEnum;

import java.util.List;

/**
 * @author sean
 * @version 1.4
 * @date 2023/3/14
 */
public class FlyToPointProgress {

    private WaylineErrorCodeEnum result;

    private FlyToStatusEnum status;

    private String flyToId;

    private Integer wayPointIndex;

    /**
     * Remaining mission distance
     * unit: m
     */
    @CloudSDKVersion(since = CloudSDKVersionEnum.V1_0_0)
    private Float remainingDistance;

    /**
     * Remaining mission time
     * unit: s
     */
    @CloudSDKVersion(since = CloudSDKVersionEnum.V1_0_0)
    private Integer remainingTime;

    /**
     * Planned trajectory point list
     */
    @CloudSDKVersion(since = CloudSDKVersionEnum.V1_0_0)
    private List<Point> plannedPathPoints;

    public FlyToPointProgress() {
    }

    @Override
    public String toString() {
        return "FlyToPointProgress{" +
                "result=" + result +
                ", status=" + status +
                ", flyToId='" + flyToId + '\'' +
                ", wayPointIndex=" + wayPointIndex +
                '}';
    }

    public WaylineErrorCodeEnum getResult() {
        return result;
    }

    public FlyToPointProgress setResult(WaylineErrorCodeEnum result) {
        this.result = result;
        return this;
    }

    public FlyToStatusEnum getStatus() {
        return status;
    }

    public FlyToPointProgress setStatus(FlyToStatusEnum status) {
        this.status = status;
        return this;
    }

    public String getFlyToId() {
        return flyToId;
    }

    public FlyToPointProgress setFlyToId(String flyToId) {
        this.flyToId = flyToId;
        return this;
    }

    public Integer getWayPointIndex() {
        return wayPointIndex;
    }

    public FlyToPointProgress setWayPointIndex(Integer wayPointIndex) {
        this.wayPointIndex = wayPointIndex;
        return this;
    }

    public Float getRemainingDistance() {
        return remainingDistance;
    }

    public FlyToPointProgress setRemainingDistance(Float remainingDistance) {
        this.remainingDistance = remainingDistance;
        return this;
    }

    public Integer getRemainingTime() {
        return remainingTime;
    }

    public FlyToPointProgress setRemainingTime(Integer remainingTime) {
        this.remainingTime = remainingTime;
        return this;
    }

    public List<Point> getPlannedPathPoints() {
        return plannedPathPoints;
    }

    public FlyToPointProgress setPlannedPathPoints(List<Point> plannedPathPoints) {
        this.plannedPathPoints = plannedPathPoints;
        return this;
    }
}
