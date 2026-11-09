package com.dji.sample.oidc.config;

import com.dji.sample.oidc.model.OidcProviderEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and caches one {@link JwtDecoder} per provider, used to verify the provider's
 * {@code id_token}.
 *
 * <p>Provider configuration now lives in the database, so decoders are created lazily on
 * first use and cached. When a provider is created, updated or deleted the corresponding
 * cache entry must be evicted via {@link #evict(String)} so stale issuer / JWKS settings
 * are no longer accepted.
 *
 * @author dyw
 */
@Slf4j
@Component
public class OidcDecoderFactory {

    /**
     * Allowed clock skew when validating {@code exp} / {@code nbf}.
     */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final Map<String, JwtDecoder> cache = new ConcurrentHashMap<>();

    /**
     * Get (or lazily build) the decoder for a provider.
     *
     * @param provider the provider configuration
     * @return the decoder bound to this provider's issuer / audience / key
     */
    public JwtDecoder get(OidcProviderEntity provider) {
        return cache.computeIfAbsent(provider.getProviderId(), id -> build(provider));
    }

    /**
     * Drop the cached decoder for a provider (call after create / update / delete).
     */
    public void evict(String providerId) {
        cache.remove(providerId);
    }

    /**
     * Drop all cached decoders.
     */
    public void evictAll() {
        cache.clear();
    }

    private JwtDecoder build(OidcProviderEntity provider) {
        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(provider.getJwkSetUri())) {
            // Fetch the provider's public key from its JWKS endpoint. This keeps working
            // even when the provider rotates its signing key on every startup.
            decoder = NimbusJwtDecoder.withJwkSetUri(provider.getJwkSetUri()).build();
        } else {
            RSAPublicKey publicKey = parsePublicKey(provider.getProviderId(), provider.getPublicKey());
            decoder = NimbusJwtDecoder
                    .withPublicKey(publicKey)
                    .signatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                    .build();
        }

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtIssuerValidator(provider.getIssuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(provider.getClientId())));

        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Parse a PEM encoded RSA public key (SubjectPublicKeyInfo / X.509).
     *
     * @param providerId the provider id, used for error messages
     * @param pem        the PEM text, may contain newlines or escaped {@code \n}
     * @return the RSA public key
     */
    private RSAPublicKey parsePublicKey(String providerId, String pem) {
        if (!StringUtils.hasText(pem)) {
            throw new IllegalStateException("oidc_provider[" + providerId
                    + "] public_key must be configured when jwk_set_uri is not set");
        }
        String normalized = pem.replace("\\n", "\n")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] decoded = Base64.getDecoder().decode(normalized);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse oidc_provider[" + providerId
                    + "] public_key as an RSA public key", e);
        }
    }
}
