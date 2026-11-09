package com.dji.sample.psdk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.psdk.dao.IPsdkWidgetMapper;
import com.dji.sample.psdk.model.entity.PsdkWidgetEntity;
import com.dji.sample.psdk.model.enums.PsdkWidgetNameEnum;
import com.dji.sample.psdk.service.IPsdkWidgetService;
import com.dji.sdk.cloudapi.device.PsdkWidgetValue;
import com.dji.sdk.cloudapi.device.PsdkWidgetValues;
import com.dji.sdk.cloudapi.device.api.AbstractDeviceService;
import com.dji.sdk.mqtt.state.TopicStateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@Transactional
@Slf4j
public class PsdkWidgetServiceImpl extends AbstractDeviceService implements IPsdkWidgetService {

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private IPsdkWidgetMapper mapper;

    @Override
    public void dronePayloadWidgetUpdate(TopicStateRequest<PsdkWidgetValues> request, MessageHeaders headers) {
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(request.getFrom());
        if (deviceOpt.isEmpty()) {
            return;
        }
        Optional<DeviceDTO> dockOpt = deviceRedisService.getDeviceOnline(request.getGateway());
        if (dockOpt.isEmpty()) {
            return;
        }
        PsdkWidgetValues psdkWidgetValues = request.getData();
        String deviceSn = request.getFrom();
        String workspaceId = deviceOpt.get().getWorkspaceId();
        for (PsdkWidgetValue widgetValue: psdkWidgetValues.getPsdkWidgetValues()) {
            if (!StringUtils.hasText(widgetValue.getPsdkName())) {
                continue;
            }
            Optional<PsdkWidgetEntity> widgetOpt = getRedisWidget(deviceSn, widgetValue.getPsdkIndex());
            if (widgetOpt.isPresent()) {
                if (widgetOpt.get().getPsdkName().equals(widgetValue.getPsdkName())) {
                    continue;
                } else {
                    disableWidgets(deviceSn, widgetValue.getPsdkIndex());
                }
            }
            saveWidget(widgetValue, deviceSn, workspaceId);
        }
    }

    @Override
    public Optional<PsdkWidgetEntity> getCurrentWidget(String deviceSn, Integer psdkIndex) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<PsdkWidgetEntity>()
                .eq(PsdkWidgetEntity::getDeviceSn, deviceSn)
                .eq(PsdkWidgetEntity::getPsdkIndex, psdkIndex)
                .eq(PsdkWidgetEntity::getStatus, 1)
        ));
    }

    private String getRedisKey(String deviceSn, Integer psdkIndex) {
        return RedisConst.PSDK_WIDGET + deviceSn + "-" + psdkIndex;
    }

    private void setRedisWidget(String deviceSn, Integer psdkIndex, PsdkWidgetEntity widget) {
        RedisOpsUtils.set(getRedisKey(deviceSn, psdkIndex), widget);
    }

    private void deleteRedisWidget(String deviceSn, Integer psdkIndex) {
        RedisOpsUtils.del(getRedisKey(deviceSn, psdkIndex));
    }

    @Override
    public Optional<PsdkWidgetEntity> getRedisWidget(String deviceSn, Integer psdkIndex) {
        Optional<PsdkWidgetEntity> widgetOpt = Optional.ofNullable((PsdkWidgetEntity) RedisOpsUtils.get(getRedisKey(deviceSn, psdkIndex)));
        if (widgetOpt.isPresent()) {
            return widgetOpt;
        }
        widgetOpt = getCurrentWidget(deviceSn, psdkIndex);
        widgetOpt.ifPresent(psdkWidget -> setRedisWidget(deviceSn, psdkIndex, psdkWidget));
        return widgetOpt;
    }

    private void disableWidgets(String deviceSn, Integer psdkIndex) {
        mapper.update(PsdkWidgetEntity.builder().status(0).build(),
                new LambdaUpdateWrapper<PsdkWidgetEntity>()
                        .eq(PsdkWidgetEntity::getDeviceSn, deviceSn)
                        .eq(PsdkWidgetEntity::getPsdkIndex, psdkIndex)
                        .eq(PsdkWidgetEntity::getStatus, 1)
                        .set(PsdkWidgetEntity::getPayloadSn, null)
        );
        deleteRedisWidget(deviceSn, psdkIndex);
    }

    @Override
    public List<PsdkWidgetEntity> getRedisWidgetByPrefix(String deviceSn) {
        Map<String, Object> map = RedisOpsUtils.getKeyValByPrefix(RedisConst.PSDK_WIDGET + deviceSn);
        List<PsdkWidgetEntity> widgets = new ArrayList<>();
        for (Object value : map.values()) {
            widgets.add((PsdkWidgetEntity) value);
        }
        return widgets;
    }

    @Override
    public Map<String, List<PsdkWidgetEntity>> getAllWidget(String workspaceId) {
        List<PsdkWidgetEntity> list = mapper.selectList(new LambdaQueryWrapper<PsdkWidgetEntity>()
                .eq(PsdkWidgetEntity::getWorkspaceId, workspaceId)
                .eq(PsdkWidgetEntity::getStatus, 1));
        Map<String, List<PsdkWidgetEntity>> mapList = new HashMap<>();
        for (PsdkWidgetEntity psdkWidgetEntity : list) {
            if (mapList.containsKey(psdkWidgetEntity.getDeviceSn())){
                mapList.get(psdkWidgetEntity.getDeviceSn()).add(psdkWidgetEntity);
            } else {
                List<PsdkWidgetEntity> widgets = new ArrayList<>();
                widgets.add(psdkWidgetEntity);
                mapList.put(psdkWidgetEntity.getDeviceSn(), widgets);
            }
        }
        return mapList;
    }

    @Override
    public Set<PsdkWidgetNameEnum> getRedisWidgetNames(String deviceSn) {
        List<PsdkWidgetEntity> widgets = getRedisWidgetByPrefix(deviceSn);
        Set<PsdkWidgetNameEnum> widgetNameEnums = new HashSet<>();
        for (PsdkWidgetEntity widget : widgets) {
            PsdkWidgetNameEnum enums = PsdkWidgetNameEnum.find(widget.getPsdkName());
            if (enums != null) {
                widgetNameEnums.add(enums);
            }
        }
        return widgetNameEnums;
    }

    private void saveWidget(PsdkWidgetValue psdkWidgetValue, String deviceSn, String workspaceId) {
        mapper.insert(PsdkWidgetEntity.builder()
                        .payloadSn(String.format("%s-%d",deviceSn,psdkWidgetValue.getPsdkIndex()))
                        .psdkSn(psdkWidgetValue.getPsdkSn())
                        .psdkLibVersion(psdkWidgetValue.getPsdkLibVersion())
                        .psdkVersion(psdkWidgetValue.getPsdkVersion())
                        .psdkType(psdkWidgetValue.getPsdkType())
                        .psdkName(psdkWidgetValue.getPsdkName())
                        .psdkIndex(psdkWidgetValue.getPsdkIndex())
                        .deviceSn(deviceSn)
                        .workspaceId(workspaceId)
                .build());
    }

    @Override
    public void unbindingWidget(String deviceSn) {
        List<PsdkWidgetEntity> widgets = getRedisWidgetByPrefix(deviceSn);
        for (PsdkWidgetEntity widget : widgets) {
            disableWidgets(widget.getDeviceSn(), widget.getPsdkIndex());
        }
    }

}
