package com.dji.sample.manage.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.common.util.JwtUtil;
import com.dji.sample.component.mqtt.config.MqttPropertyConfiguration;
import com.dji.sample.configuration.SecurityConfig;
import com.dji.sample.manage.dao.IUserMapper;
import com.dji.sample.manage.model.dto.*;
import com.dji.sample.manage.model.entity.RoleEntity;
import com.dji.sample.manage.model.entity.UserEntity;
import com.dji.sample.manage.model.enums.UserStatusEnum;
import com.dji.sample.manage.model.enums.UserTypeEnum;
import com.dji.sample.manage.service.IUserDetailService;
import com.dji.sample.manage.service.IUserService;
import com.dji.sample.manage.service.IWorkspaceService;
import com.dji.sample.proxy.model.EmqxUser;
import com.dji.sample.proxy.service.IEmqxService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.Pagination;
import com.dji.sdk.common.PaginationData;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl implements IUserService {

    @Autowired
    private IUserMapper mapper;

    @Autowired
    private MqttPropertyConfiguration mqttPropertyConfiguration;

    @Autowired
    private IWorkspaceService workspaceService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private IUserDetailService userDetailService;

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private IEmqxService emqxService;

    @Override
    public HttpResultResponse getUserByUsername(String username, String workspaceId) {

        UserEntity userEntity = userDetailService.getUserByUsername(username);
        if (userEntity == null) {
            return new HttpResultResponse()
                    .setCode(HttpStatus.UNAUTHORIZED.value())
                    .setMessage("invalid username");
        }

        UserDTO user = this.entityConvertToDTO(userEntity);
        user.setWorkspaceId(workspaceId);

        RoleEntity role = userDetailService.getRoleByUserId(user.getUserId());
        user.setRoleName(role.getRoleName());
        user.setRoleCode(role.getRoleCode());
        return HttpResultResponse.success(user);
    }

    @Override
    public HttpResultResponse userLogin(String username, String password, Integer flag) {
        // check user
        UserEntity userEntity = userDetailService.getUserByUsername(username);
        if (userEntity == null) {
            return new HttpResultResponse()
                    .setCode(HttpStatus.UNAUTHORIZED.value())
                    .setMessage("invalid username");
        }
        if (flag.intValue() != userEntity.getUserType().intValue()) {
            return HttpResultResponse.error("The account type does not match.");
        }
        String authorities;
        try {
            Authentication authResult = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            authorities = authResult.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
        } catch (AuthenticationException e) {
            return new HttpResultResponse()
                    .setCode(HttpStatus.UNAUTHORIZED.value())
                    .setData(e.getMessage())
                    .setMessage("invalid password");
        }

        Optional<WorkspaceDTO> workspaceOpt = workspaceService.getWorkspaceByWorkspaceId(userEntity.getWorkspaceId());
        if (workspaceOpt.isEmpty()) {
            return new HttpResultResponse()
                    .setCode(HttpStatus.UNAUTHORIZED.value())
                    .setMessage("invalid workspace id");
        }
        Integer level = userDetailService.getMaxLevelByCodes(authorities);
        CustomClaim customClaim = new CustomClaim(userEntity.getUserId(),
                userEntity.getUsername(), userEntity.getUserType(),
                workspaceOpt.get().getWorkspaceId(), authorities, level);

        // create token
        String token = JwtUtil.createToken(customClaim.convertToMap());

        UserDTO userDTO = entityConvertToDTO(userEntity);
        userDTO.setMqttAddr(MqttPropertyConfiguration.getBasicMqttAddress());
        userDTO.setAccessToken(token);
        userDTO.setWorkspaceId(workspaceOpt.get().getWorkspaceId());
        return HttpResultResponse.success(userDTO);
    }

    @Override
    public Optional<UserDTO> refreshToken(String token) {
        return refreshToken(token, null);
    }

    @Override
    public Optional<UserDTO> refreshToken(String token, String workspaceId) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        CustomClaim customClaim;
        try {
            DecodedJWT jwt = JwtUtil.verifyToken(token);
            customClaim = new CustomClaim(jwt.getClaims());
        } catch (TokenExpiredException e) {
            customClaim = new CustomClaim(JWT.decode(token).getClaims());
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
        if (StringUtils.hasText(workspaceId)) {customClaim.setWorkspaceId(workspaceId);}
        String refreshToken = JwtUtil.createToken(customClaim.convertToMap());

        UserDTO user = entityConvertToDTO(userDetailService.getUserByUsername(customClaim.getUsername()));
        if (Objects.isNull(user)) {
            return Optional.empty();
        }
        user.setWorkspaceId(customClaim.getWorkspaceId());
        user.setAccessToken(refreshToken);
        return Optional.of(user);
    }

    private String getValidWorkspaceId(CustomClaim customClaim, UserLoginDTO user) {
        String workspaceId = customClaim.getWorkspaceId();
        if (customClaim.getLevel() >= 5 && StringUtils.hasText(user.getWorkspaceId())) {
            Optional<WorkspaceDTO> workspaceDTO = workspaceService.getWorkspaceByIdLike(user.getWorkspaceId());
            if (workspaceDTO.isPresent()) {
                workspaceId = workspaceDTO.get().getWorkspaceId();
            }
        }
        return workspaceId;
    }

    @Override
    public HttpResultResponse userRegister(CustomClaim customClaim, UserLoginDTO user) throws JsonProcessingException {
        EmqxUser emqxUser = new EmqxUser();
        if (user.getFlag() == UserTypeEnum.PILOT) {
            emqxUser = emqxService.registerEmqxUser(user.getUsername(),null);
        }
        if (user.getUsername().contains("@")) {
            return HttpResultResponse.error("Username can not contains @");
        }
        UserEntity userEntity = userDetailService.getUserByUsername(user.getUsername());
        if (userEntity != null) {
            return HttpResultResponse.error("Username is already taken.");
        }
        RoleEntity role = userDetailService.getRoleByUserId(customClaim.getId());
        String uid = UUID.randomUUID().toString();
        String workspaceId = getValidWorkspaceId(customClaim, user);
        UserEntity newUser = UserEntity.builder()
                .userId(uid)
                .workspaceId(workspaceId)
                .userType(user.getFlag().getVal())
                .username(user.getUsername())
                .password(securityConfig.passwordEncoder().encode(user.getPassword()))
                .mqttUsername(emqxUser.getUserId())
                .mqttPassword(emqxUser.getPassword())
                .status(0)
                .updator(customClaim.getUsername())
                .build();
        mapper.insert(newUser);
        userDetailService.userSetRole(role.getChildRole(), uid);
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse resetPassword(String username, UserPasswordDTO passwordDTO) {
        UserEntity userEntity = userDetailService.getUserByUsername(username);
        int count = 0;
        if (userEntity != null) {
            try {
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, passwordDTO.getOldPassword()));
            } catch (AuthenticationException e) {
                return new HttpResultResponse()
                        .setCode(HttpStatus.UNAUTHORIZED.value())
                        .setData(e.getMessage())
                        .setMessage("invalid old password");
            }
            userEntity.setPassword(securityConfig.passwordEncoder().encode(passwordDTO.getNewPassword()));
            count = mapper.updateById(userEntity);
        }
        if (count > 0) {
            return HttpResultResponse.success();
        }
        return HttpResultResponse.error("not found username");
    }

    @Override
    public HttpResultResponse initPassword(String userId, String password) {
        UserEntity userEntity = userDetailService.getUserByUserId(userId);
        int count = 0;
        if (userEntity != null) {
            userEntity.setPassword(securityConfig.passwordEncoder().encode(password));
            count = mapper.updateById(userEntity);
        }
        if (count > 0) {
            return HttpResultResponse.success();
        }
        return HttpResultResponse.error("not found user");
    }

    @Override
    public PaginationData<UserListDTO> getUsersByWorkspaceId(long page, long pageSize, CustomClaim customClaim) {
        Integer level = 3;  //能看到组内
        Integer status = 0;
        if (customClaim.getLevel() > 3) {
            level = customClaim.getLevel();
            status = null;
        }
        Page<UserListDTO> userPage = mapper.getUsersByWorkspaceIdPage(
                new Page<>(page, pageSize),
                new QueryWrapper<UserDTO>().eq("workspace_id", customClaim.getWorkspaceId())
                        .le(customClaim.getLevel() != null, "r.level", level)
                        .ge(status != null, "u.status", status)
                        .orderByDesc("create_time"));

        return new PaginationData<>(userPage.getRecords(), new Pagination(userPage.getCurrent(), userPage.getSize(), userPage.getTotal()));
    }

    @Override
    public Boolean updateUser(String workspaceId, String userId, UserListDTO user) {
        UserEntity userEntity = mapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUserId, userId));
        if (userEntity == null) {
            return false;
        }
        userEntity.setWorkspaceId(workspaceId);
        if (user != null && StringUtils.hasText(user.getRemark())) {
            userEntity.setRemark(user.getRemark());
        }
        userEntity.setUpdateTime(System.currentTimeMillis());
        int id = mapper.update(userEntity, new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getUserId, userId));

        return id > 0;
    }

    @Override
    public Boolean updateUserStatus(CustomClaim customClaim, String userId, UserStatusEnum status) throws JsonProcessingException {
        UserEntity userEntity = mapper.getUserByWorkspaceId(
                new QueryWrapper<UserDTO>()
                        .eq("u.user_id", userId)
                        .eq("workspace_id", customClaim.getWorkspaceId())
                        .le(customClaim.getLevel() != null, "r.level", customClaim.getLevel()));
        if (userEntity == null) {
            return false;
        }
        if (status == UserStatusEnum.DISABLE && StringUtils.hasText(userEntity.getMqttUsername())) {
            emqxService.closeEmqxUser(userEntity.getMqttUsername());
            userEntity.setMqttUsername("");
            userEntity.setMqttPassword("");
        }
        userEntity.setStatus(status.getVal());
        userEntity.setUpdator(customClaim.getUsername());
        int id = mapper.update(userEntity, new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getUserId, userId)
                .eq(UserEntity::getWorkspaceId, customClaim.getWorkspaceId()));

        return id > 0;
    }

    /**
     * Convert database entity objects into user data transfer object.
     * @param entity
     * @return
     */
    private UserDTO entityConvertToDTO(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserDTO.builder()
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .userType(entity.getUserType())
                .mqttUsername(entity.getMqttUsername())
                .mqttPassword(entity.getMqttPassword())
                .mqttAddr(MqttPropertyConfiguration.getBasicMqttAddress())
                .status(entity.getStatus())
                .build();
    }


}
