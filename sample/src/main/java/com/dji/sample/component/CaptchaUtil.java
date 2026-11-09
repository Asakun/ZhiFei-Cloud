package com.dji.sample.component;

import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sdk.common.HttpResultResponse;
import com.google.common.util.concurrent.RateLimiter;
import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CaptchaUtil {

    // 验证码过期时间（秒）
    @Value("${spring.captcha.expire:300}")
    private int captchaExpire;
    // 验证码图片宽度
    @Value("${spring.captcha.width:120}")
    private int width;
    // 验证码图片高度
    @Value("${spring.captcha.height:40}")
    private int height;

    @Value("${spring.captcha.fail-limit:5}")
    private int failLimit;

    private final RateLimiter rateLimiter = RateLimiter.create(0.5);

    /**
     * 生成图形验证码
     */
    public Map<String, String> generateCaptcha() {
        // 1. 生成算术验证码（也可使用 SpecCaptcha 字母数字验证码）
        Captcha captcha = new ArithmeticCaptcha(width, height);
        captcha.setLen(2); // 算术题位数（2 位：如 1+1=?）
        String captchaCode = captcha.text().trim(); // 验证码答案

        // 2. 生成唯一标识（绑定验证码，避免跨用户复用）
        String captchaKey = RedisConst.CAPTCHA + UUID.randomUUID().toString().replace("-", "");

        // 3. 存入 Redis，设置过期时间
        RedisOpsUtils.setWithExpire(captchaKey, captchaCode, captchaExpire);

        Map<String, String> result = new HashMap<>();
        result.put("image", captcha.toBase64());
        result.put("captcha_key", captchaKey);
        return result;
    }

    public HttpResultResponse getCaptcha(String ip) {
        if (!rateLimiter.tryAcquire()) {
            return HttpResultResponse.error("request frequency too high");
        }
        if (checkFailCount(ip)) {
            return HttpResultResponse.error("login attempts exhausted");
        }
        return HttpResultResponse.success(generateCaptcha());
    }

    /**
     * 校验验证码
     * @param captchaKey 验证码标识
     * @param captchaCode 用户输入的验证码
     * @return 校验结果
     */
    public boolean validateCaptcha(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null) {
            return false;
        }
        // 从 Redis 获取验证码
        Object realCode = RedisOpsUtils.get(captchaKey);
        if (realCode == null) {
            return false; // 验证码过期或不存在
        }
        // 校验成功后删除验证码（防止重复使用）
        RedisOpsUtils.del(captchaKey);
        return realCode.toString().equalsIgnoreCase(captchaCode.trim());
    }

    public boolean checkFailCount(String ip) {
        String key = RedisConst.CAPTCHA_FAIL_KEY + ip;
        Object val = RedisOpsUtils.get(key);
        if (val != null) {
            int count = Integer.parseInt(val.toString());
            return count >= failLimit;
        }
        return false;
    }

    public int loginFail(String ip) {
        String key = RedisConst.CAPTCHA_FAIL_KEY + ip;
        Object val = RedisOpsUtils.get(key);
        int count = 1;
        if (val != null) {
            count = count + Integer.parseInt(val.toString());
        }
        RedisOpsUtils.setWithExpire(key,count, 86400);
        return failLimit - count;
    }

    public void loginSuccess(String ip) {
        String key = RedisConst.CAPTCHA_FAIL_KEY + ip;
        RedisOpsUtils.del(key);
    }

    public Map<String, Object> loginFailList() {
        return RedisOpsUtils.getKeyValByPrefix(RedisConst.CAPTCHA_FAIL_KEY);
    }
}
