package com.dji.sample.control.service.impl;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.component.websocket.model.BizCodeEnum;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.control.model.dto.AuthorityExitNotify;
import com.dji.sample.control.model.dto.AuthorityOwner;
import com.dji.sample.control.model.dto.ControlAuthority;
import com.dji.sample.control.model.enums.DroneAuthorityEnum;
import com.dji.sample.control.service.IControlRedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class ControlRedisServiceImpl implements IControlRedisService {

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Override
    public AuthorityOwner setAuthorityOwner(DroneAuthorityEnum authority, String dockSn, AuthorityOwner data) {
        String key = "";
        AuthorityOwner authorityOwner = null;
        switch (authority) {
            case FLIGHT:
                key = RedisConst.FLIGHT_AUTHORITY_PREFIX + dockSn;
                RedisOpsUtils.setWithExpire(key, data, RedisConst.DRC_MODE_ALIVE_SECOND);
                break;
            case PAYLOAD:
                key = RedisConst.PAYLOAD_AUTHORITY_PREFIX + dockSn;
                RedisOpsUtils.setWithExpire(key, data, RedisConst.DRC_MODE_ALIVE_SECOND);
                break;
        }
        authorityOwner = (AuthorityOwner) RedisOpsUtils.get(key);
        if (authorityOwner == null || !authorityOwner.equals(data)) {
            String str = authorityOwner != null ? authorityOwner.toString() : null;
            log.error("setAuthorityOwner:save {},but get {}", data.toString(), str);
        }
        return authorityOwner;
    }

    @Override
    public Optional<AuthorityOwner> getAuthorityOwner(DroneAuthorityEnum authority, String dockSn) {
        switch (authority) {
            case FLIGHT:
                return Optional.ofNullable((AuthorityOwner) RedisOpsUtils.get(RedisConst.FLIGHT_AUTHORITY_PREFIX + dockSn));
            case PAYLOAD:
                return Optional.ofNullable((AuthorityOwner) RedisOpsUtils.get(RedisConst.PAYLOAD_AUTHORITY_PREFIX + dockSn));
        }
        return Optional.empty();
    }

    @Override
    public Boolean deleteAuthorityOwner(DroneAuthorityEnum authority, String dockSn) {
        switch (authority) {
            case FLIGHT:
                return RedisOpsUtils.del(RedisConst.FLIGHT_AUTHORITY_PREFIX + dockSn);
            case PAYLOAD:
                return RedisOpsUtils.del(RedisConst.PAYLOAD_AUTHORITY_PREFIX + dockSn);
        }
        return false;
    }

    @Override
    public ControlAuthority getControlAuthority(String dockSn) {
        ControlAuthority controlAuthority = ControlAuthority.builder().build();
        Optional<AuthorityOwner> flightOwner = getAuthorityOwner(DroneAuthorityEnum.FLIGHT, dockSn);
        flightOwner.ifPresent(controlAuthority::setFlightAuthority);
        Optional<AuthorityOwner> payloadOwner = getAuthorityOwner(DroneAuthorityEnum.PAYLOAD, dockSn);
        payloadOwner.ifPresent(controlAuthority::setPayloadAuthority);
        return controlAuthority;
    }

    @Override
    public Boolean clearControlAuthority(String dockSn) {
        Boolean r1 = deleteAuthorityOwner(DroneAuthorityEnum.FLIGHT, dockSn);
        Boolean r2 = deleteAuthorityOwner(DroneAuthorityEnum.PAYLOAD,dockSn);
        return r1 && r2;
    }

    @Override
    public Boolean exitControlAuthority(CustomClaim customClaim, String dockSn) {
        Optional<AuthorityOwner> flightOpt = getAuthorityOwner(DroneAuthorityEnum.FLIGHT, dockSn);
        AuthorityExitNotify authorityExitNotify = new AuthorityExitNotify();
        if (flightOpt.isPresent()) {
            AuthorityOwner owner = flightOpt.get();
            if (customClaim.getId().equals(owner.getAuthorityOwnerId())) {
                deleteAuthorityOwner(DroneAuthorityEnum.FLIGHT, dockSn);
                authorityExitNotify.setFlightAuthorityExitFromDockSn(dockSn);
                authorityExitNotify.setUsername(customClaim.getUsername());
            }
        }
        Optional<AuthorityOwner> payloadOpt = getAuthorityOwner(DroneAuthorityEnum.PAYLOAD, dockSn);
        if (payloadOpt.isPresent()) {
            AuthorityOwner owner = payloadOpt.get();
            if (customClaim.getId().equals(owner.getAuthorityOwnerId())) {
                deleteAuthorityOwner(DroneAuthorityEnum.PAYLOAD, dockSn);
                authorityExitNotify.setPayloadAuthorityExitFromDockSn(dockSn);
                authorityExitNotify.setUsername(customClaim.getUsername());
            }
        }
        if (StringUtils.isNotEmpty(authorityExitNotify.getUsername())) {
            webSocketMessageService.sendBatch(customClaim.getWorkspaceId(),BizCodeEnum.AUTHORITY_EXIT.getCode(), authorityExitNotify);
            log.debug("Sent authority exit info: {}.", authorityExitNotify.toString());
        }
        return true;
    }
}
