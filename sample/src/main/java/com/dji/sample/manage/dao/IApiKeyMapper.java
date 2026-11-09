package com.dji.sample.manage.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sample.manage.model.dto.ApiKeyDTO;
import com.dji.sample.manage.model.entity.ApiKeyEntity;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface IApiKeyMapper extends BaseMapper<ApiKeyEntity> {
    @Select("SELECT * " +
            "FROM api_key a " +
            "INNER JOIN manage_user u ON a.uid = u.user_id " +
            "where u.status = 1 " +
            "and a.is_del = 0 " +
            "and a.enable = 1")
    List<ApiKeyDTO> getValidApiKeyList();
}
