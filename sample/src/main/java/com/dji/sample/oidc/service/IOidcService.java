package com.dji.sample.oidc.service;

import com.dji.sample.manage.model.dto.UserDTO;

import java.util.Optional;

/**
 * OIDC client (Relying Party) service.
 *
 * @author dyw
 */
public interface IOidcService {

    /**
     * Build the provider's authorization URL and remember the {@code state} / {@code nonce}
     * / PKCE verifier for the upcoming callback.
     *
     * @param providerId the provider id (as it appears in the login URL path)
     * @return the authorization URL the browser should be redirected to
     */
    String buildAuthorizeUrl(String providerId);

    /**
     * Handle the provider callback: validate {@code state}, exchange the code for tokens,
     * verify the {@code id_token} and issue a local token.
     *
     * @param providerId the provider id (as it appears in the login URL path)
     * @param code       the authorization code returned by the provider
     * @param state      the state returned by the provider
     * @return the local user with a freshly signed access token, or empty when the
     *         callback is invalid or the user is not provisioned locally
     */
    Optional<UserDTO> handleCallback(String providerId, String code, String state);
}
