package com.dji.sample.manage.dao;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sample.manage.model.dto.UserDTO;
import com.dji.sample.manage.model.dto.UserListDTO;
import com.dji.sample.manage.model.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface IUserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * " +
            "FROM manage_user u " +
            "LEFT JOIN manage_user_role ur ON ur.user_id = u.user_id " +
            "LEFT JOIN manage_role r ON r.role_code = ur.role_code " +
            "${ew.customSqlSegment}")
    Page<UserListDTO> getUsersByWorkspaceIdPage(Page<?> page, @Param(Constants.WRAPPER) Wrapper<UserDTO> ew);

    @Select("SELECT * " +
            "FROM manage_user u " +
            "LEFT JOIN manage_user_role ur ON ur.user_id = u.user_id " +
            "LEFT JOIN manage_role r ON r.role_code = ur.role_code " +
            "${ew.customSqlSegment}" +
            "limit 1")
    UserEntity getUserByWorkspaceId(@Param(Constants.WRAPPER) Wrapper<UserDTO> ew);
}
