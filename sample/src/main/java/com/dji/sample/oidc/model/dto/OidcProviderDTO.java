package com.dji.sample.oidc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OIDC provider configuration returned to the management API.
 *
 * <p>Deliberately excludes {@code clientSecret} so the secret is never exposed.
 *
 * @author dyw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderDTO {

    private String providerId;

    private String clientId;

    /**
     * Whether a client secret is configured. The secret value itself is never returned.
     */
    private Boolean hasSecret;

    private String issuer;

    private String authorizationUri;

    private String tokenUri;

    private String redirectUri;

    private String jwkSetUri;

    private String scopes;

    private String usernameClaim;

    private Long stateTtl;

    private Boolean enable;

    private Long createTime;

    private Long updateTime;
}
