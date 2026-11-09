package com.dji.sample.proxy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmImgDetectRequest {
    @NotNull
    private String modelName;

    @NotNull
    private String skillId;

    @NotNull
    private Map<String, String> promptDict;

    /**
     * 图片 base64 字符串，支持多张图片，使用 "|" 分隔。
     */
    @NotNull
    private String imgBs64;

    private String jobId;

    private String dt;

    private Double lat;

    private Double lon;

    private String deviceSn;

    private String cid;
}
