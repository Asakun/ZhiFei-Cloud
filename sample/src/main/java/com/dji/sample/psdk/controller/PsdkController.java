package com.dji.sample.psdk.controller;

import com.dji.sample.psdk.model.dto.PsdkFloatingWindowTextDto;
import com.dji.sample.psdk.model.dto.PsdkWidgetValueSetDTO;
import com.dji.sample.psdk.model.dto.WidgetValueSetDto;
import com.dji.sample.psdk.model.entity.PsdkWidgetEntity;
import com.dji.sample.psdk.service.IPsdkService;
import com.dji.sample.psdk.service.IPsdkWidgetService;
import com.dji.sdk.common.HttpResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("${url.manage.prefix}${url.manage.version}/psdk")
public class PsdkController {

    @Autowired
    private IPsdkService psdkService;

    @Autowired
    private IPsdkWidgetService psdkWidgetService;

    @GetMapping("/{workspace_id}/floating_window_datas")
    public List<PsdkFloatingWindowTextDto> getFloatingWindowDatas(@PathVariable(name = "workspace_id") String workspaceId,
                                                                  @RequestParam(defaultValue = "2") Integer psdkIndex,
                                                                  @RequestParam String jobId) {
        return psdkService.getPsdkFloatingWindowDatas(jobId, psdkIndex);
    }

    @GetMapping("/{workspace_id}/widget")
    public Map<String, List<PsdkWidgetEntity>> getWidget(@PathVariable(name = "workspace_id") String workspaceId) {
        return psdkWidgetService.getAllWidget(workspaceId);
    }

    /**
     * 获取 PSDK widget 值设置列表。
     *
     * @param name widget 名称，模糊匹配，可为空
     * @param sn   设备序列号，精确匹配，可为空
     * @return 匹配的记录列表
     */
    @GetMapping("/widget_value_set_list")
    public HttpResultResponse<List<PsdkWidgetValueSetDTO>> getWidgetValueSetList(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sn) {
        return HttpResultResponse.success(psdkService.getWidgetValueSetList(name, sn));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{workspace_id}/widget_value_set")
    public HttpResultResponse sendPsdkWidgetValueSet(@PathVariable(name = "workspace_id") String workspaceId,
                                                         @Valid @RequestBody WidgetValueSetDto dto) {
        return psdkService.sendPsdkWidgetValueSet(dto.getDockSn(), dto.getPsdkIndex(), dto.getIndex(), dto.getValue(),
                dto.getName());
    }
}
