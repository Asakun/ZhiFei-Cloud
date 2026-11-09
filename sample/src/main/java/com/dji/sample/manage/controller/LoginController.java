package com.dji.sample.manage.controller;

import com.dji.sample.common.error.CommonErrorEnum;
import com.dji.sample.common.util.IpUtil;
import com.dji.sample.component.CaptchaUtil;
import com.dji.sample.component.RsaUtil;
import com.dji.sample.manage.model.dto.UserDTO;
import com.dji.sample.manage.model.dto.UserLoginDTO;
import com.dji.sample.manage.service.IUserService;
import com.dji.sdk.common.HttpResultResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

import static com.dji.sample.component.AuthInterceptor.PARAM_TOKEN;

@RestController
@RequestMapping("${url.manage.prefix}${url.manage.version}")
public class LoginController {

    @Autowired
    private IUserService userService;

    @Autowired
    private RsaUtil rsaUtil;

    @Autowired
    private CaptchaUtil captchaUtil;

    @PostMapping("/login")
    public HttpResultResponse login(HttpServletRequest request, @RequestBody UserLoginDTO loginDTO) {
        String ip = IpUtil.getClientIp(request);
        HttpResultResponse response;
        if (captchaUtil.validateCaptcha(loginDTO.getCaptchaKey(), loginDTO.getCaptchaCode())){
            String username = loginDTO.getUsername();
            String password = rsaUtil.decryptByPrivateKey(loginDTO.getPassword());
            response = userService.userLogin(username, password, loginDTO.getFlag().getVal());
        } else {
            response = HttpResultResponse.error("invalid captcha");

        }
        if (response.getCode() == 0){
            captchaUtil.loginSuccess(ip);
        } else {
            int remainingCount = captchaUtil.loginFail(ip);
            response.setData("remaining login count:" + remainingCount);
        }
        return response;
    }

    @PostMapping("/token/refresh")
    public HttpResultResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String token = request.getHeader(PARAM_TOKEN);
        Optional<UserDTO> user = userService.refreshToken(token);

        if (user.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return HttpResultResponse.error(CommonErrorEnum.NO_TOKEN.getMessage());
        }

        return HttpResultResponse.success(user.get());
    }

    @GetMapping("/rsa/public-key")
    public HttpResultResponse getRsaPublicKey() {
        return HttpResultResponse.success(rsaUtil.getPublicKey());
    }

    @GetMapping("/captcha")
    public HttpResultResponse getCaptcha(HttpServletRequest request){
        String ip = IpUtil.getClientIp(request);
        return captchaUtil.getCaptcha(ip);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/login-fail/list")
    public HttpResultResponse getLoginFailList() {
        return HttpResultResponse.success(captchaUtil.loginFailList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/login-fail/del")
    public HttpResultResponse deleteLoginFail(String ip) {
        captchaUtil.loginSuccess(ip);
        return HttpResultResponse.success();
    }
}
