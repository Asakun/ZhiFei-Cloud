package com.dji.sample.proxy.service;

import com.dji.sample.proxy.model.EmqxUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.List;

public interface IEmqxService {
    ArrayNode getClientList(String clientIds) throws JsonProcessingException;

    boolean kickClients(ArrayNode clientIds) throws JsonProcessingException;

    boolean addAuthenticationUser(EmqxUser userData) throws JsonProcessingException;

    /**
     * 向全局认证链上的指定认证器添加用户数据。
     * 对应 EMQX API: POST /api/v5/authentication/{id}/users
     */
    boolean addAuthenticationUser(String authenticatorId, EmqxUser userData) throws JsonProcessingException;

    JsonNode getAuthentications() throws JsonProcessingException;

    List<String> listAuthenticationUsers(Integer page, Integer limit, String likeUserId, Boolean isSuperuser) throws JsonProcessingException;

    /**
     * 查询全局认证链指定认证器下的用户列表。
     * 对应 EMQX API: GET /api/v5/authentication/{id}/users
     */
    List<String> listAuthenticationUsers(String authenticatorId, Integer page, Integer limit, String likeUserId, Boolean isSuperuser) throws JsonProcessingException;

    boolean deleteAuthenticationUser(String userId) throws JsonProcessingException;

    /**
     * 删除全局认证链指定认证器下的用户。
     * 对应 EMQX API: DELETE /api/v5/authentication/{id}/users/{user_id}
     */
    boolean deleteAuthenticationUser(String authenticatorId, String userId) throws JsonProcessingException;

    EmqxUser registerEmqxUser(String username, String password) throws JsonProcessingException;

    Boolean closeEmqxUser(String username) throws JsonProcessingException;

    /**
     * 确保 EMQX 认证账号存在：查询该账号（id 为 pilot_{username}），不存在则创建。
     * 通常用于初始化时创建 MQTT BASIC / DRC 账号。
     * @param username 账号用户名
     * @param password 账号密码
     * @return true 表示账号已存在或创建成功；false 表示创建失败
     */
    boolean ensureEmqxAccount(String username, String password, Boolean isSuperuser) throws JsonProcessingException;

    /**
     * 确保 EMQX 中存在 JWT 认证器：从 MQTT password 字段中取 JWT 并用项目 jwt secret 验证。
     * 不存在则创建该认证器。通常用于初始化。
     * @return true 表示已存在或创建成功；false 表示创建失败
     */
    boolean ensureJwtAuthenticator() throws JsonProcessingException;
}
