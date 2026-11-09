package com.dji.sample.wayline.service;

import org.springframework.scheduling.annotation.Async;

public interface ICvrmpCameraService {
    @Async
    void stopCameraAsync(String cameraId);
}
