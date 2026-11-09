package com.dji.sample.control.service.impl;

import com.dji.sample.component.websocket.model.BizCodeEnum;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.control.model.dto.FlyToPointProgressDTO;
import com.dji.sample.control.model.dto.ResultNotifyDTO;
import com.dji.sample.control.model.dto.TakeoffToPointProgressDTO;
import com.dji.sample.control.service.IControlRedisService;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.model.enums.UserTypeEnum;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.manage.service.IOsdService;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sample.wayline.model.dto.WaylineJobDTO;
import com.dji.sample.wayline.model.enums.WaylineJobStatusEnum;
import com.dji.sample.wayline.service.IWaylineJobService;
import com.dji.sample.wayline.service.IWaylineRedisService;
import com.dji.sdk.cloudapi.control.*;
import com.dji.sdk.cloudapi.control.api.AbstractControlService;
import com.dji.sdk.cloudapi.wayline.WaylineErrorCodeEnum;
import com.dji.sdk.mqtt.MqttReply;
import com.dji.sdk.mqtt.drc.TopicDrcRequest;
import com.dji.sdk.mqtt.events.EventsDataRequest;
import com.dji.sdk.mqtt.events.TopicEventsRequest;
import com.dji.sdk.mqtt.events.TopicEventsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author sean
 * @version 1.7
 * @date 2023/7/4
 */
@Service
@Slf4j
public class SDKControlService extends AbstractControlService {

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private IWaylineJobService waylineJobService;

    @Autowired
    private IWaylineRedisService waylineRedisService;

    @Autowired
    private IOsdService osdService;

    @Autowired
    private IControlRedisService controlRedisService;

    @Override
    public TopicEventsResponse<MqttReply> flyToPointProgress(TopicEventsRequest<FlyToPointProgress> request, MessageHeaders headers) {
        String dockSn  = request.getGateway();

        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (deviceOpt.isEmpty()) {
            log.error("The dock is offline.");
            return null;
        }

        FlyToPointProgress eventsReceiver = request.getData();
        FlyToPointProgressDTO receiver = new FlyToPointProgressDTO();
        BeanUtils.copyProperties(eventsReceiver, receiver);
        receiver.setDockSn(dockSn);
        webSocketMessageService.sendBatch(deviceOpt.get().getWorkspaceId(), UserTypeEnum.WEB.getVal(),
                BizCodeEnum.FLY_TO_POINT_PROGRESS.getCode(), receiver);
        return new TopicEventsResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public TopicEventsResponse<MqttReply> takeoffToPointProgress(TopicEventsRequest<TakeoffToPointProgress> request, MessageHeaders headers) {
        String dockSn  = request.getGateway();

        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (deviceOpt.isEmpty()) {
            log.error("The dock is offline.");
            return null;
        }

        TakeoffToPointProgress eventsReceiver = request.getData();

        if (eventsReceiver.getStatus()==TakeoffStatusEnum.TASK_FINISH){
            WaylineJobDTO job = WaylineJobDTO.builder()
                    .jobId(eventsReceiver.getFlightId())
                    .status(WaylineJobStatusEnum.SUCCESS.getVal())
                    .completedTime(LocalDateTime.now())
                    .build();

            Optional<DuringFlightInfo> duringFlightInfo = waylineRedisService.getDuringFlightInfo(dockSn);
            duringFlightInfo.ifPresent(flightInfo -> job.setMediaCount(flightInfo.getMediaCount()));

            if (WaylineErrorCodeEnum.SUCCESS != eventsReceiver.getResult()) {
                job.setCode(eventsReceiver.getResult().getCode());
                job.setStatus(WaylineJobStatusEnum.FAILED.getVal());
            }
            osdService.getFlightDistanceTime(job.getJobId(), job);
            waylineJobService.updateJob(job);
            Optional<DuringFlightInfo> flightInfoOpt = waylineRedisService.getDuringFlightInfo(dockSn);
            flightInfoOpt.ifPresent(flightInfo -> waylineJobService.flightFinshToDo(dockSn, flightInfo));
            waylineRedisService.delDuringFlightInfo(dockSn);
            controlRedisService.clearControlAuthority(dockSn);
        }

        TakeoffToPointProgressDTO receiver = new TakeoffToPointProgressDTO();
        BeanUtils.copyProperties(eventsReceiver, receiver);
        receiver.setDockSn(dockSn);
        webSocketMessageService.sendBatch(deviceOpt.get().getWorkspaceId(), UserTypeEnum.WEB.getVal(),
                BizCodeEnum.TAKE_OFF_TO_POINT_PROGRESS.getCode(), receiver);

        return new TopicEventsResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public TopicEventsResponse<MqttReply> drcStatusNotify(TopicEventsRequest<DrcStatusNotify> request, MessageHeaders headers) {
        String dockSn  = request.getGateway();

        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (deviceOpt.isEmpty()) {
            return null;
        }

        DrcStatusNotify eventsReceiver = request.getData();
        if (DrcStatusErrorEnum.SUCCESS != eventsReceiver.getResult()) {
            webSocketMessageService.sendBatch(
                    deviceOpt.get().getWorkspaceId(), UserTypeEnum.WEB.getVal(), BizCodeEnum.DRC_STATUS_NOTIFY.getCode(),
                    ResultNotifyDTO.builder().sn(dockSn)
                            .message(eventsReceiver.getResult().getMessage())
                            .result(eventsReceiver.getResult().getCode()).build());
        }
        return new TopicEventsResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public TopicEventsResponse<MqttReply> joystickInvalidNotify(TopicEventsRequest<JoystickInvalidNotify> request, MessageHeaders headers) {
        String dockSn  = request.getGateway();

        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (deviceOpt.isEmpty()) {
            return null;
        }

        JoystickInvalidNotify eventsReceiver = request.getData();
        webSocketMessageService.sendBatch(
                deviceOpt.get().getWorkspaceId(), UserTypeEnum.WEB.getVal(), BizCodeEnum.JOYSTICK_INVALID_NOTIFY.getCode(),
                ResultNotifyDTO.builder().sn(dockSn)
                        .message(eventsReceiver.getReason().getMessage())
                        .result(eventsReceiver.getReason().getVal()).build());
        return new TopicEventsResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public TopicEventsResponse<MqttReply> cloudControlAuthNotify(TopicEventsRequest<EventsDataRequest<CloudControlAuthNotify>> request, MessageHeaders headers) {
        String dockSn  = request.getGateway();

        Optional<DeviceDTO> deviceOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (deviceOpt.isEmpty()) {
            return null;
        }

        EventsDataRequest<CloudControlAuthNotify> eventsReceiver = request.getData();
        webSocketMessageService.sendBatch(
                deviceOpt.get().getWorkspaceId(), UserTypeEnum.WEB.getVal(), BizCodeEnum.CLOUD_CONTROL_AUTH_NOTIFY.getCode(),
                ResultNotifyDTO.builder().sn(dockSn)
                        .result(eventsReceiver.getResult().getCode())
                        .message(eventsReceiver.getOutput().getStatus()).build());
        return new TopicEventsResponse<MqttReply>().setData(MqttReply.success());
    }

    @Override
    public void heartBeatUp(TopicDrcRequest<HeartBeatRequest> request, MessageHeaders headers) {
        log.info("TODO heartBeatUp:{}",request.toString());
    }

    @Override
    public void osdInfoPush(TopicDrcRequest<OsdInfoPush> request, MessageHeaders headers) {
        log.info("TODO osdInfoPush:{}",request.toString());
    }

    @Override
    public void delayInfoPush(TopicDrcRequest<DelayInfoPush> request, MessageHeaders headers) {
        log.info("TODO delayInfoPush:{}",request.toString());
    }

    @Override
    public void hsiInfoPush(TopicDrcRequest<HsiInfoPush> request, MessageHeaders headers) {
        log.info("TODO hsiInfoPush:{}",request.toString());
    }
}
