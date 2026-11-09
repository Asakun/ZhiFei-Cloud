package com.dji.sample.psdk.service;


import com.dji.sample.psdk.model.dto.PsdkFloatingWindowTextDto;
import com.dji.sample.psdk.model.dto.PsdkWidgetValueSetDTO;
import com.dji.sdk.common.HttpResultResponse;

import java.util.List;

public interface IPsdkService {
    HttpResultResponse sendPsdkWidgetValueSet(String dockSn, Integer psdkIndex, Integer index, Integer value, String name);

    List<PsdkFloatingWindowTextDto> getPsdkFloatingWindowDatas(String jobId, Integer psdkIndex);

    /**
     * 获取 PSDK widget 值设置列表，可按 name（模糊）和 sn（精确）过滤。
     *
     * @param name widget 名称，模糊匹配，可为空
     * @param sn   设备序列号，精确匹配，可为空
     * @return 匹配的记录列表
     */
    List<PsdkWidgetValueSetDTO> getWidgetValueSetList(String name, String sn);
}
