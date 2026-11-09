package com.dji.sample.configuration;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        // 1. 连接池管理器
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200); // 最大连接数
        connectionManager.setDefaultMaxPerRoute(20); // 每个路由的最大连接数

        // 2. 请求配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(15000)    // 连接超时时间（毫秒）
                .setSocketTimeout(30000)    // 读取超时时间（毫秒）
                .setConnectionRequestTimeout(5000) // 从连接池获取连接的超时时间
                .build();

        // 3. HttpClient
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryHandler(retryHandler) // 这里设置重试次数为1
                .build();

        // 4. RestTemplate工厂
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        // 5. 创建RestTemplate
        return new RestTemplate(factory);
    }

    HttpRequestRetryHandler retryHandler = (exception, executionCount, context) -> {
        if (executionCount > 1) {
            return false;
        }
        // 对所有IOException都重试，包括超时
        return exception instanceof IOException;
    };
}