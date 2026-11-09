package com.dji.sample.manage.service.impl;

import com.dji.sample.component.websocket.model.BizCodeEnum;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.control.model.dto.ResultNotifyDTO;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.model.enums.UserTypeEnum;
import com.dji.sample.manage.service.IAirsenseService;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sdk.cloudapi.airsense.AirsenseWarning;
import com.dji.sdk.cloudapi.airsense.api.AbstractAirsenseService;
import com.dji.sdk.mqtt.MqttReply;
import com.dji.sdk.mqtt.events.TopicEventsRequest;
import com.dji.sdk.mqtt.events.TopicEventsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class AirsenseServiceImpl extends AbstractAirsenseService implements IAirsenseService {

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public TopicEventsResponse<MqttReply> airsenseWarning(TopicEventsRequest<List<AirsenseWarning>> request, MessageHeaders headers) {
        String dockSn  = request.getGateway();

        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (deviceOpt.isEmpty()) {
            log.error("The dock is offline.");
            return null;
        }

        List<AirsenseWarning> eventsReceiver = request.getData();
        if (eventsReceiver == null || eventsReceiver.isEmpty()) {
            log.error("AirsenseWarning info is empty.");
            return null;
        }
        try {
            webSocketMessageService.sendBatch(deviceOpt.get().getWorkspaceId(), UserTypeEnum.WEB.getVal(),
                    BizCodeEnum.AIRSENSE.getCode(),
                    ResultNotifyDTO.builder().sn(dockSn)
                            .message(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(eventsReceiver))
                            .result(0)
                            .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return new TopicEventsResponse<MqttReply>().setData(MqttReply.success());
    }
}
