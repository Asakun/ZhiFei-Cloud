package com.dji.sample.manage.service.impl;

import com.dji.sample.component.websocket.model.BizCodeEnum;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.manage.dao.IOsdDockDroneMapper;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.model.dto.DevicePayloadReceiver;
import com.dji.sample.manage.model.dto.DeviceStateDto;
import com.dji.sample.manage.model.entity.OsdDockDroneEntity;
import com.dji.sample.manage.model.enums.DeviceFirmwareStatusEnum;
import com.dji.sample.manage.model.param.DeviceQueryParam;
import com.dji.sample.manage.service.IDeviceDictionaryService;
import com.dji.sample.manage.service.IDevicePayloadService;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.manage.service.IDeviceService;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sample.wayline.service.IWaylineRedisService;
import com.dji.sdk.cloudapi.device.*;
import com.dji.sdk.cloudapi.device.api.AbstractDeviceService;
import com.dji.sdk.cloudapi.property.DockDroneCommanderFlightHeight;
import com.dji.sdk.cloudapi.property.DockDroneCommanderModeLostAction;
import com.dji.sdk.cloudapi.property.DockDroneRthMode;
import com.dji.sdk.cloudapi.tsa.DeviceIconUrl;
import com.dji.sdk.cloudapi.tsa.IconUrlEnum;
import com.dji.sdk.config.version.GatewayManager;
import com.dji.sdk.common.SDKManager;
import com.dji.sdk.mqtt.MqttReply;
import com.dji.sdk.mqtt.osd.TopicOsdRequest;
import com.dji.sdk.mqtt.state.TopicStateRequest;
import com.dji.sdk.mqtt.state.TopicStateResponse;
import com.dji.sdk.mqtt.status.TopicStatusRequest;
import com.dji.sdk.mqtt.status.TopicStatusResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author sean
 * @version 1.7
 * @date 2023/7/4
 */
@Service
@Slf4j
public class SDKDeviceService extends AbstractDeviceService {

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IDeviceDictionaryService dictionaryService;

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Autowired
    private IDevicePayloadService devicePayloadService;

    @Autowired
    private IWaylineRedisService waylineRedisService;

    @Autowired
    private IOsdDockDroneMapper osdDockDroneMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public TopicStatusResponse<MqttReply> updateTopoOnline(TopicStatusRequest<UpdateTopo> request, MessageHeaders headers) {
        UpdateTopoSubDevice updateTopoSubDevice = request.getData().getSubDevices().get(0);
        String deviceSn = updateTopoSubDevice.getSn();

        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(deviceSn);
        Optional<DeviceDTO> gatewayOpt = deviceRedisService.getDeviceOnline(request.getFrom());
        GatewayManager gatewayManager = SDKManager.registerDevice(request.getFrom(), deviceSn,
                request.getData().getDomain(), request.getData().getType(),
                request.getData().getSubType(), request.getData().getThingVersion(), updateTopoSubDevice.getThingVersion());

        if (deviceOpt.isPresent() && gatewayOpt.isPresent()) {
            deviceOnlineAgain(deviceOpt.get().getWorkspaceId(), request.getFrom(), deviceSn);
            // TODO 动态设置setMethod("update_topo")
            return new TopicStatusResponse<MqttReply>().setMethod("update_topo").setData(MqttReply.success());
        }

        changeSubDeviceParent(deviceSn, request.getFrom());

        DeviceDTO gateway = deviceGatewayConvertToDevice(request.getFrom(), request.getData());
        Optional<DeviceDTO> gatewayEntityOpt = onlineSaveDevice(gateway, deviceSn, null);
        if (gatewayEntityOpt.isEmpty()) {
            log.error("Failed to go online, please check the status data or code logic.");
            return null;
        }
        DeviceDTO subDevice = subDeviceConvertToDevice(updateTopoSubDevice);
        Optional<DeviceDTO> subDeviceEntityOpt = onlineSaveDevice(subDevice, null, gateway.getDeviceSn());
        if (subDeviceEntityOpt.isEmpty()) {
            log.error("Failed to go online, please check the status data or code logic.");
            return null;
        }
        subDevice = subDeviceEntityOpt.get();
        gateway = gatewayEntityOpt.get();
        dockGoOnline(gateway, subDevice);
        deviceService.gatewayOnlineSubscribeTopic(gatewayManager);

        if (!StringUtils.hasText(subDevice.getWorkspaceId())) {
            return new TopicStatusResponse<MqttReply>().setMethod("update_topo").setData(MqttReply.success());
        }

        // Subscribe to topic related to drone devices.
        deviceService.subDeviceOnlineSubscribeTopic(gatewayManager);
        deviceService.pushDeviceOnlineTopo(gateway.getWorkspaceId(), gateway.getDeviceSn(), subDevice.getDeviceSn());

        log.debug("{} online.", subDevice.getDeviceSn());
        return new TopicStatusResponse<MqttReply>().setMethod("update_topo").setData(MqttReply.success());
    }

    @Override
    public TopicStatusResponse<MqttReply> updateTopoOffline(TopicStatusRequest<UpdateTopo> request, MessageHeaders headers) {
        GatewayManager gatewayManager = SDKManager.registerDevice(request.getFrom(), null,
                request.getData().getDomain(), request.getData().getType(),
                request.getData().getSubType(), request.getData().getThingVersion(), null);
        deviceService.gatewayOnlineSubscribeTopic(gatewayManager);
        // Only the remote controller is logged in and the aircraft is not connected.
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(request.getFrom());
        if (deviceOpt.isEmpty()) {
            // When connecting for the first time
            DeviceDTO gatewayDevice = deviceGatewayConvertToDevice(request.getFrom(), request.getData());
            Optional<DeviceDTO> gatewayDeviceOpt = onlineSaveDevice(gatewayDevice, null, null);
            if (gatewayDeviceOpt.isEmpty()) {
                return null;
            }
            deviceService.pushDeviceOnlineTopo(gatewayDeviceOpt.get().getWorkspaceId(), request.getFrom(), null);
            return new TopicStatusResponse<MqttReply>().setMethod("update_topo").setData(MqttReply.success());
        }

        String deviceSn = deviceOpt.get().getChildDeviceSn();
        if (!StringUtils.hasText(deviceSn)) {
            return new TopicStatusResponse<MqttReply>().setMethod("update_topo").setData(MqttReply.success());
        }

        deviceService.subDeviceOffline(deviceSn);
        return new TopicStatusResponse<MqttReply>().setMethod("update_topo").setData(MqttReply.success());
    }

    @Override
    public void osdDock(TopicOsdRequest<OsdDock> request, MessageHeaders headers) {
        String from = request.getFrom();
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(from);
        if (deviceOpt.isEmpty() || !StringUtils.hasText(deviceOpt.get().getWorkspaceId())) {
            deviceOpt = deviceService.getDeviceBySn(from);
            if (deviceOpt.isEmpty()) {
                log.error("Please restart the drone.");
                return;
            }
        }

        DeviceDTO device = deviceOpt.get();
        if (!StringUtils.hasText(device.getWorkspaceId())) {
            log.error("Please bind the dock first.");
        }
        if (StringUtils.hasText(device.getChildDeviceSn())) {
            deviceService.getDeviceBySn(device.getChildDeviceSn()).ifPresent(device::setChildren);
        }

        deviceRedisService.setDeviceOnline(device);
        fillDockOsd(from, request.getData());

        deviceService.pushOsdDataToWeb(device.getWorkspaceId(), BizCodeEnum.DOCK_OSD, from, request.getData());
    }

    private void insertOsd(TopicOsdRequest<?> request) throws JsonProcessingException {
        Optional<DuringFlightInfo> duringFlightInfo = waylineRedisService.getDuringFlightInfo(request.getGateway());
        if (duringFlightInfo.isPresent()) {
            OsdDockDroneEntity entity = OsdDockDroneEntity.builder()
                    .jobId(duringFlightInfo.get().getFlightId())
                    .data(objectMapper.writeValueAsString(request.getData()))
                    .createTime(request.getTimestamp())
                    .build();
            osdDockDroneMapper.insert(entity);
        }
    }

    @Override
    public void osdDockDrone(TopicOsdRequest<OsdDockDrone> request, MessageHeaders headers) throws JsonProcessingException {
        String from = request.getFrom();
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(from);
        if (deviceOpt.isEmpty()) {
            deviceOpt = deviceService.getDeviceBySn(from);
            if (deviceOpt.isEmpty()) {
                log.error("Please restart the drone.");
                return;
            }
        }

        if (!StringUtils.hasText(deviceOpt.get().getWorkspaceId())) {
            log.error("Please restart the drone.");
        }

        DeviceDTO device = deviceOpt.get();
        deviceRedisService.setDeviceOnline(device);
        deviceRedisService.setDeviceOsd(from, request.getData());

        insertOsd(request);

        deviceService.pushOsdDataToWeb(device.getWorkspaceId(), BizCodeEnum.DEVICE_OSD, from, request.getData());
    }

    @Override
    public void osdRemoteControl(TopicOsdRequest<OsdRemoteControl> request, MessageHeaders headers) {
        String from = request.getFrom();
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(from);
        if (deviceOpt.isEmpty()) {
            deviceOpt = deviceService.getDeviceBySn(from);
            if (deviceOpt.isEmpty()) {
                log.error("Please restart the drone.");
                return;
            }
        }
        DeviceDTO device = deviceOpt.get();
        if (StringUtils.hasText(device.getChildDeviceSn())) {
            deviceService.getDeviceBySn(device.getChildDeviceSn()).ifPresent(device::setChildren);
        }
        deviceRedisService.setDeviceOnline(device);
        deviceRedisService.setDeviceOsd(from, request.getData());

        OsdRemoteControl data = request.getData();
        deviceService.pushOsdDataToPilot(device.getWorkspaceId(), from,
                new DeviceOsdHost()
                        .setLatitude(data.getLatitude())
                        .setLongitude(data.getLongitude())
                        .setHeight(data.getHeight()));
        deviceService.pushOsdDataToWeb(device.getWorkspaceId(), BizCodeEnum.RC_OSD, from, data);

    }

    @Override
    public void osdRcDrone(TopicOsdRequest<OsdRcDrone> request, MessageHeaders headers) throws JsonProcessingException {
        String from = request.getFrom();
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(from);
        if (deviceOpt.isEmpty()) {
            deviceOpt = deviceService.getDeviceBySn(from);
            if (deviceOpt.isEmpty()) {
                log.error("Please restart the drone.");
                return;
            }
        }
        DeviceDTO device = deviceOpt.get();
        if (!StringUtils.hasText(device.getWorkspaceId())) {
            log.error("Please bind the drone first.");
        }

        deviceRedisService.setDeviceOnline(device);
        deviceRedisService.setDeviceOsd(from, request.getData());

        insertOsd(request);

        OsdRcDrone data = request.getData();
        deviceService.pushOsdDataToPilot(device.getWorkspaceId(), from,
                new DeviceOsdHost()
                        .setLatitude(data.getLatitude())
                        .setLongitude(data.getLongitude())
                        .setElevation(data.getElevation())
                        .setHeight(data.getHeight())
                        .setAttitudeHead(data.getAttitudeHead())
                        .setElevation(data.getElevation())
                        .setHorizontalSpeed(data.getHorizontalSpeed())
                        .setVerticalSpeed(data.getVerticalSpeed()));
        deviceService.pushOsdDataToWeb(device.getWorkspaceId(), BizCodeEnum.DEVICE_OSD, from, data);
    }

    @Override
    public void dockFirmwareVersionUpdate(TopicStateRequest<DockFirmwareVersion> request, MessageHeaders headers) {
        // If the reported version is empty, it will not be processed to prevent misleading page.
        if (!StringUtils.hasText(request.getData().getFirmwareVersion())) {
            return;
        }

        DeviceDTO device = DeviceDTO.builder()
                .deviceSn(request.getFrom())
                .firmwareVersion(request.getData().getFirmwareVersion())
                .firmwareStatus(request.getData().getNeedCompatibleStatus() ?
                        DeviceFirmwareStatusEnum.UNKNOWN : DeviceFirmwareStatusEnum.CONSISTENT_UPGRADE)
                .build();
        boolean isUpd = deviceService.updateDevice(device);
        if (!isUpd) {
            log.error("Data update of firmware version failed. SN: {}", request.getFrom());
        }
    }

    @Override
    public void rcAndDroneFirmwareVersionUpdate(TopicStateRequest<FirmwareVersion> request, MessageHeaders headers) {
        // If the reported version is empty, it will not be processed to prevent misleading page.
        if (!StringUtils.hasText(request.getData().getFirmwareVersion())) {
            return;
        }

        DeviceDTO device = DeviceDTO.builder()
                .deviceSn(request.getFrom())
                .firmwareVersion(request.getData().getFirmwareVersion())
                .build();
        boolean isUpd = deviceService.updateDevice(device);
        if (!isUpd) {
            log.error("Data update of firmware version failed. SN: {}", request.getFrom());
        }
    }

    @Override
    public void rcPayloadFirmwareVersionUpdate(TopicStateRequest<PayloadFirmwareVersion> request, MessageHeaders headers) {
        // If the reported version is empty, it will not be processed to prevent misleading page.
        if (!StringUtils.hasText(request.getData().getFirmwareVersion())) {
            return;
        }

        boolean isUpd = devicePayloadService.updateFirmwareVersion(request.getFrom(), request.getData());
        if (!isUpd) {
            log.error("Data update of payload firmware version failed. SN: {}", request.getFrom());
        }
    }

    @Override
    public void dockControlSourceUpdate(TopicStateRequest<DockDroneControlSource> request, MessageHeaders headers) {
        // If the control source is empty, it will not be processed.
        if (ControlSourceEnum.UNKNOWN == request.getData().getControlSource()) {
            return;
        }
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(request.getFrom());
        if (deviceOpt.isEmpty()) {
            return;
        }
        Optional<DeviceDTO> dockOpt = deviceRedisService.getDeviceOnline(request.getGateway());
        if (dockOpt.isEmpty()) {
            return;
        }

        deviceService.updateFlightControl(dockOpt.get(), request.getData().getControlSource());
        devicePayloadService.updatePayloadControl(deviceOpt.get(),
                request.getData().getPayloads().stream()
                        .map(p -> DevicePayloadReceiver.builder()
                                .controlSource(p.getControlSource())
                                .payloadIndex(p.getPayloadIndex())
                                .sn(p.getSn())
                                .deviceSn(request.getFrom())
                                .build()).collect(Collectors.toList()));
    }

    @Override
    public void rcControlSourceUpdate(TopicStateRequest<RcDroneControlSource> request, MessageHeaders headers) {
        // If the control source is empty, it will not be processed.
        if (ControlSourceEnum.UNKNOWN == request.getData().getControlSource()) {
            return;
        }
        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(request.getFrom());
        if (deviceOpt.isEmpty()) {
            return;
        }
        Optional<DeviceDTO> dockOpt = deviceRedisService.getDeviceOnline(request.getGateway());
        if (dockOpt.isEmpty()) {
            return;
        }

        deviceService.updateFlightControl(dockOpt.get(), request.getData().getControlSource());
        devicePayloadService.updatePayloadControl(deviceOpt.get(),
                request.getData().getPayloads().stream()
                        .map(p -> DevicePayloadReceiver.builder()
                                .controlSource(p.getControlSource())
                                .payloadIndex(p.getPayloadIndex())
                                .sn(p.getSn())
                                .deviceSn(request.getFrom())
                                .build()).collect(Collectors.toList()));
    }

    private void dockGoOnline(DeviceDTO gateway, DeviceDTO subDevice) {
        if (DeviceDomainEnum.DOCK != gateway.getDomain()) {
            return;
        }
        if (!StringUtils.hasText(gateway.getWorkspaceId())) {
            log.error("The dock is not bound, please bind it first and then go online.");
            return;
        }
        if (!Objects.requireNonNullElse(subDevice.getBoundStatus(), false)) {
            // Directly bind the drone of the dock to the same workspace as the dock.
            deviceService.bindDevice(DeviceDTO.builder().deviceSn(subDevice.getDeviceSn()).workspaceId(gateway.getWorkspaceId()).build());
            subDevice.setWorkspaceId(gateway.getWorkspaceId());
        }
        deviceRedisService.setDeviceOnline(subDevice);
    }

    private void changeSubDeviceParent(String deviceSn, String gatewaySn) {
        List<DeviceDTO> gatewaysList = deviceService.getDevicesByParams(
                DeviceQueryParam.builder()
                        .childSn(deviceSn)
                        .build());
        gatewaysList.stream()
                .filter(gateway -> !gateway.getDeviceSn().equals(gatewaySn))
                .forEach(gateway -> {
                    gateway.setChildDeviceSn("");
                    deviceService.updateDevice(gateway);
                    deviceRedisService.getDeviceOnline(gateway.getDeviceSn())
                            .ifPresent(device -> {
                                device.setChildDeviceSn(null);
                                deviceRedisService.setDeviceOnline(device);
                            });
                });
    }


    public void deviceOnlineAgain(String workspaceId, String gatewaySn, String deviceSn) {
        DeviceDTO device = DeviceDTO.builder().loginTime(LocalDateTime.now()).deviceSn(deviceSn).build();
        DeviceDTO gateway = DeviceDTO.builder()
                .loginTime(LocalDateTime.now())
                .deviceSn(gatewaySn)
                .childDeviceSn(deviceSn).build();
        deviceService.updateDevice(gateway);
        deviceService.updateDevice(device);
        gateway = deviceRedisService.getDeviceOnline(gatewaySn).map(g -> {
            g.setChildDeviceSn(deviceSn);
            return g;
        }).get();
        device = deviceRedisService.getDeviceOnline(deviceSn).map(d -> {
            d.setParentSn(gatewaySn);
            return d;
        }).get();
        deviceRedisService.setDeviceOnline(gateway);
        deviceRedisService.setDeviceOnline(device);
        if (StringUtils.hasText(workspaceId)) {
            deviceService.subDeviceOnlineSubscribeTopic(SDKManager.getDeviceSDK(gatewaySn));
        }

        log.warn("{} is already online.", deviceSn);
    }

    /**
     * Convert the received gateway device object into a database entity object.
     * @param gateway
     * @return
     */
    private DeviceDTO deviceGatewayConvertToDevice(String gatewaySn, UpdateTopo gateway) {
        if (null == gateway) {
            throw new IllegalArgumentException();
        }
        return DeviceDTO.builder()
                .deviceSn(gatewaySn)
                .subType(gateway.getSubType())
                .type(gateway.getType())
                .thingVersion(gateway.getThingVersion())
                .domain(gateway.getDomain())
                .controlSource(gateway.getSubDevices().isEmpty() ? null :
                        ControlSourceEnum.find(gateway.getSubDevices().get(0).getIndex().getControlSource()))
                .build();
    }

    /**
     * Convert the received drone device object into a database entity object.
     * @param device
     * @return
     */
    private DeviceDTO subDeviceConvertToDevice(UpdateTopoSubDevice device) {
        if (null == device) {
            throw new IllegalArgumentException();
        }
        return DeviceDTO.builder()
                .deviceSn(device.getSn())
                .type(device.getType())
                .subType(device.getSubType())
                .thingVersion(device.getThingVersion())
                .domain(device.getDomain())
                .build();
    }

    private Optional<DeviceDTO> onlineSaveDevice(DeviceDTO device, String childSn, String parentSn) {

        device.setChildDeviceSn(childSn);
        device.setLoginTime(LocalDateTime.now());

        Optional<DeviceDTO> deviceOpt = deviceService.getDeviceBySn(device.getDeviceSn());

        if (deviceOpt.isEmpty()) {
            device.setIconUrl(new DeviceIconUrl());
            // Set the icon of the gateway device displayed in the pilot's map, required in the TSA module.
            device.getIconUrl().setNormalIconUrl(IconUrlEnum.NORMAL_PERSON.getUrl());
            // Set the icon of the gateway device displayed in the pilot's map when it is selected, required in the TSA module.
            device.getIconUrl().setSelectIconUrl(IconUrlEnum.SELECT_PERSON.getUrl());
            device.setBoundStatus(false);

            // Query the model information of this gateway device.
            dictionaryService.getOneDictionaryInfoByTypeSubType(
                    device.getDomain().getDomain(), device.getType().getType(), device.getSubType().getSubType())
                    .ifPresent(entity -> {
                        device.setDeviceName(entity.getDeviceName());
                        device.setNickname(entity.getDeviceName());
                        device.setDeviceDesc(entity.getDeviceDesc());
                    });
        }
        boolean success = deviceService.saveOrUpdateDevice(device);
        if (!success) {
            return Optional.empty();
        }

        deviceOpt = deviceService.getDeviceBySn(device.getDeviceSn());
        DeviceDTO redisDevice = deviceOpt.get();
        redisDevice.setStatus(true);
        redisDevice.setParentSn(parentSn);

        deviceRedisService.setDeviceOnline(redisDevice);
        return deviceOpt;
    }

    private void fillDockOsd(String dockSn, OsdDock dock) {
        Optional<OsdDock> oldDockOpt = deviceRedisService.getDeviceOsd(dockSn, OsdDock.class);
        if (Objects.nonNull(dock.getJobNumber())) {
            return;
        }
        if (oldDockOpt.isEmpty()) {
            deviceRedisService.setDeviceOsd(dockSn, dock);
            return;
        }
        OsdDock oldDock = oldDockOpt.get();
        if (Objects.nonNull(dock.getModeCode())) {
            dock.setDrcState(oldDock.getDrcState());
            deviceRedisService.setDeviceOsd(dockSn, dock);
            return;
        }
        if (Objects.nonNull(dock.getDrcState()) ) {
            oldDock.setDrcState(dock.getDrcState());
            deviceRedisService.setDeviceOsd(dockSn, oldDock);
        }
    }

    @Override
    public void dockThermalSupportedPaletteStyle(TopicStateRequest<DockDroneThermalSupportedPaletteStyle> request, MessageHeaders headers) {
        // TODO
        log.info("TODO dockThermalSupportedPaletteStyle:{}",request.toString());
    }

    @Override
    public TopicStateResponse<MqttReply> dockDroneCommanderFlightHeight(TopicStateRequest<DockDroneCommanderFlightHeight> request, MessageHeaders headers) {
        // TODO
        log.info("TODO dockDroneCommanderFlightHeight:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public TopicStateResponse<MqttReply> dockDroneRthMode(TopicStateRequest<DockDroneRthMode> request, MessageHeaders headers) {
        // TODO
        log.info("TODO dockDroneRthMode:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public TopicStateResponse<MqttReply> dockDroneCurrentCommanderFlightMode(TopicStateRequest<DockDroneCurrentCommanderFlightMode> request, MessageHeaders headers) {
        // TODO
        log.info("TODO dockDroneCurrentCommanderFlightMode:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public TopicStateResponse<MqttReply> dockDroneCurrentRthMode(TopicStateRequest<DockDroneCurrentRthMode> request, MessageHeaders headers) {
        // TODO
        log.info("TODO dockDroneCurrentRthMode:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public TopicStateResponse<MqttReply> dockDroneModeCodeReason(TopicStateRequest<DockDroneModeCodeReason> request, MessageHeaders headers) {
        // TODO
        log.info("TODO dockDroneModeCodeReason:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public void dockWpmzVersionUpdate(TopicStateRequest<DockDroneWpmzVersion> request, MessageHeaders headers) {
        // TODO
        log.info("TODO dockWpmzVersionUpdate:{}",request.toString());
    }

    @Override
    public TopicStateResponse<MqttReply> dockDroneCommanderModeLostAction(TopicStateRequest<DockDroneCommanderModeLostAction> request, MessageHeaders headers) {
        // TODO
        log.info("TODO dockDroneCommanderModeLostAction:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public void dockLiveStatusUpdate(TopicStateRequest<DockLiveStatus> request, MessageHeaders headers){
        // TODO save live_status
        log.info("TODO dockLiveStatusUpdate:{}",request.toString());
    }

    @Override
    public void rcLiveStatusUpdate(TopicStateRequest<RcLiveStatus> request, MessageHeaders headers) {
        // TODO save rc_live_status
        log.info("TODO rcLiveStatusUpdate:{}",request.toString());
    }

    @Override
    public TopicStateResponse<MqttReply> dongleInfos(TopicStateRequest<DongleInfos> request, MessageHeaders headers) {
        // TODO save dongle_infos
        log.info("TODO dongleInfos:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public void droneRemainingPowerForReturnHomeUpdate(TopicStateRequest<DroneRemainingPowerForReturnHome> request, MessageHeaders headers) {
        DeviceStateDto droneState = deviceRedisService.getDroneState(request.getFrom());
        droneState.setRemainingPowerForReturnHome(request.getData().getRemainingPowerForReturnHome());
        deviceRedisService.setDroneState(request.getFrom(), droneState);
    }

    @Override
    public void droneAirTransferEnableUpdate(TopicStateRequest<AirTransferSwitch> request, MessageHeaders headers) {
        DeviceStateDto droneState = deviceRedisService.getDroneState(request.getFrom());
        droneState.setAirTransferEnabled(request.getData().getAirTransferEnable());
        deviceRedisService.setDroneState(request.getFrom(), droneState);
    }

    @Override
    public void droneCameraWatermarkSettingsUpdate(TopicStateRequest<CameraWatermarkSetting> request, MessageHeaders headers) {
        DeviceStateDto droneState = deviceRedisService.getDroneState(request.getFrom());
        droneState.setCameraWatermarkSettings(request.getData().getCameraWatermarkSettings());
        deviceRedisService.setDroneState(request.getFrom(), droneState);
    }

    @Override
    public TopicStateResponse<MqttReply> rcCloudControlAuth(TopicStateRequest<CloudControlAuth> request, MessageHeaders headers) {
        log.info("TODO rcCloudControlAuth:{}",request.toString());
        return new TopicStateResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public void rcCameraWatermarkSettingsUpdate(TopicStateRequest<CameraWatermarkSetting> request, MessageHeaders headers) {
        DeviceStateDto droneState = deviceRedisService.getDroneState(request.getFrom());
        droneState.setCameraWatermarkSettings(request.getData().getCameraWatermarkSettings());
        deviceRedisService.setDroneState(request.getFrom(), droneState);
    }
}
