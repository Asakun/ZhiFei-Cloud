package com.dji.sample.control.service.impl;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.component.websocket.model.BizCodeEnum;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.control.model.dto.AuthorityOwner;
import com.dji.sample.control.model.dto.AuthorityRevokedNotify;
import com.dji.sample.control.model.enums.DroneAuthorityEnum;
import com.dji.sample.control.model.enums.DroneControlKeyEnum;
import com.dji.sample.control.model.enums.RemoteDebugMethodEnum;
import com.dji.sample.control.model.param.*;
import com.dji.sample.control.service.IControlRedisService;
import com.dji.sample.control.service.IControlService;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.service.IDevicePayloadService;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.manage.service.IDeviceService;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sample.wayline.model.dto.WaylineJobDTO;
import com.dji.sample.wayline.model.enums.WaylineJobStatusEnum;
import com.dji.sample.wayline.service.IWaylineJobService;
import com.dji.sample.wayline.service.IWaylineRedisService;
import com.dji.sdk.cloudapi.control.*;
import com.dji.sdk.cloudapi.control.api.AbstractControlService;
import com.dji.sdk.cloudapi.debug.DebugMethodEnum;
import com.dji.sdk.cloudapi.debug.api.AbstractDebugService;
import com.dji.sdk.cloudapi.device.DeviceDomainEnum;
import com.dji.sdk.cloudapi.device.DockModeCodeEnum;
import com.dji.sdk.cloudapi.device.DroneModeCodeEnum;
import com.dji.sdk.cloudapi.device.PayloadIndex;
import com.dji.sdk.cloudapi.wayline.api.AbstractWaylineService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.SDKManager;
import com.dji.sdk.exception.CloudSDKErrorEnum;
import com.dji.sdk.mqtt.services.ServicesReplyData;
import com.dji.sdk.mqtt.services.TopicServicesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @author sean
 * @version 1.2
 * @date 2022/7/29
 */
@Service
@Slf4j
public class ControlServiceImpl implements IControlService {

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private IDevicePayloadService devicePayloadService;

    @Autowired
    private AbstractControlService abstractControlService;

    @Autowired
    private AbstractDebugService abstractDebugService;

    @Autowired
    @Qualifier("SDKWaylineService")
    private AbstractWaylineService abstractWaylineService;

    @Autowired
    private IWaylineJobService waylineJobService;

    @Autowired
    private IWaylineRedisService waylineRedisService;

    @Autowired
    private IControlRedisService controlRedisService;

    private RemoteDebugHandler checkDebugCondition(String sn, RemoteDebugParam param, RemoteDebugMethodEnum controlMethodEnum) {
        RemoteDebugHandler handler = Objects.nonNull(controlMethodEnum.getClazz()) ?
                mapper.convertValue(Objects.nonNull(param) ? param : new Object(), controlMethodEnum.getClazz())
                : new RemoteDebugHandler();
        if (!handler.canPublish(sn)) {
            throw new RuntimeException("The current state of the dock does not support this function.");
        }
        return handler;
    }

    @Override
    public HttpResultResponse controlDockDebug(String sn, RemoteDebugMethodEnum controlMethodEnum, RemoteDebugParam param) {
        DebugMethodEnum methodEnum = controlMethodEnum.getDebugMethodEnum();
        RemoteDebugHandler data = checkDebugCondition(sn, param, controlMethodEnum);

        boolean isExist = deviceRedisService.checkDeviceOnline(sn);
        if (!isExist) {
            return HttpResultResponse.error("The dock is offline.");
        }
        TopicServicesResponse response;
        long startTime = System.currentTimeMillis();
        switch (controlMethodEnum) {
            case RETURN_HOME:
                response = abstractWaylineService.returnHome(SDKManager.getDeviceSDK(sn));
                break;
            case RETURN_HOME_CANCEL:
                response = abstractWaylineService.returnHomeCancel(SDKManager.getDeviceSDK(sn));
                break;
            default:
                response = abstractDebugService.remoteDebug(SDKManager.getDeviceSDK(sn), methodEnum,
                        Objects.nonNull(methodEnum.getClazz()) ? mapper.convertValue(data, methodEnum.getClazz()) : null);
        }
        long elapsed = System.currentTimeMillis() - startTime;
        ServicesReplyData serviceReply = (ServicesReplyData) response.getData();
        if (!serviceReply.getResult().isSuccess()) {
            return HttpResultResponse.error(serviceReply.getResult());
        }
        return HttpResultResponse.success(Map.of("time", elapsed));
    }

    private void checkFlyToCondition(CustomClaim customClaim, String dockSn) {
        // TODO 设备固件版本不兼容情况
        Optional<DeviceDTO> dockOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (dockOpt.isEmpty()) {
            throw new RuntimeException("The dock is offline, please restart the dock.");
        }

        DroneModeCodeEnum deviceMode = deviceService.getDeviceMode(dockOpt.get().getChildDeviceSn());
        if (DroneModeCodeEnum.MANUAL != deviceMode) {
            throw new RuntimeException("The current state of the drone does not support this function, please try again later.");
        }

        if (dockOpt.get().getDomain() == DeviceDomainEnum.DOCK) {
            HttpResultResponse result = seizeAuthority(customClaim, dockSn, DroneAuthorityEnum.FLIGHT, null);
            if (HttpResultResponse.CODE_SUCCESS != result.getCode()) {
                throw new IllegalArgumentException(result.getMessage());
            }
        }
    }

    @Override
    public HttpResultResponse flyToPoint(String sn, FlyToPointParam param, CustomClaim customClaim) {
        checkFlyToCondition(customClaim, sn);

        param.setFlyToId(UUID.randomUUID().toString());
        TopicServicesResponse<ServicesReplyData> response = abstractControlService.flyToPoint(
                SDKManager.getDeviceSDK(sn), mapper.convertValue(param, FlyToPointRequest.class));
        ServicesReplyData reply = response.getData();
        return reply.getResult().isSuccess() ?
                HttpResultResponse.success()
                : HttpResultResponse.error("Flying to the target point failed. " + reply.getResult());
    }

    @Override
    public HttpResultResponse flyToPointStop(String sn) {
        TopicServicesResponse<ServicesReplyData> response = abstractControlService.flyToPointStop(SDKManager.getDeviceSDK(sn));
        ServicesReplyData reply = response.getData();

        return reply.getResult().isSuccess() ?
                HttpResultResponse.success()
                : HttpResultResponse.error("The drone flying to the target point failed to stop. " + reply.getResult());
    }

    private DeviceDTO checkTakeoffCondition(CustomClaim customClaim, String dockSn) {
        Optional<DeviceDTO> dockOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (dockOpt.isEmpty()) {
            throw new RuntimeException("The device is offline.");
        } else if (dockOpt.get().getDomain() == DeviceDomainEnum.DOCK && DockModeCodeEnum.IDLE != deviceService.getDockMode(dockSn)) {
            throw new RuntimeException("The dock current state does not support takeoff.");
        } else if (dockOpt.get().getDomain() == DeviceDomainEnum.REMOTER_CONTROL && DroneModeCodeEnum.IDLE != deviceService.getRcDroneMode(dockOpt.get().getChildDeviceSn())) {
            throw new RuntimeException("The rc current state does not support takeoff.");
        }
        HttpResultResponse result;
        if (dockOpt.get().getDomain() == DeviceDomainEnum.REMOTER_CONTROL) {
            result = seizeCloudControlAuth(customClaim, dockSn);
        } else {
            result = seizeAuthority(customClaim, dockSn, DroneAuthorityEnum.FLIGHT, null);
        }
        if (HttpResultResponse.CODE_SUCCESS != result.getCode()) {
            throw new IllegalArgumentException(result.getMessage());
        }
        return dockOpt.get();
    }

    @Override
    public HttpResultResponse takeoffToPoint(String sn, TakeoffToPointParam param, CustomClaim customClaim) throws SQLException {
        DeviceDTO deviceOnline = checkTakeoffCondition(customClaim, sn);

        param.setFlightId(UUID.randomUUID().toString());
        Optional<WaylineJobDTO> waylineJobOpt = waylineJobService.createWaylineJob(param, customClaim.getWorkspaceId(), customClaim.getUsername(), sn);
        if (waylineJobOpt.isEmpty()) {
            throw new SQLException("Failed to create wayline job.");
        }
        TopicServicesResponse<ServicesReplyData> response = abstractControlService.takeoffToPoint(
                SDKManager.getDeviceSDK(sn), mapper.convertValue(param, TakeoffToPointRequest.class));
        ServicesReplyData reply = response.getData();
        WaylineJobDTO job = waylineJobOpt.get();
        if (reply.getResult().isSuccess()) {
            waylineJobService.updateJob(WaylineJobDTO.builder()
                    .jobId(job.getJobId())
                    .executeTime(LocalDateTime.now())
                    .status(WaylineJobStatusEnum.IN_PROGRESS.getVal())
                    .build());
            waylineRedisService.setDuringFlightInfo(sn, DuringFlightInfo.builder().
                    flightId(param.getFlightId()).droneSn(deviceOnline.getChildDeviceSn()).mediaCount(0).build());
            return HttpResultResponse.success();
        }else {
            waylineJobService.updateJob(WaylineJobDTO.builder()
                    .jobId(job.getJobId())
                    .executeTime(LocalDateTime.now())
                    .status(WaylineJobStatusEnum.FAILED.getVal())
                    .completedTime(LocalDateTime.now())
                    .code(reply.getResult().getCode()).build());
            return HttpResultResponse.error("The drone failed to take off. " + reply.getResult());
        }
    }

    private boolean checkAuthorityCondition(CustomClaim customClaim, Optional<AuthorityOwner> authorityOwner) {
        if (authorityOwner.isPresent()) {
            AuthorityOwner owner = authorityOwner.get();
            if (owner.getAuthorityOwnerId() != null) {
                if (!customClaim.getId().equals(owner.getAuthorityOwnerId())) {
                    // user level < 3 不能夺权
                    return customClaim.getLevel() >= 3;
                }
            }
        }
        return true;
    }

    private boolean hasAuthority(CustomClaim customClaim, Optional<AuthorityOwner> authorityOwner) {
        if (authorityOwner.isPresent()) {
            AuthorityOwner owner = authorityOwner.get();
            return customClaim.getId().equals(owner.getAuthorityOwnerId());
        }
        return false;
    }

    private AuthorityOwner updateAuthorityInfo(CustomClaim customClaim, String sn, DroneAuthorityEnum authority) {
        log.debug("Updating authority info for sn: {}", sn);
        Optional<AuthorityOwner> authorityOwner = controlRedisService.getAuthorityOwner(authority, sn);
        log.debug("Get {} authority info: {}", authority, authorityOwner);
        String oldUid = null;
        if (authorityOwner.isPresent()) {
            AuthorityOwner owner = authorityOwner.get();
            oldUid = !customClaim.getId().equals(owner.getAuthorityOwnerId()) ? owner.getAuthorityOwnerId() : null;
        }
        AuthorityOwner newOwner = AuthorityOwner.builder().authorityOwner(customClaim.getUsername()).authorityOwnerId(customClaim.getId()).build();
        newOwner = controlRedisService.setAuthorityOwner(authority, sn, newOwner);
        log.debug("Save authority info:{}.", newOwner);
        if (StringUtils.isNotEmpty(oldUid)) {
            webSocketMessageService.sendBatch(AuthorityRevokedNotify.builder().usurper(customClaim.getUsername()).authorityRevoked(authority.name()).dockSn(sn).build(),
                    customClaim.getWorkspaceId(), oldUid, BizCodeEnum.AUTHORITY_REVOKED.getCode());
            log.debug("Sent authority info to {}.", oldUid);
        }
        return newOwner;
    }

    @Override
    public HttpResultResponse seizeAuthority(CustomClaim customClaim, String sn, DroneAuthorityEnum authority, DronePayloadParam param) {
        Optional<AuthorityOwner> authorityOwner = controlRedisService.getAuthorityOwner(authority, sn);
        if (!checkAuthorityCondition(customClaim, authorityOwner)) {
            return HttpResultResponse.error("The user does not have permission to seize this authority.");
        }
        TopicServicesResponse<ServicesReplyData> response;
        switch (authority) {
            case FLIGHT:
                if (deviceService.checkAuthorityFlight(sn) && hasAuthority(customClaim, authorityOwner)) {
                    return HttpResultResponse.success();
                }
                response = abstractControlService.flightAuthorityGrab(SDKManager.getDeviceSDK(sn));
                break;
            case PAYLOAD:
                if (checkPayloadAuthority(sn, param.getPayloadIndex()) && hasAuthority(customClaim, authorityOwner)) {
                    return HttpResultResponse.success();
                }
                response = abstractControlService.payloadAuthorityGrab(SDKManager.getDeviceSDK(sn),
                        new PayloadAuthorityGrabRequest().setPayloadIndex(new PayloadIndex(param.getPayloadIndex())));
                break;
            default:
                return HttpResultResponse.error(CloudSDKErrorEnum.INVALID_PARAMETER);
        }

        ServicesReplyData serviceReply = response.getData();
        if (serviceReply.getResult().isSuccess()) {
            AuthorityOwner owner = updateAuthorityInfo(customClaim, sn, authority);
            return HttpResultResponse.success(owner);
        }
        return HttpResultResponse.error(serviceReply.getResult());
    }

    private Boolean checkPayloadAuthority(String sn, String payloadIndex) {
        Optional<DeviceDTO> dockOpt = deviceRedisService.getDeviceOnline(sn);
        if (dockOpt.isEmpty()) {
            throw new RuntimeException("The dock is offline, please restart the dock.");
        }
        return devicePayloadService.checkAuthorityPayload(dockOpt.get().getChildDeviceSn(), payloadIndex);
    }

    @Override
    public HttpResultResponse payloadCommands(PayloadCommandsParam param) throws Exception {
        param.getCmd().getClazz()
                .getDeclaredConstructor(DronePayloadParam.class)
                .newInstance(param.getData())
                .checkCondition(param.getSn());

        long startTime = System.currentTimeMillis();
        TopicServicesResponse<ServicesReplyData> response = abstractControlService.payloadControl(
                SDKManager.getDeviceSDK(param.getSn()), param.getCmd().getCmd(),
                mapper.convertValue(param.getData(), param.getCmd().getCmd().getClazz()));
        long elapsed = System.currentTimeMillis() - startTime;

        ServicesReplyData serviceReply = response.getData();
        return serviceReply.getResult().isSuccess() ?
                HttpResultResponse.success(Map.of("time", elapsed))
                : HttpResultResponse.error(serviceReply.getResult());
    }

    @Override
    public HttpResultResponse seizeCloudControlAuth(CustomClaim customClaim, String sn) {
//        Optional<AuthorityOwner> authorityOwner = controlRedisService.getAuthorityOwner(DroneAuthorityEnum.FLIGHT, sn);
//        if (!checkAuthorityCondition(customClaim, authorityOwner)) {
//            return HttpResultResponse.error("The user does not have permission to seize this authority.");
//        }
//
//        if (hasAuthority(customClaim, authorityOwner)) {
//            return HttpResultResponse.success();
//        }
        CloudControlAuthGrabRequest req = new CloudControlAuthGrabRequest().setUserId(customClaim.getId())
                .setUserCallsign(customClaim.getUsername()).setControlKeys(List.of(DroneControlKeyEnum.FLIGHT.getKey()));
        TopicServicesResponse<ServicesReplyData> response = abstractControlService.cloudControlAuthGrab(SDKManager.getDeviceSDK(sn),req);

        ServicesReplyData serviceReply = response.getData();
        if (serviceReply.getResult().isSuccess()) {
            return HttpResultResponse.success();
        }
        return HttpResultResponse.error(serviceReply.getResult());
    }

    @Override
    public HttpResultResponse releaseCloudControlAuth(CustomClaim customClaim, String sn) {
//        Optional<AuthorityOwner> flightOpt = controlRedisService.getAuthorityOwner(DroneAuthorityEnum.FLIGHT, sn);
//        AuthorityExitNotify authorityExitNotify = new AuthorityExitNotify();
//        if (flightOpt.isPresent()) {
//            AuthorityOwner owner = flightOpt.get();
//            if (customClaim.getId().equals(owner.getAuthorityOwnerId())) {
//                authorityExitNotify.setFlightAuthorityExitFromDockSn(sn);
//                authorityExitNotify.setUsername(customClaim.getUsername());
//            }
//        }
//        if (StringUtils.isEmpty(authorityExitNotify.getUsername())) {return HttpResultResponse.error("user not have the auth");}

        CloudControlAuthGrabRequest req = new CloudControlAuthGrabRequest().setControlKeys(List.of(DroneControlKeyEnum.FLIGHT.getKey()));
        TopicServicesResponse<ServicesReplyData> response = abstractControlService.cloudControlAuthRelease(SDKManager.getDeviceSDK(sn),req);

        ServicesReplyData serviceReply = response.getData();
        if (serviceReply.getResult().isSuccess()) {
//            controlRedisService.deleteAuthorityOwner(DroneAuthorityEnum.FLIGHT, sn);
//            webSocketMessageService.sendBatch(customClaim.getWorkspaceId(),BizCodeEnum.AUTHORITY_EXIT.getCode(), authorityExitNotify);
//            log.debug("Sent authority exit info: {}.", authorityExitNotify);
            return HttpResultResponse.success();
        }
        return HttpResultResponse.error(serviceReply.getResult());
    }
}
