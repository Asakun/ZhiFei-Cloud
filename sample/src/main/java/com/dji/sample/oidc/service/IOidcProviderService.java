package com.dji.sample.oidc.service;

import com.dji.sample.oidc.model.OidcProviderEntity;
import com.dji.sample.oidc.model.dto.OidcProviderDTO;
import com.dji.sample.oidc.model.dto.OidcProviderParam;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.PaginationData;

import java.util.Optional;

/**
 * OIDC provider configuration service.
 *
 * @author dyw
 */
public interface IOidcProviderService {

    /**
     * Get an enabled provider by id, used by the login flow.
     *
     * @param providerId the provider id (as it appears in the login URL path)
     * @return the provider configuration, or empty when it does not exist or is disabled
     */
    Optional<OidcProviderEntity> getEnabledProvider(String providerId);

    /**
     * Paginated list of providers for the management API.
     */
    PaginationData<OidcProviderDTO> getPagination(String keyword, long page, long pageSize);

    /**
     * Get a provider (including disabled ones) for the management API.
     */
    Optional<OidcProviderDTO> getByProviderId(String providerId);

    /**
     * Create a provider.
     */
    HttpResultResponse save(OidcProviderParam param);

    /**
     * Update a provider. An empty {@code clientSecret} keeps the existing secret.
     */
    HttpResultResponse update(OidcProviderParam param);

    /**
     * Enable or disable a provider.
     */
    HttpResultResponse enable(String providerId, Boolean enable);

    /**
     * Soft-delete a provider.
     */
    HttpResultResponse delete(String providerId);
}
