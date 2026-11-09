package com.dji.sample.component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RestTemplateUtil {
    @Autowired
    private RestTemplate restTemplate;

    public <T> T getWithApiKey(String url, String apiKey, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", apiKey);
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
        ).getBody();
    }
}
