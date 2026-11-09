package com.dji.sample.psdk.service.impl;

import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.component.websocket.model.BizCodeEnum;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.model.enums.UserTypeEnum;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.psdk.model.dto.AeroSpotDataTransmissionDto;
import com.dji.sample.psdk.model.dto.FloatingWindowTextNotifyDto;
import com.dji.sample.psdk.model.enums.AeroSpotValueEnum;
import com.dji.sample.psdk.model.enums.PsdkWidgetNameEnum;
import com.dji.sample.psdk.service.IInterConnectionService;
import com.dji.sample.psdk.service.IPsdkAeroSpotService;
import com.dji.sample.psdk.service.IPsdkWidgetService;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sample.wayline.service.IWaylineRedisService;
import com.dji.sdk.cloudapi.interconnection.CustomDataTransmissionFromPsdk;
import com.dji.sdk.cloudapi.interconnection.CustomDataTransmissionToPsdkRequest;
import com.dji.sdk.cloudapi.interconnection.api.AbstractInterconnectionService;
import com.dji.sdk.common.SDKManager;
import com.dji.sdk.mqtt.events.TopicEventsRequest;
import com.dji.sdk.mqtt.services.ServicesReplyData;
import com.dji.sdk.mqtt.services.TopicServicesResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;

@Service
@Transactional
@Slf4j
public class InterConnectionServiceImpl extends AbstractInterconnectionService implements IInterConnectionService {

    @Autowired
    private IPsdkWidgetService widgetService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private IWaylineRedisService waylineRedisService;

    @Autowired
    private IPsdkAeroSpotService aeroSpotService;

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void customDataTransmissionFromPsdk(TopicEventsRequest<CustomDataTransmissionFromPsdk> request, MessageHeaders headers) {
        CustomDataTransmissionFromPsdk data = request.getData();
        String dockSn = request.getGateway();
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(dockSn);

        if (deviceOpt.isPresent()) {
            String droneSn = deviceOpt.get().getChildDeviceSn();
            Set<PsdkWidgetNameEnum> widgetNameEnums = widgetService.getRedisWidgetNames(droneSn);
            for (PsdkWidgetNameEnum enums: widgetNameEnums) {
                switch (enums) {
                    case AERO_SPOT:
                        if (caseAeroSpot(data, deviceOpt.get())) {
                            return;
                        }
                        break;
                    case SNIFFER4D_NANO2:
                    default:
                        break;
                }
            }
        }
    }

    public boolean caseAeroSpot(CustomDataTransmissionFromPsdk data, DeviceDTO device) {
        try {
            AeroSpotDataTransmissionDto dto = objectMapper.readValue(data.getValue(), AeroSpotDataTransmissionDto.class);
            if (dto.getCode() == 200) {
                if (StringUtils.hasText(dto.getMsg()) && dto.getMsg().contains("firmwareVersion")) {
                    JsonNode version = objectMapper.readTree(dto.getMsg());
                    RedisOpsUtils.set(aeroSpotService.getRedisASSLKey(device.getChildDeviceSn()), version.get("deviceSn"));
                } else {
                    sendMessage2Web(device, dto.getMsg());
                }
            } else if (dto.getCode() == 100) {
                Optional<DuringFlightInfo> duringFlightInfo = waylineRedisService.getDuringFlightInfo(device.getDeviceSn());
                if (duringFlightInfo.isPresent()) {
                    DuringFlightInfo flightInfo = duringFlightInfo.get();
                    String key = aeroSpotService.getRedisASFKey(flightInfo.getFlightId());
                    RedisOpsUtils.addSetElementWithExpire(key,dto.getMsg(), RedisConst.DRC_MODE_ALIVE_SECOND);
                    if (!StringUtils.hasText(flightInfo.getToUploadAeroSpotKey())) {
                        flightInfo.setToUploadAeroSpotKey(key);
                        waylineRedisService.setDuringFlightInfo(device.getDeviceSn(), flightInfo);
                        aeroSpotService.psdkWidgetValueSetAeroSpot(device.getDeviceSn(), AeroSpotValueEnum.QUERY_INFO);
                    }
                }
            }
            return true;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void sendMessage2Web(DeviceDTO device, String text) {
        webSocketMessageService.sendBatch(
                device.getWorkspaceId(), UserTypeEnum.WEB.getVal(),
                BizCodeEnum.CUSTOM_DATA_TRANSMISSION_FROM_PSDK.getCode(),
                FloatingWindowTextNotifyDto.builder().sn(device.getDeviceSn()).text(text).build());
    }

    @Override
    public Boolean customDataTransmissionToPsdk(String dockSn, String value) {
        CustomDataTransmissionToPsdkRequest request = new CustomDataTransmissionToPsdkRequest();
        request.setValue(value);
        TopicServicesResponse<ServicesReplyData> response = this.customDataTransmissionToPsdk(SDKManager.getDeviceSDK(dockSn), request);
        ServicesReplyData reply = response.getData();
        return reply.getResult().isSuccess();
    }
}
