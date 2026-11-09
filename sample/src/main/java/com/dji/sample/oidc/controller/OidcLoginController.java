package com.dji.sample.oidc.controller;

import com.dji.sample.manage.model.dto.UserDTO;
import com.dji.sample.oidc.service.IOidcService;
import com.dji.sdk.common.HttpResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * OIDC client endpoints (Relying Party side).
 *
 * <p>Both endpoints are whitelisted in {@code GlobalMVCConfigurer} because the caller
 * does not carry a local {@code x-auth-token} yet.
 *
 * @author dyw
 */
@Slf4j
@RestController
@RequestMapping("${url.manage.prefix}${url.manage.version}/login/oidc")
@ConditionalOnProperty(prefix = "oidc", name = "enabled", havingValue = "true")
public class OidcLoginController {

    @Autowired
    private IOidcService oidcService;

    /**
     * Start the login: redirect the browser to the provider's authorization endpoint.
     *
     * <p>The provider is selected by the path, e.g. {@code /login/oidc/oidc-test}.
     */
    @GetMapping("/{provider}")
    public void authorize(@PathVariable String provider, HttpServletResponse response) throws IOException {
        String authorizeUrl;
        try {
            authorizeUrl = oidcService.buildAuthorizeUrl(provider);
        } catch (IllegalArgumentException e) {
            log.warn("OIDC authorize failed: {}", e.getMessage());
            response.sendError(HttpStatus.NOT_FOUND.value(), e.getMessage());
            return;
        }
        response.sendRedirect(authorizeUrl);
    }

    /**
     * Handle the provider callback and issue a local token.
     *
     * <p>When the provider reports an error (e.g. {@code invalid_scope}) it redirects back
     * with {@code error} instead of {@code code}, so both parameters are optional here.
     */
    @GetMapping("/{provider}/callback")
    public HttpResultResponse<UserDTO> callback(@PathVariable String provider,
                                                @RequestParam(required = false) String code,
                                                @RequestParam(required = false) String state,
                                                @RequestParam(required = false) String error,
                                                @RequestParam(name = "error_description", required = false) String errorDescription,
                                                HttpServletResponse response) {
        if (StringUtils.hasText(error)) {
            log.warn("OIDC provider returned an error: {} - {}", error, errorDescription);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return HttpResultResponse.<UserDTO>error("oidc login failed: " + error
                    + (StringUtils.hasText(errorDescription) ? " - " + errorDescription : ""));
        }
        Optional<UserDTO> user = oidcService.handleCallback(provider, code, state);
        if (user.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return HttpResultResponse.<UserDTO>error("oidc login failed");
        }
        return HttpResultResponse.success(user.get());
    }
}
