package com.dji.sample.manage.controller;

import com.dji.sample.manage.model.dto.OsdDockDroneDTO;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.manage.service.IOsdService;
import com.dji.sdk.cloudapi.device.OsdDock;
import com.dji.sdk.common.HttpResultResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@Slf4j
@RequestMapping("${url.manage.prefix}${url.manage.version}/workspaces")
public class OsdController {

    @Autowired
    private IOsdService osdService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @GetMapping("/{workspace_id}/flight-osd/{job_id}")
    public HttpResultResponse getOsdDockDronesByJobId(@PathVariable("workspace_id") String workspaceId,
                                          @PathVariable("job_id") String jobId) throws JsonProcessingException {
        List<OsdDockDroneDTO> data = osdService.getOsdDockDronesByJobId(jobId);
        return HttpResultResponse.success(data);
    }

    @GetMapping("/{workspace_id}/osd/{dock_sn}")
    public HttpResultResponse getDeviceOsd(@PathVariable("workspace_id") String workspaceId,
                                           @PathVariable("dock_sn") String dockSn) {
        Optional<OsdDock> data = deviceRedisService.getDeviceOsd(dockSn, OsdDock.class);
        return HttpResultResponse.success(data.orElse(null));
    }
}
