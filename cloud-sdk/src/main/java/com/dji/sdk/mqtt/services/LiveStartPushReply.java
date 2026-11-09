package com.dji.sdk.mqtt.services;

import java.util.List;

public class LiveStartPushReply {
    List<String> originVideoId;

    public LiveStartPushReply() {
    }

    @Override
    public String toString() {
        return "LiveStartPushReply{" +
                "originVideoId=" + originVideoId +
                '}';
    }

    public List<String> getOriginVideoId() {
        return originVideoId;
    }

    public LiveStartPushReply setOriginVideoId(List<String> originVideoId) {
        this.originVideoId = originVideoId;
        return this;
    }
}
