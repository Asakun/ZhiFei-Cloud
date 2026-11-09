package com.dji.sample.manage.dao;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sample.manage.model.dto.WorkspaceDTO;
import com.dji.sample.manage.model.entity.WorkspaceEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface IWorkspaceMapper extends BaseMapper<WorkspaceEntity> {
    @Select("SELECT * " +
            "FROM manage_workspace w " +
            "${ew.customSqlSegment}")
    Page<WorkspaceDTO> getWorkspacePageList(Page<?> page, @Param(Constants.WRAPPER) Wrapper<WorkspaceDTO> ew);
}
