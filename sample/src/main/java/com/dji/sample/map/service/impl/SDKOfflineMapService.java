package com.dji.sample.map.service.impl;

import com.dji.sdk.cloudapi.map.api.AbstractOfflineMapService;
import com.dji.sdk.cloudapi.property.DockDroneOfflineMapEnable;
import com.dji.sdk.mqtt.MqttReply;
import com.dji.sdk.mqtt.state.TopicStateRequest;
import com.dji.sdk.mqtt.state.TopicStateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SDKOfflineMapService extends AbstractOfflineMapService {

    @Override
    public TopicStateResponse<MqttReply> dockDroneOfflineMapEnable(TopicStateRequest<DockDroneOfflineMapEnable> request, MessageHeaders headers) {
        log.info("TODO dockDroneOfflineMapEnable:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }
}
