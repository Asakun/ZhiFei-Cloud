package com.dji.sample.proxy.service.impl;

import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.proxy.model.EmqxUser;
import com.dji.sample.proxy.service.IEmqxService;
import com.dji.sample.proxy.service.IProxyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;

import static com.dji.sample.common.util.CommonUtil.randomPwdGenerate;

@Service
@Slf4j
public class EmqxServiceImpl implements IEmqxService {
    @Autowired
    private IProxyService proxyService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${proxy.emqx.url}")
    private String TARGET_API_URL;

    @Value("${proxy.emqx.username}")
    private String username;

    @Value("${proxy.emqx.password}")
    private String password;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final String emqxAuthenticatorId = "password_based:built_in_database";

    private String getRedisKey() {
        return RedisConst.SYSTEM_TOKEN + "emqx";
    }

    private String getToken() throws JsonProcessingException {
        String key = getRedisKey();
        Object val = RedisOpsUtils.get(key);
        String token;
        if (val == null) {
            token = Login();
        } else {
            token = val.toString();
        }
        return token;
    }

    private void delToken() {
        RedisOpsUtils.del(getRedisKey());
    }

    private HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        if (token != null) {
            headers.add("Authorization", String.format("Bearer %s", token));
        }
        return headers;
    }

    private String getUrl(String path) {
        return String.format("%s/%s/%s", TARGET_API_URL, "api/v5", path);
    }

    private String getAuthenticationUsersUrl(String authenticatorId) {
        return getUrl(String.format("authentication/%s/users", authenticatorId));
    }

    private String getAuthenticationUserUrl(String authenticatorId, String userId) {
        return getUrl(String.format("authentication/%s/users/%s", authenticatorId, userId));
    }

    private String Login() throws JsonProcessingException {
        String url = getUrl("login");
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("username", this.username);
        rootNode.put("password", this.password);
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(null), requestBody, new LinkedMultiValueMap<>());
        JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
        String token = responseNode.get("token").asText();
        RedisOpsUtils.setWithExpire(getRedisKey(), token, 3600);
        return token;
    }

    @Override
    public ArrayNode getClientList(String clientId) throws JsonProcessingException {
        String url = getUrl("clients_v2");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "1");
        params.add("limit", "100");
        params.add("username", this.username);
        params.add("fields", "clientid,username,connected");
        if (clientId != null) {
            params.add("like_clientid", clientId);
        }
        String token = getToken();
        try {
            ResponseEntity<String> responseEntity = proxyService.getRequest(url, getHeaders(token), params);
            JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return (ArrayNode) responseNode.get("data");
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                delToken();
            }
            throw new HttpClientErrorException(e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    @Override
    public boolean kickClients(ArrayNode clientIds) throws JsonProcessingException {
        String url = getUrl("clients/kickout/bulk");
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(clientIds);
        String token = getToken();
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(token), requestBody, new LinkedMultiValueMap<>());
        return responseEntity.getStatusCode() == HttpStatus.NO_CONTENT;
    }

    @Override
    public boolean addAuthenticationUser(EmqxUser userData) throws JsonProcessingException {
        return addAuthenticationUser(emqxAuthenticatorId, userData);
    }

    @Override
    public boolean addAuthenticationUser(String authenticatorId, EmqxUser userData) throws JsonProcessingException {
        String url = getAuthenticationUsersUrl(authenticatorId);
        String requestBody = objectMapper.writeValueAsString(userData);
        String token = getToken();
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(token), requestBody, new LinkedMultiValueMap<>());
        HttpStatus status = responseEntity.getStatusCode();
        if (status == HttpStatus.CREATED || status == HttpStatus.OK) {
            return true;
        }
        log.warn("Failed to add authentication user, status: {}, body: {}", status, responseEntity.getBody());
        return false;
    }

    @Override
    public JsonNode getAuthentications() throws JsonProcessingException {
        String url = getUrl("authentication");
        String token = getToken();
        ResponseEntity<String> responseEntity = proxyService.getRequest(url, getHeaders(token), new LinkedMultiValueMap<>());
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return objectMapper.readTree(responseEntity.getBody());
        }
        return null;
    }

    @Override
    public List<String> listAuthenticationUsers(Integer page, Integer limit, String likeUserId, Boolean isSuperuser) throws JsonProcessingException {
        return listAuthenticationUsers(emqxAuthenticatorId, page, limit, likeUserId, isSuperuser);
    }

    @Override
    public List<String> listAuthenticationUsers(String authenticatorId, Integer page, Integer limit, String likeUserId, Boolean isSuperuser) throws JsonProcessingException {
        String url = getAuthenticationUsersUrl(authenticatorId);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (page != null) {
            params.add("page", page.toString());
        }
        if (limit != null) {
            params.add("limit", limit.toString());
        }
        if (likeUserId != null) {
            params.add("like_user_id", likeUserId);
        }
        if (isSuperuser != null) {
            params.add("is_superuser", isSuperuser.toString());
        }
        String token = getToken();
        try {
            ResponseEntity<String> responseEntity = proxyService.getRequest(url, getHeaders(token), params);
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
                ArrayNode data = (ArrayNode) responseNode.get("data");
                List<String> userIds = new ArrayList<>();
                if (data != null) {
                    for (JsonNode userNode : data) {
                        JsonNode userIdNode = userNode.get("user_id");
                        if (userIdNode != null) {
                            userIds.add(userIdNode.asText());
                        }
                    }
                }
                return userIds;
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                delToken();
            }
            log.warn("Failed to list authentication users, status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new HttpClientErrorException(e.getStatusCode(), e.getResponseBodyAsString());
        }
        return null;
    }

    @Override
    public boolean deleteAuthenticationUser(String userId) throws JsonProcessingException {
        return deleteAuthenticationUser(emqxAuthenticatorId, userId);
    }

    @Override
    public boolean deleteAuthenticationUser(String authenticatorId, String userId) throws JsonProcessingException {
        String url = getAuthenticationUserUrl(authenticatorId, userId);
        String token = getToken();
        ResponseEntity<String> responseEntity = proxyService.deleteRequest(url, getHeaders(token), new LinkedMultiValueMap<>());
        if (responseEntity.getStatusCode() == HttpStatus.NO_CONTENT) {
            return true;
        }
        log.warn("Failed to delete authentication user, status: {}, body: {}", responseEntity.getStatusCode(), responseEntity.getBody());
        return false;
    }

    @Override
    public EmqxUser registerEmqxUser(String username, String password) throws JsonProcessingException {
        String uid = String.format("pilot_%s", username);
        List<String> list = listAuthenticationUsers(1, 20, uid, null);
        for (String userId : list) {
            if (userId.equals(uid)) {
                throw new IllegalArgumentException("username in emqx already exists");
            }
        }
        if (StringUtils.isEmpty(password)) {
            password = randomPwdGenerate(16);
        }
        EmqxUser user = EmqxUser.builder().userId(uid).password(password).isSuperuser(false).build();
        if (addAuthenticationUser(user)) {
            return user;
        }
        throw new IllegalStateException("failed to register emqx user");
    }

    @Override
    public boolean ensureJwtAuthenticator() throws JsonProcessingException {
        String url = getUrl("authentication");
        String token = getToken();
        JsonNode authentications = getAuthentications();
        if (authentications != null) {
            for (JsonNode authenticator : authentications) {
                JsonNode mechanism = authenticator.get("mechanism");
                if (mechanism != null && "jwt".equals(mechanism.asText())) {
                    log.info("emqx jwt authenticator already exists, skip init.");
                    return true;
                }
            }
        }
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("from", "password");
        rootNode.put("use_jwks", false);
        rootNode.putObject("verify_claims");
        rootNode.put("disconnect_after_expire", true);
        rootNode.put("algorithm", "hmac-based");
        rootNode.put("secret", jwtSecret);
        rootNode.put("secret_base64_encoded", false);
        rootNode.put("mechanism", "jwt");
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(token), requestBody, new LinkedMultiValueMap<>());
        HttpStatus status = responseEntity.getStatusCode();
        if (status == HttpStatus.CREATED || status == HttpStatus.OK) {
            log.info("emqx jwt authenticator created.");
            return true;
        }
        log.warn("Failed to create emqx jwt authenticator, status: {}, body: {}", status, responseEntity.getBody());
        return false;
    }

    @Override
    public boolean ensureEmqxAccount(String username, String password, Boolean isSuperuser) throws JsonProcessingException {
        if (StringUtils.isEmpty(username)) {
            log.warn("emqx account username is empty, skip init.");
            return false;
        }
        List<String> list = listAuthenticationUsers(1, 20, username, null);
        for (String userId : list) {
            if (userId.equals(username)) {
                log.info("emqx account {} already exists, skip init.", username);
                return true;
            }
        }
        EmqxUser user = EmqxUser.builder().userId(username).password(password).isSuperuser(isSuperuser).build();
        if (addAuthenticationUser(user)) {
            log.info("emqx account {} created.", username);
            return true;
        }
        log.warn("failed to create emqx account {}.", username);
        return false;
    }

    @Override
    public Boolean closeEmqxUser(String username) throws JsonProcessingException {
        List<String> list = listAuthenticationUsers(1, 20, username, null);
        for (String userId : list) {
            if (userId.equals(username)) {
                return deleteAuthenticationUser(username);
            }
        }
        log.warn("disable user {},but not found in emqx", username);
        return true;
    }
}
