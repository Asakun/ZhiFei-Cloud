package com.dji.sample.manage.service;

import com.dji.sample.manage.model.entity.RoleEntity;
import com.dji.sample.manage.model.entity.UserEntity;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface IUserDetailService extends UserDetailsService {
    boolean userSetRole(String roleCode, String userId);

    RoleEntity getRoleByUserId(String userId);

    Integer getMaxLevelByCodes(String codes);

    UserEntity getUserByUsername(String username);

    UserEntity getUserByUserId(String userId);
}
