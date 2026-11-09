package com.dji.sample.common.util;

import org.springframework.util.StringUtils;
import javax.servlet.http.HttpServletRequest;
import java.net.*;
import java.util.Enumeration;

/**
 * Spring 获取客户端 IP 工具类（适配直连/代理/网关场景）
 */
public class IpUtil {

    // 代理/网关常用的 IP 请求头（优先级从高到低）
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", // 多层代理时，第一个为真实 IP（格式：客户端IP, 代理1IP, 代理2IP...）
            "X-Real-IP",       // Nginx 常用，直接返回真实 IP
            "Proxy-Client-IP", // 代理服务器（如 Apache）
            "WL-Proxy-Client-IP", // WebLogic 代理
            "HTTP_CLIENT_IP",  // 部分代理
            "HTTP_X_FORWARDED_FOR" // 兼容小写/下划线
    };

    /**
     * 获取客户端真实 IP 地址
     * @param request HttpServletRequest 对象
     * @return 客户端 IP（如 192.168.1.100，本地环境可能为 0:0:0:0:0:0:0:1 或 127.0.0.1）
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String ip = null;
        // 1. 优先从代理请求头中获取真实 IP
        for (String header : IP_HEADERS) {
            ip = request.getHeader(header);
            // 跳过空值/unknown
            if (!StringUtils.isEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
                break;
            }
        }

        // 2. 处理 X-Forwarded-For 多 IP 场景（取第一个非 unknown 的 IP）
        if (!StringUtils.isEmpty(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        // 3. 代理场景未获取到，取直连 IP
        if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
            // 处理本地回环地址（IPv6 转 IPv4）
            if ("0:0:0:0:0:0:0:1".equals(ip)) {
                ip = "127.0.0.1";
            }
        }

        // 4. 最终兜底（理论上不会走到这）
        return StringUtils.isEmpty(ip) ? "unknown" : ip;
    }

    /**
     * 获取服务器真实IP（排除本地回环）
     */
    public static String getServerRealIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // 排除回环地址、虚拟地址、IPv6
                    if (!addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()
                            && addr.getHostAddress().indexOf(":") == -1) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "172.17.0.1";
    }
}
