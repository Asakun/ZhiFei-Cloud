package com.dji.sample.media.service;

import org.springframework.scheduling.annotation.Async;

import java.util.List;

public interface ICvrmpEventService {
    @Async
    void sendLlmImgEvent(List<String> urls, String jobId, String rule, String num);
}
