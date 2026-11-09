package com.dji.sample.proxy.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

public interface IProxyService {
    ResponseEntity<String> getRequest(String url, HttpHeaders headers, MultiValueMap<String, String> params);

    ResponseEntity<String> postRequest(String url, HttpHeaders headers, String requestBody, MultiValueMap<String, String> params);

    ResponseEntity<String> postFileRequest(String url, HttpHeaders headers, MultiValueMap<String, Object> requestBody, MultiValueMap<String, String> params);

    ResponseEntity<String> deleteRequest(String url, HttpHeaders headers, MultiValueMap<String, String> params);
}
