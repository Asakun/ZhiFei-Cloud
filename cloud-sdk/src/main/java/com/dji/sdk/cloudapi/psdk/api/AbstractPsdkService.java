package com.dji.sdk.cloudapi.psdk.api;

import com.dji.sdk.annotations.CloudSDKVersion;
import com.dji.sdk.cloudapi.psdk.PsdkFloatingWindowText;
import com.dji.sdk.cloudapi.psdk.PsdkMethodEnum;
import com.dji.sdk.cloudapi.psdk.PsdkWidgetValueSetRequest;
import com.dji.sdk.config.version.CloudSDKVersionEnum;
import com.dji.sdk.config.version.GatewayManager;
import com.dji.sdk.config.version.GatewayTypeEnum;
import com.dji.sdk.mqtt.ChannelName;
import com.dji.sdk.mqtt.MqttReply;
import com.dji.sdk.mqtt.events.TopicEventsRequest;
import com.dji.sdk.mqtt.events.TopicEventsResponse;
import com.dji.sdk.mqtt.services.ServicesPublish;
import com.dji.sdk.mqtt.services.ServicesReplyData;
import com.dji.sdk.mqtt.services.TopicServicesResponse;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageHeaders;

import javax.annotation.Resource;


public abstract class AbstractPsdkService {

    @Resource
    private ServicesPublish servicesPublish;

    @ServiceActivator(inputChannel = ChannelName.INBOUND_EVENTS_PSDK_FLOATING_WINDOW_TEXT, outputChannel = ChannelName.OUTBOUND_EVENTS)
    @CloudSDKVersion(since = CloudSDKVersionEnum.V1_0_3)
    public TopicEventsResponse<MqttReply> psdkFloatingWindowText(TopicEventsRequest<PsdkFloatingWindowText> request, MessageHeaders headers) {
        throw new UnsupportedOperationException("psdkFloatingWindowText not implemented");
    }

    @CloudSDKVersion(since = CloudSDKVersionEnum.V1_0_3, exclude = GatewayTypeEnum.RC)
    public TopicServicesResponse<ServicesReplyData> psdkWidgetValueSet(GatewayManager gateway, PsdkWidgetValueSetRequest request) {
        return servicesPublish.publish(
                gateway.getGatewaySn(),
                PsdkMethodEnum.PSDK_WIDGET_VALUE_SET.getMethod(),
                request);
    }
}
