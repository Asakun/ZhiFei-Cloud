package com.dji.sample.wayline.repository;


import com.dji.sample.wayline.model.entity.WaylineJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface WaylineJobRepository extends JpaRepository<WaylineJobEntity, Integer> {

    @Query(nativeQuery=true, value = 
            "SELECT " +
            "    COUNT(id) AS total_count, " +
            "    IFNULL(SUM(flight_distance), 0) AS total_distance, " +
            "    IFNULL(SUM(flight_time), 0) AS total_time " +
            "FROM " +
            "    wayline_job " +
            "WHERE " +
            "    workspace_id = :workspaceId" +
            "    AND completed_time >= :startTime " +
            "    AND completed_time <= :endTime ")
    Map<String, Object> getWaylineJobSum(@Param("startTime") Long startTime, @Param("endTime") Long endTime,
                                         @Param("workspaceId") String workspaceId);

    @Query(nativeQuery=true, value =
            "SELECT " +
            "    DATE(FROM_UNIXTIME(completed_time/1000)) AS stat_date, " +
            "    COUNT(id) AS total_count, " +
            "    IFNULL(SUM(flight_distance), 0) AS total_distance, " +
            "    IFNULL(SUM(flight_time), 0) AS total_time " +
            "FROM " +
            "    wayline_job " +
            "WHERE " +
            "    workspace_id = :workspaceId" +
            "    AND completed_time >= :startTime " +
            "    AND completed_time <= :endTime " +
            "GROUP BY " +
            "    DATE(FROM_UNIXTIME(completed_time/1000)) " +
            "ORDER BY " +
            "    stat_date ")
    List<Map<String, Object>> getWaylineJobSumByDate(@Param("startTime") Long startTime, @Param("endTime") Long endTime,
                                                     @Param("workspaceId") String workspaceId);
}
