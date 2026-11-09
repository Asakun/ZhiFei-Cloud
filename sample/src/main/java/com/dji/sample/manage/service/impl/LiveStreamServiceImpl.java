package com.dji.sample.manage.service.impl;

import com.dji.sample.component.RestTemplateUtil;
import com.dji.sample.manage.model.dto.*;
import com.dji.sample.manage.model.param.DeviceQueryParam;
import com.dji.sample.manage.service.*;
import com.dji.sdk.cloudapi.device.DeviceDomainEnum;
import com.dji.sdk.cloudapi.device.VideoId;
import com.dji.sdk.cloudapi.livestream.*;
import com.dji.sdk.cloudapi.livestream.api.AbstractLivestreamService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.SDKManager;
import com.dji.sdk.mqtt.services.LiveStartPushReply;
import com.dji.sdk.mqtt.services.ServicesReplyData;
import com.dji.sdk.mqtt.services.TopicServicesResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author sean.zhou
 * @date 2021/11/22
 * @version 0.1
 */
@Service
@Transactional
public class LiveStreamServiceImpl implements ILiveStreamService {

    private static final Logger log = LoggerFactory.getLogger(LiveStreamServiceImpl.class);

    @Autowired
    private ICapacityCameraService capacityCameraService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IWorkspaceService workspaceService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private AbstractLivestreamService abstractLivestreamService;

    @Autowired
    private RestTemplateUtil restTemplateUtil;

    @Override
    public List<CapacityDeviceDTO> getLiveCapacity(String workspaceId) {

        // Query all devices in this workspace.
        List<DeviceDTO> devicesList = deviceService.getDevicesByParams(
                DeviceQueryParam.builder()
                        .workspaceId(workspaceId)
                        .domains(List.of(DeviceDomainEnum.DRONE.getDomain(), DeviceDomainEnum.DOCK.getDomain()))
                        .build());

        // Query the live capability of each drone.
        return devicesList.stream()
                .filter(device -> deviceRedisService.checkDeviceOnline(device.getDeviceSn()))
                .map(device -> CapacityDeviceDTO.builder()
                        .name(Objects.requireNonNullElse(device.getNickname(), device.getDeviceName()))
                        .sn(device.getDeviceSn())
                        .camerasList(capacityCameraService.getCapacityCameraByDeviceSn(device.getDeviceSn()))
                        .build())
                .collect(Collectors.toList());
    }

    private HttpResultResponse startGB28181(ILivestreamUrl url, LiveDTO live) {
        LivestreamGb28181Url gb28181 = (LivestreamGb28181Url) url;
        if (gb28181.getApiKey() != null && gb28181.getApiPort() != null) {
            String wvpUrl = String.format("http://%s:%d/api/play/start/%s/%s",gb28181.getServerIP(),gb28181.getApiPort(),gb28181.getAgentID(),gb28181.getChannel());
            try {
                String result = restTemplateUtil.getWithApiKey(wvpUrl, gb28181.getApiKey(), String.class);
                log.info("wvp play start {}",result);
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(result);
                int code = jsonNode.get("code").asInt();
                if (code != 0) {
                    String msg = jsonNode.get("msg").asText();
                    return HttpResultResponse.error(msg);
                }
                String rtc = jsonNode.get("data").get("rtc").asText();
                live.setUrl(rtc.replace("http", "webrtc"));
            } catch (Exception e) {
                return HttpResultResponse.error(e.getMessage());
            }
        } else {
            live.setUrl(new StringBuilder()
                    .append("webrtc://")
                    .append(gb28181.getServerIP())
                    .append("/live/")
                    .append(gb28181.getAgentID())
                    .append("@")
                    .append(gb28181.getChannel())
                    .toString());
        }
        return null;
    }

    @Override
    public HttpResultResponse liveStart(LiveTypeDTO liveParam) {
        // Check if this lens is available live.
        HttpResultResponse<DeviceDTO> responseResult = this.checkBeforeLive(liveParam.getVideoId());
        if (HttpResultResponse.CODE_SUCCESS != responseResult.getCode()) {
            return responseResult;
        }

        ILivestreamUrl url = LiveStreamProperty.get(liveParam.getUrlType());
        url = setExt(liveParam.getUrlType(), url, liveParam.getVideoId());

        if (liveParam.getGatewayType() == GatewayTypeEnum.RC) {
            return rcLiveStart(liveParam, url, responseResult);
        } else {
            return liveStart(liveParam, url, responseResult);
        }
    }

    public HttpResultResponse liveStart(LiveTypeDTO liveParam, ILivestreamUrl url, HttpResultResponse<DeviceDTO> responseResult) {

        TopicServicesResponse<ServicesReplyData<String>> response = abstractLivestreamService.liveStartPush(
                SDKManager.getDeviceSDK(responseResult.getData().getDeviceSn()),
                new LiveStartPushRequest()
                        .setUrl(url)
                        .setUrlType(liveParam.getUrlType())
                        .setVideoId(liveParam.getVideoId())
                        .setVideoQuality(liveParam.getVideoQuality()));

        if (!response.getData().getResult().isSuccess()) {
            if (response.getData().getResult().getCode() != 513003) {
                return HttpResultResponse.error(response.getData().getResult());
            }
        }

        LiveDTO live = new LiveDTO();

        switch (liveParam.getUrlType()) {
            case AGORA:
                break;
            case RTMP:
                live.setUrl(url.toString().replace("rtmp", "webrtc"));
                break;
            case GB28181:
                HttpResultResponse resp = startGB28181(url, live);
                if (resp != null) {return resp;}
                break;
            case RTSP:
                live.setUrl(response.getData().getOutput());
                break;
            case WHIP:
                live.setUrl(url.toString().replace("whip", "whep"));
                break;
            default:
                return HttpResultResponse.error(LiveErrorCodeEnum.URL_TYPE_NOT_SUPPORTED);
        }

        if (response.getData().getResult().getCode() == 513003) {
            return HttpResultResponse.error(response.getData().getResult()).setData(live);
        }

        return HttpResultResponse.success(live);
    }

    public HttpResultResponse rcLiveStart(LiveTypeDTO liveParam, ILivestreamUrl url, HttpResultResponse<DeviceDTO> responseResult) {

        TopicServicesResponse<ServicesReplyData<LiveStartPushReply>> response = abstractLivestreamService.rcLiveStartPush(
                SDKManager.getDeviceSDK(responseResult.getData().getDeviceSn()),
                new LiveStartPushRequest()
                        .setUrl(url)
                        .setUrlType(liveParam.getUrlType())
                        .setVideoId(liveParam.getVideoId())
                        .setVideoQuality(liveParam.getVideoQuality()));

        if (!response.getData().getResult().isSuccess()) {
            if (response.getData().getResult().getCode() != 613003) {
                return HttpResultResponse.error(response.getData().getResult());
            }
        }

        LiveDTO live = new LiveDTO();

        switch (liveParam.getUrlType()) {
            case AGORA:
                break;
            case RTMP:
                live.setUrl(url.toString().replace("rtmp", "webrtc"));
                break;
            case GB28181:
                HttpResultResponse resp = startGB28181(url, live);
                if (resp != null) {return resp;}
                break;
            case WHIP:
                live.setUrl(url.toString().replace("whip", "whep"));
                break;
            default:
                return HttpResultResponse.error(LiveErrorCodeEnum.URL_TYPE_NOT_SUPPORTED);
        }

        if (response.getData().getResult().getCode() == 613003) {
            return HttpResultResponse.error(response.getData().getResult()).setData(live);
        }

        return HttpResultResponse.success(live);
    }

    @Override
    public HttpResultResponse liveStop(VideoId videoId) {
        HttpResultResponse<DeviceDTO> responseResult = this.checkBeforeLive(videoId);
        if (HttpResultResponse.CODE_SUCCESS != responseResult.getCode()) {
            return responseResult;
        }

        TopicServicesResponse<ServicesReplyData> response = abstractLivestreamService.liveStopPush(
                SDKManager.getDeviceSDK(responseResult.getData().getDeviceSn()), new LiveStopPushRequest()
                        .setVideoId(videoId));
        if (!response.getData().getResult().isSuccess()) {
            return HttpResultResponse.error(response.getData().getResult());
        }

        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse liveSetQuality(LiveTypeDTO liveParam) {
        HttpResultResponse<DeviceDTO> responseResult = this.checkBeforeLive(liveParam.getVideoId());
        if (responseResult.getCode() != 0) {
            return responseResult;
        }

        TopicServicesResponse<ServicesReplyData> response = abstractLivestreamService.liveSetQuality(
                SDKManager.getDeviceSDK(responseResult.getData().getDeviceSn()), new LiveSetQualityRequest()
                        .setVideoQuality(liveParam.getVideoQuality())
                        .setVideoId(liveParam.getVideoId()));
        if (!response.getData().getResult().isSuccess()) {
            return HttpResultResponse.error(response.getData().getResult());
        }

        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse liveLensChange(LiveTypeDTO liveParam) {
        HttpResultResponse<DeviceDTO> responseResult = this.checkBeforeLive(liveParam.getVideoId());
        if (HttpResultResponse.CODE_SUCCESS != responseResult.getCode()) {
            return responseResult;
        }

        TopicServicesResponse<ServicesReplyData> response = abstractLivestreamService.liveLensChange(
                SDKManager.getDeviceSDK(responseResult.getData().getDeviceSn()), new LiveLensChangeRequest()
                        .setVideoType(liveParam.getVideoType())
                        .setVideoId(liveParam.getVideoId()));

        if (!response.getData().getResult().isSuccess()) {
            return HttpResultResponse.error(response.getData().getResult());
        }

        return HttpResultResponse.success();
    }

    /**
     * Check if this lens is available live.
     * @param videoId
     * @return
     */
    private HttpResultResponse<DeviceDTO> checkBeforeLive(VideoId videoId) {
        if (Objects.isNull(videoId)) {
            return HttpResultResponse.error(LiveErrorCodeEnum.ERROR_PARAMETERS);
        }

        Optional<DeviceDTO> deviceOpt = deviceService.getDeviceBySn(videoId.getDroneSn());
        // Check if the gateway device connected to this drone exists
        if (deviceOpt.isEmpty()) {
            return HttpResultResponse.error(LiveErrorCodeEnum.NO_AIRCRAFT);
        }

        if (DeviceDomainEnum.DOCK == deviceOpt.get().getDomain()) {
            return HttpResultResponse.success(deviceOpt.get());
        }
        List<DeviceDTO> gatewayList = deviceService.getDevicesByParams(
                DeviceQueryParam.builder()
                        .childSn(videoId.getDroneSn())
                        .build());
        if (gatewayList.isEmpty()) {
            return HttpResultResponse.error(LiveErrorCodeEnum.NO_FLIGHT_CONTROL);
        }

        return HttpResultResponse.success(gatewayList.get(0));
    }

    /**
     * This is business-customized logic and is only used for testing.
     * @param type
     * @param url
     * @param videoId
     */
    private ILivestreamUrl setExt(UrlTypeEnum type, ILivestreamUrl url, VideoId videoId) {
        switch (type) {
            case AGORA:
                LivestreamAgoraUrl agoraUrl = (LivestreamAgoraUrl) url.clone();
                return agoraUrl.setSn(videoId.getDroneSn());
            case RTMP:
                LivestreamRtmpUrl rtmpUrl = (LivestreamRtmpUrl) url.clone();
                return rtmpUrl.setUrl(rtmpUrl.getUrl() + videoId.getDroneSn() + "-" + videoId.getPayloadIndex().toString());
            case GB28181:
                String random = String.valueOf(Math.abs(videoId.getDroneSn().hashCode()) % 1000);
                LivestreamGb28181Url gbUrl = (LivestreamGb28181Url) url.clone();
                gbUrl.setAgentID(gbUrl.getAgentID().substring(0, 20 - random.length()) + random);
                String deviceType = String.valueOf(videoId.getPayloadIndex().getType().getType());
                return gbUrl.setChannel(gbUrl.getChannel().substring(0, 20 - deviceType.length()) + deviceType);
            case WHIP:
                LivestreamWhipUrl whipUrl = (LivestreamWhipUrl) url.clone();
                return whipUrl.setUrl(whipUrl.getUrl() + videoId.getDroneSn() + "-" + videoId.getPayloadIndex().toString());
        }
        return url;
    }
}