package com.dji.sample.control.controller;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.control.model.dto.ControlAuthority;
import com.dji.sample.control.model.enums.DroneAuthorityEnum;
import com.dji.sample.control.model.enums.RemoteDebugMethodEnum;
import com.dji.sample.control.model.param.*;
import com.dji.sample.control.service.IControlRedisService;
import com.dji.sample.control.service.IControlService;
import com.dji.sdk.common.HttpResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import java.sql.SQLException;

import static com.dji.sample.component.AuthInterceptor.TOKEN_CLAIM;

/**
 * @author sean
 * @version 1.2
 * @date 2022/7/29
 */
@RestController
@Slf4j
@RequestMapping("${url.control.prefix}${url.control.version}/devices")
public class DockController {

    @Autowired
    private IControlService controlService;

    @Autowired
    private IControlRedisService controlRedisService;

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{sn}/jobs/{service_identifier}")
    public HttpResultResponse createControlJob(@PathVariable String sn,
                                               @PathVariable("service_identifier") String serviceIdentifier,
                                               @Valid @RequestBody(required = false) RemoteDebugParam param) {
        return controlService.controlDockDebug(sn, RemoteDebugMethodEnum.find(serviceIdentifier), param);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{sn}/jobs/fly-to-point")
    public HttpResultResponse flyToPoint(HttpServletRequest request, @PathVariable String sn,
                                         @Valid @RequestBody FlyToPointParam param) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return controlService.flyToPoint(sn, param, customClaim);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @DeleteMapping("/{sn}/jobs/fly-to-point")
    public HttpResultResponse flyToPointStop(@PathVariable String sn) {
        return controlService.flyToPointStop(sn);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{sn}/jobs/takeoff-to-point")
    public HttpResultResponse takeoffToPoint(HttpServletRequest request, @PathVariable String sn,
                                             @Valid @RequestBody TakeoffToPointParam param) throws SQLException {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return controlService.takeoffToPoint(sn, param, customClaim);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{sn}/authority/flight")
    public HttpResultResponse seizeFlightAuthority(HttpServletRequest request, @PathVariable String sn) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return controlService.seizeAuthority(customClaim, sn, DroneAuthorityEnum.FLIGHT, null);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{sn}/authority/payload")
    public HttpResultResponse seizePayloadAuthority(HttpServletRequest request, @PathVariable String sn,
                                                    @Valid @RequestBody DronePayloadParam param) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        param.checkPayloadIndex();
        return controlService.seizeAuthority(customClaim, sn, DroneAuthorityEnum.PAYLOAD, param);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{sn}/payload/commands")
    public HttpResultResponse payloadCommands(@PathVariable String sn, @Valid @RequestBody PayloadCommandsParam param) throws Exception {
        param.getData().checkPayloadIndex();
        param.setSn(sn);
        return controlService.payloadCommands(param);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @GetMapping("/{dock_sn}/authority")
    public HttpResultResponse getControlAuthority(@PathVariable(name = "dock_sn") String dockSn) {
        ControlAuthority controlAuthority = controlRedisService.getControlAuthority(dockSn);
        return HttpResultResponse.success(controlAuthority);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @DeleteMapping("/{dock_sn}/authority")
    public HttpResultResponse exitControlAuthority(HttpServletRequest request,
                                                   @PathVariable(name = "dock_sn") String dockSn) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        controlRedisService.exitControlAuthority(customClaim, dockSn);
        return HttpResultResponse.success();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/{sn}/auth/cloud-control")
    public HttpResultResponse seizeCloudControlAuth(HttpServletRequest request, @PathVariable String sn) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return controlService.seizeCloudControlAuth(customClaim, sn);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @DeleteMapping("/{sn}/auth/cloud-control")
    public HttpResultResponse releaseCloudControlAuth(HttpServletRequest request, @PathVariable String sn) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return controlService.releaseCloudControlAuth(customClaim, sn);
    }
}
