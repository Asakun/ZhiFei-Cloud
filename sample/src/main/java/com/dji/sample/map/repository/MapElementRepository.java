package com.dji.sample.map.repository;

import com.dji.sample.map.model.entity.GroupElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface MapElementRepository extends JpaRepository<GroupElementEntity, Long> {
    @Query(nativeQuery=true, value =
            "SELECT e.element_id elementId," +
                    "e.element_name elementName," +
                    "e.group_id groupId," +
                    "e.element_type elementType," +
                    "e.username," +
                    "e.color," +
                    "e.clamp_to_ground clampToGround," +
                    "e.create_time createTime," +
                    "e.update_time updateTime," +
                    "e.related_system relatedSystem," +
                    "e.related_id relatedId," +
                    "GROUP_CONCAT(c.latitude) latitudes," +
                    "GROUP_CONCAT(c.longitude) longitudes," +
                    "GROUP_CONCAT(c.altitude) altitudes " +
            "FROM ( " +
                    "SELECT * FROM map_group_element e " +
                    "WHERE group_id = :groupId " +
                    "ORDER BY e.create_time DESC " +
                    "LIMIT :offset, :pageSize " +
            ") e " +
            "INNER JOIN map_element_coordinate c ON c.element_id = e.element_id " +
            "GROUP BY e.element_id ")
    List<Map<String, Object>> findMapElementDTOList(@Param("groupId") String groupId,
                                                    @Param("offset") Long offset,
                                                    @Param("pageSize") Long pageSize);
}
