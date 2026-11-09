package com.dji.sample.oidc.service.impl;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.common.util.JwtUtil;
import com.dji.sample.component.mqtt.config.MqttPropertyConfiguration;
import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.manage.model.dto.UserDTO;
import com.dji.sample.manage.model.dto.WorkspaceDTO;
import com.dji.sample.manage.model.entity.RoleEntity;
import com.dji.sample.manage.model.entity.UserEntity;
import com.dji.sample.manage.service.IUserDetailService;
import com.dji.sample.manage.service.IWorkspaceService;
import com.dji.sample.oidc.config.OidcDecoderFactory;
import com.dji.sample.oidc.model.OidcProviderEntity;
import com.dji.sample.oidc.service.IOidcProviderService;
import com.dji.sample.oidc.service.IOidcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal OIDC client implementation (Authorization Code flow).
 *
 * <p>Flow: build the authorization URL with {@code state} / {@code nonce} / PKCE, then on
 * the callback exchange the code for tokens, verify the {@code id_token} with the
 * provider's public key, and finally issue a local token with {@link JwtUtil}. The two
 * JWT systems remain independent.
 *
 * @author dyw
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "oidc", name = "enabled", havingValue = "true")
public class OidcServiceImpl implements IOidcService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String NONCE_KEY = "nonce";

    private static final String VERIFIER_KEY = "code_verifier";

    private static final String PROVIDER_KEY = "provider";

    @Autowired
    private IOidcProviderService providerService;

    @Autowired
    private OidcDecoderFactory decoderFactory;

    @Autowired
    private IUserDetailService userDetailService;

    @Autowired
    private IWorkspaceService workspaceService;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String buildAuthorizeUrl(String providerId) {
        OidcProviderEntity provider = providerService.getEnabledProvider(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or disabled OIDC provider: " + providerId));
        String state = randomToken(32);
        String nonce = randomToken(32);
        String codeVerifier = randomToken(64);
        String codeChallenge = codeChallenge(codeVerifier);

        Map<String, String> session = new HashMap<>(4);
        session.put(NONCE_KEY, nonce);
        session.put(VERIFIER_KEY, codeVerifier);
        session.put(PROVIDER_KEY, providerId);
        RedisOpsUtils.setWithExpire(RedisConst.OIDC_STATE_PREFIX + state, session, provider.getStateTtl());

        return UriComponentsBuilder.fromHttpUrl(provider.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", provider.getRedirectUri())
                .queryParam("scope", provider.getScopes())
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public Optional<UserDTO> handleCallback(String providerId, String code, String state) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            log.warn("OIDC callback is missing code or state.");
            return Optional.empty();
        }

        // Consume the state exactly once.
        String stateKey = RedisConst.OIDC_STATE_PREFIX + state;
        Object cached = RedisOpsUtils.get(stateKey);
        RedisOpsUtils.del(stateKey);
        if (!(cached instanceof Map)) {
            log.warn("OIDC callback state is invalid or already used.");
            return Optional.empty();
        }
        Map<?, ?> session = (Map<?, ?>) cached;
        String nonce = String.valueOf(session.get(NONCE_KEY));
        String codeVerifier = String.valueOf(session.get(VERIFIER_KEY));
        String cachedProvider = String.valueOf(session.get(PROVIDER_KEY));

        // The provider in the callback path must match the one that started the flow.
        if (!providerId.equals(cachedProvider)) {
            log.warn("OIDC provider mismatch: path={}, state={}", providerId, cachedProvider);
            return Optional.empty();
        }

        Optional<OidcProviderEntity> providerOpt = providerService.getEnabledProvider(providerId);
        if (providerOpt.isEmpty()) {
            log.warn("OIDC provider [{}] is unknown or disabled.", providerId);
            return Optional.empty();
        }
        OidcProviderEntity provider = providerOpt.get();

        String idToken = exchangeCode(provider, code, codeVerifier);
        if (!StringUtils.hasText(idToken)) {
            return Optional.empty();
        }

        Jwt jwt;
        try {
            jwt = decoderFactory.get(provider).decode(idToken);
        } catch (Exception e) {
            log.warn("OIDC id_token verification failed: {}", e.getMessage());
            return Optional.empty();
        }

        if (!nonce.equals(jwt.getClaimAsString("nonce"))) {
            log.warn("OIDC id_token nonce mismatch.");
            return Optional.empty();
        }

        String username = jwt.getClaimAsString(provider.getUsernameClaim());
        if (!StringUtils.hasText(username)) {
            username = jwt.getSubject();
        }
        return buildLocalUser(username);
    }

    /**
     * Exchange the authorization code for tokens at the provider's token endpoint.
     *
     * @return the {@code id_token}, or {@code null} when the exchange fails
     */
    private String exchangeCode(OidcProviderEntity provider, String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", provider.getRedirectUri());
        form.add("client_id", provider.getClientId());
        form.add("client_secret", provider.getClientSecret());
        form.add("code_verifier", codeVerifier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    provider.getTokenUri(), new HttpEntity<>(form, headers), String.class);
            JsonNode body = objectMapper.readTree(response.getBody());
            return body.path("id_token").asText(null);
        } catch (Exception e) {
            log.warn("OIDC token exchange failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Map the external identity to a local user and issue a local token.
     *
     * <p>The user must already exist locally; unknown users are rejected.
     */
    private Optional<UserDTO> buildLocalUser(String username) {
        UserEntity userEntity = userDetailService.getUserByUsername(username);
        if (userEntity == null) {
            log.warn("OIDC user [{}] is not provisioned locally.", username);
            return Optional.empty();
        }
        if (userEntity.getStatus() == null || userEntity.getStatus() != 1) {
            log.warn("OIDC user [{}] is not active.", username);
            return Optional.empty();
        }

        RoleEntity role = userDetailService.getRoleByUserId(userEntity.getUserId());
        if (role == null) {
            log.warn("OIDC user [{}] has no role assigned.", username);
            return Optional.empty();
        }
        String authorities = role.getRoleCode();
        Integer level = userDetailService.getMaxLevelByCodes(authorities);
        if (level == null || level >= 4) {
            log.warn("OIDC login is not allowed for user [{}] with level [{}].", username, level);
            return Optional.empty();
        }

        Optional<WorkspaceDTO> workspaceOpt = workspaceService.getWorkspaceByWorkspaceId(userEntity.getWorkspaceId());
        if (workspaceOpt.isEmpty()) {
            log.warn("OIDC user [{}] has an invalid workspace.", username);
            return Optional.empty();
        }

        CustomClaim customClaim = new CustomClaim(userEntity.getUserId(), userEntity.getUsername(),
                userEntity.getUserType(), workspaceOpt.get().getWorkspaceId(), authorities, level);
        String token = JwtUtil.createToken(customClaim.convertToMap());

        UserDTO userDTO = UserDTO.builder()
                .userId(userEntity.getUserId())
                .username(userEntity.getUsername())
                .userType(userEntity.getUserType())
                .remark(userEntity.getRemark())
                .status(userEntity.getStatus())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .workspaceId(workspaceOpt.get().getWorkspaceId())
                .mqttAddr(MqttPropertyConfiguration.getBasicMqttAddress())
                .accessToken(token)
                .build();
        return Optional.of(userDTO);
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String codeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute PKCE code challenge", e);
        }
    }
}
