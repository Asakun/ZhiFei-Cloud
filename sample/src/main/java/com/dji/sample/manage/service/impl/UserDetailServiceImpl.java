package com.dji.sample.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dji.sample.manage.dao.IRoleMapper;
import com.dji.sample.manage.dao.IUserMapper;
import com.dji.sample.manage.dao.IUserRoleMapper;
import com.dji.sample.manage.model.entity.RoleEntity;
import com.dji.sample.manage.model.entity.UserEntity;
import com.dji.sample.manage.model.entity.UserRoleEntity;
import com.dji.sample.manage.service.IUserDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserDetailServiceImpl implements IUserDetailService {

    @Autowired
    private IUserMapper mapper;

    @Autowired
    private IRoleMapper roleMapper;

    @Autowired
    private IUserRoleMapper userRoleMapper;

    @Override
    public boolean userSetRole(String roleCode, String userId) {
        int count = userRoleMapper.insert(UserRoleEntity.builder().roleCode(roleCode).userId(userId).build());
        return count > 0;
    }

    @Override
    public RoleEntity getRoleByUserId(String userId) {
        //当前user role 1:1映射
        UserRoleEntity userRoleEntity = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId));
        return roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, userRoleEntity.getRoleCode()));
    }

    @Override
    public Integer getMaxLevelByCodes(String codes) {
        List<String> codeList = Arrays.asList(codes.split(","));
        return (Integer) roleMapper.selectObjs(new QueryWrapper<RoleEntity>().select("MAX(level) as max_level")
                .in("role_code", codeList)).stream().findFirst().orElse(0);
    }

    public List<UserRoleEntity> selectUserRoleByUserId(String userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId));
    }

    /**
     * Query a user by username.
     * @param username
     * @return
     */
    @Override
    public UserEntity getUserByUsername(String username) {
        return mapper.selectOne(new QueryWrapper<UserEntity>()
                .eq("username", username));
    }

    @Override
    public UserEntity getUserByUserId(String userId) {
        return mapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUserId, userId));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = this.getUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户名不存在");
        }

        // 2. 查询用户角色（转换为 Spring Security 认可的权限格式）
        List<UserRoleEntity> roles = selectUserRoleByUserId(user.getUserId());
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getRoleCode())) // 角色编码必须以 ROLE_ 开头
                .collect(Collectors.toList());

        // 3. 返回 UserDetails 对象（包含用户名、加密后的密码、角色权限）
        return User.withUsername(user.getUsername())
                .password(user.getPassword()) // 数据库中已加密的密码，Spring Security 会自动校验
                .accountLocked(user.getStatus() == -1) // 状态为 0 则锁定
                .accountExpired(false)
                .credentialsExpired(false)
                .disabled(user.getStatus() == 0)
                .authorities(authorities)
                .build();
    }

}
