package com.dji.sample.manage.controller;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.manage.model.dto.ApiKeyDTO;
import com.dji.sample.manage.service.IApiKeyService;
import com.dji.sdk.common.HttpResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

import static com.dji.sample.component.AuthInterceptor.TOKEN_CLAIM;

@RestController
@Slf4j
@RequestMapping("${url.manage.prefix}${url.manage.version}/apikey")
public class ApiKeyController {

    @Autowired
    private IApiKeyService apiKeyService;

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/all-list")
    public HttpResultResponse<List<ApiKeyDTO>> getAllApiKeys(HttpServletRequest request) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        List<ApiKeyDTO> apiKeyDTOS = apiKeyService.getApiKeysByUid(customClaim.getId());
        return HttpResultResponse.success(apiKeyDTOS);
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/add")
    public HttpResultResponse addApiKey(HttpServletRequest request, @Valid @RequestBody ApiKeyDTO apiKeyDTO) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return apiKeyService.addApiKey(customClaim, apiKeyDTO.getApp(), apiKeyDTO.getExpireTime());
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/del")
    public HttpResultResponse delApiKey(HttpServletRequest request, @RequestParam String app) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return apiKeyService.deleteApiKey(customClaim, app);
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/enable")
    public HttpResultResponse enableApiKey(HttpServletRequest request,@Valid @RequestBody ApiKeyDTO apiKeyDTO) {
        if (apiKeyDTO.getEnable()==null) {
            return HttpResultResponse.error("enable must not be null");
        }
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return apiKeyService.enableApiKey(customClaim, apiKeyDTO.getEnable(), apiKeyDTO.getApp());
    }
}
