package com.dji.sample.wayline.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sample.wayline.model.entity.WaylineJobEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @author sean
 * @version 1.1
 * @date 2022/6/1
 */
public interface IWaylineJobMapper extends BaseMapper<WaylineJobEntity> {

    @Select({
            "SELECT a.*, nickname FROM ( ",
            "    SELECT ",
            "        dock_sn, " +
            "        username, " +
            "        COUNT(id) AS total_count, ",
            "        IFNULL(SUM(flight_distance), 0) AS total_distance, ",
            "        IFNULL(SUM(flight_time), 0) AS total_time ",
            "    FROM wayline_job ",
            "    WHERE workspace_id = #{workspaceId} ",
            "      AND completed_time >= #{startTime} ",
            "      AND completed_time <= #{endTime} ",
            "    GROUP BY ${groupBy} ", // 动态拼接GROUP BY
            ") a ",
            "LEFT JOIN manage_device ON device_sn = dock_sn ", // 动态拼接关联字段
            "ORDER BY ${sort} DESC" // 动态拼接排序字段
    })
    List<Map<String, Object>> getWaylineJobRank(
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("groupBy") String groupBy,
            @Param("sort") String sort,
            @Param("workspaceId") String workspaceId
    );
}
