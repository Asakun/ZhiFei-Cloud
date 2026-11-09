package com.dji.sample.oidc.controller;

import com.dji.sample.oidc.model.dto.OidcProviderDTO;
import com.dji.sample.oidc.model.dto.OidcProviderParam;
import com.dji.sample.oidc.service.IOidcProviderService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.PaginationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Optional;

/**
 * OIDC provider configuration management API.
 *
 * <p>Restricted to administrators. Responses never contain the client secret.
 *
 * @author dyw
 */
@Slf4j
@RestController
@RequestMapping("${url.manage.prefix}${url.manage.version}/oidc/providers")
@ConditionalOnProperty(prefix = "oidc", name = "enabled", havingValue = "true")
public class OidcProviderController {

    @Autowired
    private IOidcProviderService oidcProviderService;

    /**
     * Paginated list of providers.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/list")
    public HttpResultResponse<PaginationData<OidcProviderDTO>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(name = "page_size", defaultValue = "10") Long pageSize) {
        return HttpResultResponse.success(oidcProviderService.getPagination(keyword, page, pageSize));
    }

    /**
     * Get a provider by id.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/{provider_id}")
    public HttpResultResponse<OidcProviderDTO> getDetail(@PathVariable(name = "provider_id") String providerId) {
        Optional<OidcProviderDTO> opt = oidcProviderService.getByProviderId(providerId);
        return opt.isEmpty() ? HttpResultResponse.error("not found.") : HttpResultResponse.success(opt.get());
    }

    /**
     * Create a provider.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/add")
    public HttpResultResponse add(@Valid @RequestBody OidcProviderParam param) {
        return oidcProviderService.save(param);
    }

    /**
     * Update a provider. An empty clientSecret keeps the existing secret.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/update")
    public HttpResultResponse update(@Valid @RequestBody OidcProviderParam param) {
        return oidcProviderService.update(param);
    }

    /**
     * Enable or disable a provider.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/enable")
    public HttpResultResponse enable(@RequestParam String providerId,
                                     @RequestParam Boolean enable) {
        return oidcProviderService.enable(providerId, enable);
    }

    /**
     * Soft-delete a provider.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/del")
    public HttpResultResponse delete(@RequestParam String providerId) {
        return oidcProviderService.delete(providerId);
    }
}
