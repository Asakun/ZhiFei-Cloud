package com.dji.sample.psdk.service;

import com.dji.sample.psdk.model.entity.PsdkWidgetEntity;
import com.dji.sample.psdk.model.enums.PsdkWidgetNameEnum;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface IPsdkWidgetService {
    Optional<PsdkWidgetEntity> getCurrentWidget(String deviceSn, Integer psdkIndex);

    Optional<PsdkWidgetEntity> getRedisWidget(String deviceSn, Integer psdkIndex);

    List<PsdkWidgetEntity> getRedisWidgetByPrefix(String deviceSn);

    Map<String, List<PsdkWidgetEntity>> getAllWidget(String workspaceId);

    Set<PsdkWidgetNameEnum> getRedisWidgetNames(String deviceSn);

    void unbindingWidget(String deviceSn);
}
