package com.dji.sample.wayline.service.impl;

import com.dji.sample.wayline.service.ICvrmpCameraService;
import com.dji.sample.proxy.service.ICvrmpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(ICvrmpService.class)
@Slf4j
public class CvrmpCameraServiceImpl implements ICvrmpCameraService {

    @Override
    public void stopCameraAsync(String cameraId) {
        log.debug("ICvrmpCameraService not implemented");
    }

}
