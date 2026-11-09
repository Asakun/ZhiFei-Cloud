package com.dji.sdk.cloudapi.device;

import java.util.List;

public class OsdDrone {

    protected Float attitudeHead;

    protected Double attitudePitch;

    protected Double attitudeRoll;

    protected Float elevation;

    protected DroneBattery battery;

    protected String firmwareVersion;

    protected GearEnum gear;

    protected Float height;

    protected Float homeDistance;

    protected Float horizontalSpeed;

    protected Float latitude;

    protected Float longitude;

    protected DroneModeCodeEnum modeCode;

    protected Double totalFlightDistance;

    protected Float totalFlightTime;

    protected Float verticalSpeed;

    protected WindDirectionEnum windDirection;

    protected Float windSpeed;

    protected DronePositionState positionState;

    protected Storage storage;

    protected Integer heightLimit;

    protected String trackId;

    protected List<OsdCamera> cameras;

    public Float getAttitudeHead() {
        return attitudeHead;
    }

    public OsdDrone setAttitudeHead(Float attitudeHead) {
        this.attitudeHead = attitudeHead;
        return this;
    }

    public Double getAttitudePitch() {
        return attitudePitch;
    }

    public OsdDrone setAttitudePitch(Double attitudePitch) {
        this.attitudePitch = attitudePitch;
        return this;
    }

    public Double getAttitudeRoll() {
        return attitudeRoll;
    }

    public OsdDrone setAttitudeRoll(Double attitudeRoll) {
        this.attitudeRoll = attitudeRoll;
        return this;
    }

    public Float getElevation() {
        return elevation;
    }

    public OsdDrone setElevation(Float elevation) {
        this.elevation = elevation;
        return this;
    }

    public DroneBattery getBattery() {
        return battery;
    }

    public OsdDrone setBattery(DroneBattery battery) {
        this.battery = battery;
        return this;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public OsdDrone setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
        return this;
    }

    public GearEnum getGear() {
        return gear;
    }

    public OsdDrone setGear(GearEnum gear) {
        this.gear = gear;
        return this;
    }

    public Float getHeight() {
        return height;
    }

    public OsdDrone setHeight(Float height) {
        this.height = height;
        return this;
    }

    public Float getHomeDistance() {
        return homeDistance;
    }

    public OsdDrone setHomeDistance(Float homeDistance) {
        this.homeDistance = homeDistance;
        return this;
    }

    public Float getHorizontalSpeed() {
        return horizontalSpeed;
    }

    public OsdDrone setHorizontalSpeed(Float horizontalSpeed) {
        this.horizontalSpeed = horizontalSpeed;
        return this;
    }

    public Float getLatitude() {
        return latitude;
    }

    public OsdDrone setLatitude(Float latitude) {
        this.latitude = latitude;
        return this;
    }

    public Float getLongitude() {
        return longitude;
    }

    public OsdDrone setLongitude(Float longitude) {
        this.longitude = longitude;
        return this;
    }

    public DroneModeCodeEnum getModeCode() {
        return modeCode;
    }

    public OsdDrone setModeCode(DroneModeCodeEnum modeCode) {
        this.modeCode = modeCode;
        return this;
    }

    public Double getTotalFlightDistance() {
        return totalFlightDistance;
    }

    public OsdDrone setTotalFlightDistance(Double totalFlightDistance) {
        this.totalFlightDistance = totalFlightDistance;
        return this;
    }

    public Float getTotalFlightTime() {
        return totalFlightTime;
    }

    public OsdDrone setTotalFlightTime(Float totalFlightTime) {
        this.totalFlightTime = totalFlightTime;
        return this;
    }

    public Float getVerticalSpeed() {
        return verticalSpeed;
    }

    public OsdDrone setVerticalSpeed(Float verticalSpeed) {
        this.verticalSpeed = verticalSpeed;
        return this;
    }

    public WindDirectionEnum getWindDirection() {
        return windDirection;
    }

    public OsdDrone setWindDirection(WindDirectionEnum windDirection) {
        this.windDirection = windDirection;
        return this;
    }

    public Float getWindSpeed() {
        return windSpeed;
    }

    public OsdDrone setWindSpeed(Float windSpeed) {
        this.windSpeed = windSpeed;
        return this;
    }

    public DronePositionState getPositionState() {
        return positionState;
    }

    public OsdDrone setPositionState(DronePositionState positionState) {
        this.positionState = positionState;
        return this;
    }

    public Storage getStorage() {
        return storage;
    }

    public OsdDrone setStorage(Storage storage) {
        this.storage = storage;
        return this;
    }

    public Integer getHeightLimit() {
        return heightLimit;
    }

    public OsdDrone setHeightLimit(Integer heightLimit) {
        this.heightLimit = heightLimit;
        return this;
    }

    public String getTrackId() {
        return trackId;
    }

    public OsdDrone setTrackId(String trackId) {
        this.trackId = trackId;
        return this;
    }

    public List<OsdCamera> getCameras() {
        return cameras;
    }

    public OsdDrone setCameras(List<OsdCamera> cameras) {
        this.cameras = cameras;
        return this;
    }

}
