package com.dji.sample.oidc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sample.oidc.config.OidcDecoderFactory;
import com.dji.sample.oidc.dao.IOidcProviderMapper;
import com.dji.sample.oidc.model.OidcProviderEntity;
import com.dji.sample.oidc.model.dto.OidcProviderDTO;
import com.dji.sample.oidc.model.dto.OidcProviderParam;
import com.dji.sample.oidc.service.IOidcProviderService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.Pagination;
import com.dji.sdk.common.PaginationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OIDC provider configuration service implementation.
 *
 * @author dyw
 */
@Slf4j
@Service
@Transactional
public class OidcProviderServiceImpl implements IOidcProviderService {

    private static final String DEFAULT_SCOPES = "openid profile";

    private static final String DEFAULT_USERNAME_CLAIM = "preferred_username";

    private static final long DEFAULT_STATE_TTL = 120L;

    @Autowired
    private IOidcProviderMapper mapper;

    @Autowired
    private OidcDecoderFactory decoderFactory;

    @Override
    public Optional<OidcProviderEntity> getEnabledProvider(String providerId) {
        if (!StringUtils.hasText(providerId)) {
            return Optional.empty();
        }
        OidcProviderEntity entity = mapper.selectOne(new LambdaQueryWrapper<OidcProviderEntity>()
                .eq(OidcProviderEntity::getProviderId, providerId)
                .eq(OidcProviderEntity::getEnable, true)
                .eq(OidcProviderEntity::getIsDel, false));
        return Optional.ofNullable(entity);
    }

    @Override
    public PaginationData<OidcProviderDTO> getPagination(String keyword, long page, long pageSize) {
        Page<OidcProviderEntity> pageData = mapper.selectPage(
                new Page<OidcProviderEntity>(page, pageSize),
                new LambdaQueryWrapper<OidcProviderEntity>()
                        .eq(OidcProviderEntity::getIsDel, false)
                        .like(StringUtils.hasText(keyword), OidcProviderEntity::getProviderId, keyword)
                        .orderByDesc(OidcProviderEntity::getId));

        List<OidcProviderDTO> records = pageData.getRecords().stream()
                .map(this::entityConvertToDto)
                .collect(Collectors.toList());

        return new PaginationData<>(records,
                new Pagination(pageData.getCurrent(), pageData.getSize(), pageData.getTotal()));
    }

    @Override
    public Optional<OidcProviderDTO> getByProviderId(String providerId) {
        return getEntityByProviderId(providerId).map(this::entityConvertToDto);
    }

    @Override
    public HttpResultResponse save(OidcProviderParam param) {
        if (!StringUtils.hasText(param.getClientSecret())) {
            return HttpResultResponse.error("clientSecret must not be empty");
        }
        if (getEntityByProviderId(param.getProviderId()).isPresent()) {
            return HttpResultResponse.error("providerId already exists");
        }
        OidcProviderEntity entity = paramConvertToEntity(param);
        entity.setClientSecret(param.getClientSecret());
        mapper.insert(entity);
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse update(OidcProviderParam param) {
        Optional<OidcProviderEntity> entityOpt = getEntityByProviderId(param.getProviderId());
        if (entityOpt.isEmpty()) {
            return HttpResultResponse.error("provider not found");
        }
        OidcProviderEntity entity = entityOpt.get();
        entity.setClientId(param.getClientId());
        entity.setIssuer(param.getIssuer());
        entity.setAuthorizationUri(param.getAuthorizationUri());
        entity.setTokenUri(param.getTokenUri());
        entity.setRedirectUri(param.getRedirectUri());
        entity.setJwkSetUri(param.getJwkSetUri());
        entity.setPublicKey(param.getPublicKey());
        entity.setScopes(StringUtils.hasText(param.getScopes()) ? param.getScopes() : DEFAULT_SCOPES);
        entity.setUsernameClaim(StringUtils.hasText(param.getUsernameClaim())
                ? param.getUsernameClaim() : DEFAULT_USERNAME_CLAIM);
        entity.setStateTtl(param.getStateTtl() != null ? param.getStateTtl() : DEFAULT_STATE_TTL);
        if (param.getEnable() != null) {
            entity.setEnable(param.getEnable());
        }
        // An empty secret keeps the existing value.
        if (StringUtils.hasText(param.getClientSecret())) {
            entity.setClientSecret(param.getClientSecret());
        }
        mapper.updateById(entity);
        decoderFactory.evict(param.getProviderId());
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse enable(String providerId, Boolean enable) {
        if (enable == null) {
            return HttpResultResponse.error("enable must not be null");
        }
        Optional<OidcProviderEntity> entityOpt = getEntityByProviderId(providerId);
        if (entityOpt.isEmpty()) {
            return HttpResultResponse.error("provider not found");
        }
        OidcProviderEntity entity = entityOpt.get();
        entity.setEnable(enable);
        mapper.updateById(entity);
        decoderFactory.evict(providerId);
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse delete(String providerId) {
        Optional<OidcProviderEntity> entityOpt = getEntityByProviderId(providerId);
        if (entityOpt.isEmpty()) {
            return HttpResultResponse.error("provider not found");
        }
        OidcProviderEntity entity = entityOpt.get();
        entity.setIsDel(true);
        mapper.updateById(entity);
        decoderFactory.evict(providerId);
        return HttpResultResponse.success();
    }

    private Optional<OidcProviderEntity> getEntityByProviderId(String providerId) {
        if (!StringUtils.hasText(providerId)) {
            return Optional.empty();
        }
        OidcProviderEntity entity = mapper.selectOne(new LambdaQueryWrapper<OidcProviderEntity>()
                .eq(OidcProviderEntity::getProviderId, providerId)
                .eq(OidcProviderEntity::getIsDel, false));
        return Optional.ofNullable(entity);
    }

    /**
     * Entity to DTO. Never exposes the client secret.
     */
    private OidcProviderDTO entityConvertToDto(OidcProviderEntity entity) {
        return OidcProviderDTO.builder()
                .providerId(entity.getProviderId())
                .clientId(entity.getClientId())
                .hasSecret(StringUtils.hasText(entity.getClientSecret()))
                .issuer(entity.getIssuer())
                .authorizationUri(entity.getAuthorizationUri())
                .tokenUri(entity.getTokenUri())
                .redirectUri(entity.getRedirectUri())
                .jwkSetUri(entity.getJwkSetUri())
                .scopes(entity.getScopes())
                .usernameClaim(entity.getUsernameClaim())
                .stateTtl(entity.getStateTtl())
                .enable(entity.getEnable())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * Param to Entity. The client secret is set separately by the caller.
     */
    private OidcProviderEntity paramConvertToEntity(OidcProviderParam param) {
        return OidcProviderEntity.builder()
                .providerId(param.getProviderId())
                .clientId(param.getClientId())
                .issuer(param.getIssuer())
                .authorizationUri(param.getAuthorizationUri())
                .tokenUri(param.getTokenUri())
                .redirectUri(param.getRedirectUri())
                .jwkSetUri(param.getJwkSetUri())
                .publicKey(param.getPublicKey())
                .scopes(StringUtils.hasText(param.getScopes()) ? param.getScopes() : DEFAULT_SCOPES)
                .usernameClaim(StringUtils.hasText(param.getUsernameClaim())
                        ? param.getUsernameClaim() : DEFAULT_USERNAME_CLAIM)
                .stateTtl(param.getStateTtl() != null ? param.getStateTtl() : DEFAULT_STATE_TTL)
                .enable(param.getEnable() != null ? param.getEnable() : true)
                .isDel(false)
                .build();
    }
}
