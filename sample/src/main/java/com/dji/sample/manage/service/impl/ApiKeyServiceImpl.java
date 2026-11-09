package com.dji.sample.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.common.util.CommonUtil;
import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.manage.dao.IApiKeyMapper;
import com.dji.sample.manage.dao.IRoleMapper;
import com.dji.sample.manage.model.dto.ApiKeyDTO;
import com.dji.sample.manage.model.entity.ApiKeyEntity;
import com.dji.sample.manage.model.entity.RoleEntity;
import com.dji.sample.manage.service.IApiKeyService;
import com.dji.sdk.common.HttpResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ApiKeyServiceImpl implements IApiKeyService {

    @Autowired
    private IApiKeyMapper mapper;

    @Autowired
    private IRoleMapper roleMapper;

    private String getRedisKey(String apiKey) {
        return RedisConst.API_KEY + apiKey;
    }

    public static String generateApiKey() {
        // 32位UUID（去掉连字符）
        String uuid = UUID.randomUUID().toString().replace("-", "");
        // 16位随机字符串
        String random = CommonUtil.randomString(16);
        // 拼接返回
        return uuid + random;
    }

    private void setRedisApiKey(ApiKeyEntity apiKeyEntity, CustomClaim customClaim) {
        String redisKey = getRedisKey(apiKeyEntity.getApiKey());
        String username = customClaim.getUsername() + "@" + apiKeyEntity.getApp();
        customClaim.setUsername(username);
        RoleEntity role = roleMapper.selectOne(
                new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getRoleCode, "ROLE_SHARER"));
        customClaim.setAuthorities(role.getRoleCode());
        customClaim.setLevel(role.getLevel());
        if (apiKeyEntity.getExpireTime() != null) {
            long expire = (apiKeyEntity.getExpireTime() - System.currentTimeMillis())/1000;
            if (expire > 0) {
                RedisOpsUtils.setWithExpire(redisKey, customClaim, expire);
            }
        } else {
            RedisOpsUtils.set(redisKey, customClaim);
        }
    }

    private Boolean delRedisApiKey(String apiKey) {
        return RedisOpsUtils.del(getRedisKey(apiKey));
    }

    public Optional<ApiKeyEntity> getApiKeyByApp(String app, String uid) {
        ApiKeyEntity apiKeyEntity = mapper.selectOne(new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getApp, app)
                .eq(ApiKeyEntity::getUid, uid));
        return Optional.ofNullable(apiKeyEntity);
    }

    private ApiKeyDTO entity2Dto(ApiKeyEntity entity) {
        if (Objects.isNull(entity)) {
            return null;
        }
        return ApiKeyDTO.builder()
                .uid(entity.getUid())
                .app(entity.getApp())
                .expireTime(entity.getExpireTime())
                .enable(entity.getEnable())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    @Override
    public List<ApiKeyDTO> getApiKeysByUid(String uid) {
        List<ApiKeyEntity> apiKeyEntityList = mapper.selectList(new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getUid, uid)
                .eq(ApiKeyEntity::getIsDel, false)
                .orderByDesc(ApiKeyEntity::getCreateTime));
        return apiKeyEntityList.stream().map(this::entity2Dto).collect(Collectors.toList());
    }

    @Override
    public HttpResultResponse addApiKey(CustomClaim customClaim, String app, Long expireTime) {
        Optional<ApiKeyEntity> apiKeyEntityOpt = getApiKeyByApp(app, customClaim.getId());
        if (apiKeyEntityOpt.isPresent()) {
            return HttpResultResponse.error("app name already exist");
        }
        String key = generateApiKey();
        ApiKeyEntity apiKeyEntity = ApiKeyEntity.builder().app(app).apiKey(key).uid(customClaim.getId())
                .expireTime(expireTime).build();
        boolean insert = mapper.insert(apiKeyEntity) > 0;
        if (insert) {
            setRedisApiKey(apiKeyEntity, customClaim);
            return HttpResultResponse.success(apiKeyEntity);
        } else {
            return HttpResultResponse.error("app insert failed");
        }
    }

    @Override
    public HttpResultResponse deleteApiKey(CustomClaim customClaim, String app) {
        Optional<ApiKeyEntity> apiKeyEntityOpt = getApiKeyByApp(app, customClaim.getId());
        if (apiKeyEntityOpt.isPresent()) {
            ApiKeyEntity apiKeyEntity = apiKeyEntityOpt.get();
            delRedisApiKey(apiKeyEntity.getApiKey());
            apiKeyEntity.setIsDel(true);
            boolean update = mapper.updateById(apiKeyEntity) > 0;
            if (!update) {
                return HttpResultResponse.error("app delete failed");
            }
        } else {
            return HttpResultResponse.error("app does not exist");
        }
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse enableApiKey(CustomClaim customClaim, Boolean enable, String app) {
        Optional<ApiKeyEntity> apiKeyEntityOpt = getApiKeyByApp(app, customClaim.getId());
        if (apiKeyEntityOpt.isPresent()) {
            ApiKeyEntity apiKeyEntity = apiKeyEntityOpt.get();
            apiKeyEntity.setEnable(enable);
            boolean update = mapper.updateById(apiKeyEntity) > 0;
            if (!update) {
                return HttpResultResponse.error("app update failed");
            }
            if (enable) {
                setRedisApiKey(apiKeyEntity, customClaim);
            } else {
                delRedisApiKey(apiKeyEntity.getApiKey());
            }
        } else {
            return HttpResultResponse.error("app does not exist");
        }
        return HttpResultResponse.success();
    }

    private ApiKeyEntity dto2entity(ApiKeyDTO dto) {
        if (Objects.isNull(dto)) {
            return null;
        }
        return ApiKeyEntity.builder()
                .uid(dto.getUid())
                .app(dto.getApp())
                .expireTime(dto.getExpireTime())
                .enable(dto.getEnable())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    @Override
    public Integer syncApiKey() {
        RedisOpsUtils.deleteKeysByPrefix(RedisConst.API_KEY);
        List<ApiKeyDTO> apiKeyEntityList = mapper.getValidApiKeyList();
        for (ApiKeyDTO apiKeyDTO : apiKeyEntityList) {
            ApiKeyEntity apiKeyEntity = dto2entity(apiKeyDTO);
            assert apiKeyEntity != null;
            apiKeyEntity.setApiKey(apiKeyDTO.getApiKey());
            CustomClaim customClaim = new CustomClaim();
            customClaim.setId(apiKeyDTO.getUid());
            customClaim.setUsername(apiKeyDTO.getUsername());
            customClaim.setUserType(apiKeyDTO.getUserType());
            customClaim.setWorkspaceId(apiKeyDTO.getWorkspaceId());
            setRedisApiKey(apiKeyEntity, customClaim);
        }
        return apiKeyEntityList.size();
    }
}
