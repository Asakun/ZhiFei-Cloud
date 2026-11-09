package com.dji.sample.oidc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * OIDC provider configuration submitted to the management API.
 *
 * <p>On update, an empty {@code clientSecret} means "keep the existing secret".
 *
 * @author dyw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderParam {

    @NotBlank
    private String providerId;

    @NotBlank
    private String clientId;

    /**
     * The client secret. Required on create; optional on update (empty keeps the old value).
     */
    private String clientSecret;

    @NotBlank
    private String issuer;

    @NotBlank
    private String authorizationUri;

    @NotBlank
    private String tokenUri;

    @NotBlank
    private String redirectUri;

    private String jwkSetUri;

    private String publicKey;

    private String scopes;

    private String usernameClaim;

    private Long stateTtl;

    private Boolean enable;
}
