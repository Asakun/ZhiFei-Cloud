package com.dji.sample.oidc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the OIDC client (this service acts as the Relying Party).
 *
 * <p>Only the on/off switch lives here. The per-provider configuration (client id/secret,
 * issuer, endpoints, keys, ...) is stored in the database ({@code oidc_provider} table) and
 * managed through the management API.
 *
 * @author dyw
 */
@Data
@Component
@ConfigurationProperties(prefix = "oidc")
public class OidcProperties {

    /**
     * Whether the OIDC login is enabled.
     */
    private boolean enabled = false;
}
