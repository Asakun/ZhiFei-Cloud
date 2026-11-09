package com.dji.sdk.cloudapi.control;

import com.dji.sdk.common.BaseModel;

import java.util.List;

public class CloudControlAuthGrabRequest extends BaseModel {

    private String userId;

    private String userCallsign;

    private List<String> controlKeys;

    @Override
    public String toString() {
        return "CloudControlAuthGrabRequest{" +
                "userId='" + userId + '\'' +
                ", userCallsign='" + userCallsign + '\'' +
                ", controlKeys=" + controlKeys +
                '}';
    }

    public String getUserId() {
        return userId;
    }

    public CloudControlAuthGrabRequest setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getUserCallsign() {
        return userCallsign;
    }

    public CloudControlAuthGrabRequest setUserCallsign(String userCallsign) {
        this.userCallsign = userCallsign;
        return this;
    }

    public List<String> getControlKeys() {
        return controlKeys;
    }

    public CloudControlAuthGrabRequest setControlKeys(List<String> controlKeys) {
        this.controlKeys = controlKeys;
        return this;
    }
}
