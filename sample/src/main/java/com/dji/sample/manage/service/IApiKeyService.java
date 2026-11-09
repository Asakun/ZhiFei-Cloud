package com.dji.sample.manage.service;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.manage.model.dto.ApiKeyDTO;
import com.dji.sdk.common.HttpResultResponse;

import java.util.List;

public interface IApiKeyService {
    List<ApiKeyDTO> getApiKeysByUid(String uid);

    HttpResultResponse addApiKey(CustomClaim customClaim, String app, Long expireTime);

    HttpResultResponse deleteApiKey(CustomClaim customClaim, String app);

    HttpResultResponse enableApiKey(CustomClaim customClaim, Boolean enable, String app);

    Integer syncApiKey();
}
