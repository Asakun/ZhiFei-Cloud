package com.dji.sample.component;

import com.dji.sample.common.error.CommonErrorEnum;
import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.common.util.JwtUtil;
import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String PARAM_TOKEN = "x-auth-token";

    public static final String PARAM_API_KEY = "x-api-key";

    public static final String TOKEN_CLAIM = "customClaim";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        log.debug("request uri: {}, IP: {}", uri, request.getRemoteAddr());
        // The options method is passed directly.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpStatus.OK.value());
            return false;
        }
        String token = request.getHeader(PARAM_TOKEN);
        String apiKey = request.getHeader(PARAM_API_KEY);
        Optional<CustomClaim> customClaimOpt;
        // Check if the token exists.
        if (StringUtils.hasText(token)) {
            customClaimOpt = JwtUtil.parseToken(token);
        } else if (StringUtils.hasText(apiKey)) {
            customClaimOpt = Optional.ofNullable((CustomClaim) RedisOpsUtils.get(RedisConst.API_KEY + apiKey));
        } else {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            log.error(CommonErrorEnum.NO_TOKEN.getMessage());
            return false;
        }

        // Check if the current token is valid.
        if (customClaimOpt.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        // Put the custom data from the token into the request.
        request.setAttribute(TOKEN_CLAIM, customClaimOpt.get());

        setAuthentication(customClaimOpt.get());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // Delete the custom data in the request after the request ends.
        request.removeAttribute(TOKEN_CLAIM);
    }

    private void setAuthentication(CustomClaim customClaim) {
        if (customClaim.getAuthorities() != null) {
            String username = customClaim.getUsername();
            List<String> roles = Arrays.asList(customClaim.getAuthorities().split(","));
            List<SimpleGrantedAuthority> auths = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());;
            Authentication auth = new UsernamePasswordAuthenticationToken(username, null, auths);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }
}
