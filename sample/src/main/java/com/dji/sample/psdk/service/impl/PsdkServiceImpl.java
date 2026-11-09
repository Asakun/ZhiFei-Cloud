package com.dji.sample.psdk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sample.component.websocket.model.BizCodeEnum;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.model.enums.UserTypeEnum;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.psdk.dao.IPsdkFloatingWindowTextMapper;
import com.dji.sample.psdk.dao.IPsdkWidgetValueSetMapper;
import com.dji.sample.psdk.model.dto.FloatingWindowTextNotifyDto;
import com.dji.sample.psdk.model.dto.PsdkFloatingWindowTextDto;
import com.dji.sample.psdk.model.dto.PsdkWidgetValueSetDTO;
import com.dji.sample.psdk.model.entity.PsdkFloatingWindowTextEntity;
import com.dji.sample.psdk.model.entity.PsdkWidgetEntity;
import com.dji.sample.psdk.model.entity.PsdkWidgetValueSetEntity;
import com.dji.sample.psdk.model.enums.PsdkWidgetNameEnum;
import com.dji.sample.psdk.service.IPsdkService;
import com.dji.sample.psdk.service.IPsdkWidgetService;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sample.wayline.service.IWaylineRedisService;
import com.dji.sdk.cloudapi.device.OsdDockDrone;
import com.dji.sdk.cloudapi.psdk.PsdkFloatingWindowText;
import com.dji.sdk.cloudapi.psdk.PsdkWidgetValueSetRequest;
import com.dji.sdk.cloudapi.psdk.api.AbstractPsdkService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.SDKManager;
import com.dji.sdk.mqtt.MqttReply;
import com.dji.sdk.mqtt.events.TopicEventsRequest;
import com.dji.sdk.mqtt.events.TopicEventsResponse;
import com.dji.sdk.mqtt.services.ServicesReplyData;
import com.dji.sdk.mqtt.services.TopicServicesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class PsdkServiceImpl extends AbstractPsdkService implements IPsdkService {

    @Autowired
    private IPsdkFloatingWindowTextMapper psdkFloatingWindowTextMapper;

    @Autowired
    private IPsdkWidgetValueSetMapper psdkWidgetValueSetMapper;

    @Autowired
    private IWaylineRedisService waylineRedisService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private IPsdkWidgetService widgetService;

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public HttpResultResponse sendPsdkWidgetValueSet(String dockSn, Integer psdkIndex, Integer index, Integer value, String name) {
        PsdkWidgetValueSetRequest request = new PsdkWidgetValueSetRequest();
        request.setPsdkIndex(psdkIndex);
        request.setIndex(index);
        request.setValue(value);
        TopicServicesResponse<ServicesReplyData> response = this.psdkWidgetValueSet(SDKManager.getDeviceSDK(dockSn), request);
        ServicesReplyData reply = response.getData();
        return reply.getResult().isSuccess() ?
                HttpResultResponse.success()
                : HttpResultResponse.error(name + "psdk widget value set failed. " + reply.getResult());
    }

    @Override
    public TopicEventsResponse<MqttReply> psdkFloatingWindowText(TopicEventsRequest<PsdkFloatingWindowText> request, MessageHeaders headers) {
        Optional<DuringFlightInfo> duringFlightInfo = waylineRedisService.getDuringFlightInfo(request.getGateway());
        PsdkFloatingWindowText data = request.getData();

        if (duringFlightInfo.isPresent() && duringFlightInfo.get().getEnablePsdkFloatingWindowTextRecord()) {
            String droneSn = duringFlightInfo.get().getDroneSn();
            Optional<OsdDockDrone> osdDockDrone = deviceRedisService.getDeviceOsd(droneSn, OsdDockDrone.class);
            if (osdDockDrone.isPresent()) {
                PsdkFloatingWindowTextEntity entity = PsdkFloatingWindowTextEntity.builder()
                        .jobId(duringFlightInfo.get().getFlightId())
                        .latitude(osdDockDrone.get().getLatitude())
                        .longitude(osdDockDrone.get().getLongitude())
                        .height(osdDockDrone.get().getHeight())
                        .psdkIndex(data.getPsdk_index())
                        .value(data.getValue())
                        .createTime(request.getTimestamp())
                        .build();
                Optional<PsdkWidgetEntity> widgetOpt = widgetService.getRedisWidget(droneSn, data.getPsdk_index());
                widgetOpt.ifPresent(psdkWidget -> entity.setPsdkName(psdkWidget.getPsdkName()));
                psdkFloatingWindowTextMapper.insert(entity);
            }
        }
        sendMessage2Web(request.getGateway(), BizCodeEnum.PSDK_FLOATING_WINDOW_TEXT, data.getValue());
        return new TopicEventsResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public List<PsdkFloatingWindowTextDto> getPsdkFloatingWindowDatas(String jobId, Integer psdkIndex) {
        return psdkFloatingWindowTextMapper.selectList(
                new LambdaQueryWrapper<PsdkFloatingWindowTextEntity>()
                        .eq(PsdkFloatingWindowTextEntity::getPsdkIndex, psdkIndex)
                        .eq(PsdkFloatingWindowTextEntity::getJobId, jobId)).stream()
                .map(this::entity2Dto).collect(Collectors.toList());
    }

    @Override
    public List<PsdkWidgetValueSetDTO> getWidgetValueSetList(String name, String sn) {
        return psdkWidgetValueSetMapper.selectList(
                new LambdaQueryWrapper<PsdkWidgetValueSetEntity>()
                        .eq(PsdkWidgetValueSetEntity::getVisible, 1)
                        .like(StringUtils.hasText(name), PsdkWidgetValueSetEntity::getName, name)
                        .eq(StringUtils.hasText(sn), PsdkWidgetValueSetEntity::getSn, sn)).stream()
                .map(this::entityConvertToDto).collect(Collectors.toList());
    }

    private PsdkWidgetValueSetDTO entityConvertToDto(PsdkWidgetValueSetEntity entity) {
        if (Objects.isNull(entity)) {
            return null;
        }
        return PsdkWidgetValueSetDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .sn(entity.getSn())
                .psdkIndex(entity.getPsdkIndex())
                .index(entity.getIndex())
                .value(entity.getValue())
                .action(entity.getAction())
                .remark(entity.getRemark())
                .visible(entity.getVisible())
                .build();
    }

    private void sendMessage2Web(String dockSn,BizCodeEnum codeEnum, String text) {
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(dockSn);
        deviceOpt.ifPresent(deviceDTO -> webSocketMessageService.sendBatch(
                deviceDTO.getWorkspaceId(), UserTypeEnum.WEB.getVal(), codeEnum.getCode(),
                FloatingWindowTextNotifyDto.builder().sn(dockSn).text(text).build()));
    }

    private PsdkFloatingWindowTextDto entity2Dto(PsdkFloatingWindowTextEntity entity) {
        if (Objects.isNull(entity)) {
            return null;
        }
        return PsdkFloatingWindowTextDto.builder()
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .height(entity.getHeight())
                .value(convertToObjectNode(entity.getValue(), entity.getValue()))
                .psdkName(entity.getPsdkName())
                .createTime(entity.getCreateTime())
                .build();
    }


    /**
     * 将输入字符串转换为ObjectNode
     */
    public ObjectNode convertToObjectNode(String input, String psdkName) {
        // 创建根节点（ObjectNode）
        ObjectNode rootNode = objectMapper.createObjectNode();

        if (psdkName.equals(PsdkWidgetNameEnum.SNIFFER4D_NANO2.getName())){
            // 按换行符分割成行
            String[] lines = input.split("\n");

            // 当前处理的层级节点（如"ug/m3"、"ppm"对应的子节点）
            ObjectNode currentLevelNode = null;

            for (String line : lines) {
                // 去除首尾空白字符
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // 判断是否是层级行（如"ug/m3:"、"ppm:"）
                if (line.endsWith(":")) {
                    // 提取层级名称（去除末尾的冒号）
                    String levelName = line.substring(0, line.length() - 1);
                    // 创建当前层级的子节点（ObjectNode）
                    currentLevelNode = objectMapper.createObjectNode();
                    // 将子节点添加到根节点
                    rootNode.set(levelName, currentLevelNode);
                } else {
                    // 处理键值对行（可能包含多个键值对，用制表符分隔）
                    if (currentLevelNode != null) {
                        // 按制表符分割，过滤空字符串
                        String[] keyValuePairs = line.split("\t");
                        for (String pair : keyValuePairs) {
                            pair = pair.trim();
                            if (pair.isEmpty()) {
                                continue;
                            }
                            // 分割键和值（按第一个冒号分割）
                            String[] kv = pair.split(":", 2);
                            if (kv.length == 2) {
                                String key = kv[0].trim();
                                double value = Double.parseDouble(kv[1].trim());
                                // 向当前层级节点添加键值对
                                currentLevelNode.put(key, value);
                            }
                        }
                    }
                }
            }
        } else {
            rootNode.put("value", input);
        }

        return rootNode;
    }
}
