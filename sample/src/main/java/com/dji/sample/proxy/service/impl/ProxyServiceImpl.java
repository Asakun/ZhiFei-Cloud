package com.dji.sample.proxy.service.impl;

import com.dji.sample.proxy.service.IProxyService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


@Service
public class ProxyServiceImpl implements IProxyService {

    // 注入 RestTemplate（用于转发 HTTP 请求）
    private final RestTemplate restTemplate;

    public ProxyServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ResponseEntity<String> getRequest(String url, HttpHeaders headers, MultiValueMap<String, String> params) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
        for (String key : params.keySet()) {
            String val = params.getFirst(key);
            uriBuilder.queryParam(key, val);
        }
        url = uriBuilder.toUriString();
        // 2. 构建转发请求（包含参数和头信息）
        HttpEntity<?> httpEntity = new HttpEntity<>(headers);

        // 3. 转发 GET 请求到目标 API，并获取响应
        ResponseEntity<String> targetResponse = restTemplate.exchange(
                url,    // 目标 API 地址
                HttpMethod.GET,    // 与客户端请求方法一致
                httpEntity,        // 包含头信息
                String.class       // 响应体类型
        );

        // 4. 将目标 API 的响应返回给客户端
        return new ResponseEntity<>(
                targetResponse.getBody(),
                targetResponse.getHeaders(),
                targetResponse.getStatusCode()
        );
    }

    @Override
    public ResponseEntity<String> postRequest(String url, HttpHeaders headers, String requestBody, MultiValueMap<String, String> params) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
        for (String key : params.keySet()) {
            String val = params.getFirst(key);
            uriBuilder.queryParam(key, val);
        }
        url = uriBuilder.toUriString();

        // 1. 构建包含请求体和头信息的请求实体
        HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, headers);

        // 2. 转发 POST 请求到目标 API
        ResponseEntity<String> targetResponse = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                String.class
        );

        // 3. 返回目标 API 的响应
        return targetResponse;
    }

    @Override
    public ResponseEntity<String> postFileRequest(String url, HttpHeaders headers, MultiValueMap<String, Object> requestBody, MultiValueMap<String, String> params) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
        for (String key : params.keySet()) {
            String val = params.getFirst(key);
            uriBuilder.queryParam(key, val);
        }
        url = uriBuilder.toUriString();

        // 1. 构建包含请求体和头信息的请求实体
        HttpEntity<MultiValueMap<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

        // 2. 转发 POST 请求到目标 API
        ResponseEntity<String> targetResponse = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                String.class
        );

        // 3. 返回目标 API 的响应
        return targetResponse;
    }

    @Override
    public ResponseEntity<String> deleteRequest(String url, HttpHeaders headers, MultiValueMap<String, String> params) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
        for (String key : params.keySet()) {
            String val = params.getFirst(key);
            uriBuilder.queryParam(key, val);
        }
        url = uriBuilder.toUriString();

        HttpEntity<?> httpEntity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.DELETE, httpEntity, String.class);
    }
}
