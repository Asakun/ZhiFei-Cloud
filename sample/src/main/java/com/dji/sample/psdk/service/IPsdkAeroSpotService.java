package com.dji.sample.psdk.service;

import com.dji.sample.psdk.model.enums.AeroSpotValueEnum;
import com.dji.sdk.common.HttpResultResponse;

public interface IPsdkAeroSpotService {
    String getRedisASFKey(String flightId);

    String getRedisASSLKey(String droneSn);

    String getRedisASTUKey(String aeroSpotSn);

    HttpResultResponse psdkWidgetValueSetAeroSpot(String dockSn, AeroSpotValueEnum value);
}
