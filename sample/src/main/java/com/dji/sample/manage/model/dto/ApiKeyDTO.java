package com.dji.sample.manage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyDTO {

    @NotBlank
    private String app;

    private String apiKey;

    private String uid;

    private Long expireTime;

    private Boolean enable;

    private Long createTime;

    private Long updateTime;

    private String username;

    private Integer userType;

    private String workspaceId;
}
