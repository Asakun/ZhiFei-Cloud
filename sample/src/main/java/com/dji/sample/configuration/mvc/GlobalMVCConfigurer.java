package com.dji.sample.configuration.mvc;

import com.dji.sample.component.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class GlobalMVCConfigurer implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    private static List<String> excludePaths = new ArrayList<>();

    @Value("${url.manage.prefix}")
    private String managePrefix;

    @Value("${url.manage.version}")
    private String manageVersion;

    @Value("${url.psdk.prefix}")
    private String psdkPrefix;

    @Value("${url.psdk.version}")
    private String psdkVersion;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Exclude the login interface.
        excludePaths.add("/" + managePrefix + manageVersion + "/login");
        excludePaths.add("/" + managePrefix + manageVersion + "/login/oidc/**");
        excludePaths.add("/" + managePrefix + manageVersion + "/token/refresh");
        excludePaths.add("/" + managePrefix + manageVersion + "/rsa/public-key");
        excludePaths.add("/" + managePrefix + manageVersion + "/captcha");
        excludePaths.add("/" + psdkPrefix + psdkVersion + "/aero_spot/file/upload");
        excludePaths.add("/swagger-ui.html");
        excludePaths.add("/swagger-ui/**");
        excludePaths.add("/v3/**");
        excludePaths.add("/ui/**");
        // Intercept for all request interfaces.
        registry.addInterceptor(authInterceptor).addPathPatterns("/**").excludePathPatterns(excludePaths);
    }
}
