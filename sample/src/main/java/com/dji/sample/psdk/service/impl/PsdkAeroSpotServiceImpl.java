package com.dji.sample.psdk.service.impl;

import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.psdk.model.enums.AeroSpotValueEnum;
import com.dji.sample.psdk.service.IPsdkAeroSpotService;
import com.dji.sample.psdk.service.IPsdkService;
import com.dji.sdk.common.HttpResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
public class PsdkAeroSpotServiceImpl implements IPsdkAeroSpotService {

    @Autowired
    private IPsdkService psdkService;

    @Override
    public String getRedisASFKey(String flightId) {
        return RedisConst.PSDK_AERO_SPOT_FILE + flightId;
    }

    @Override
    public String getRedisASSLKey(String droneSn) {
        return RedisConst.PSDK_AERO_SPOT_SN_LINK + droneSn;
    }

    @Override
    public String getRedisASTUKey(String aeroSpotSn) {
        return RedisConst.PSDK_AERO_SPOT_TO_UPLOAD + aeroSpotSn;
    }

    @Override
    public HttpResultResponse psdkWidgetValueSetAeroSpot(String dockSn, AeroSpotValueEnum value) {
        return psdkService.sendPsdkWidgetValueSet(dockSn, 2, value.getValue(), 1, "AeroSpot");
    }
}
