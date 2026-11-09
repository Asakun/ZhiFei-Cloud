package com.dji.sample.wayline.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author sean
 * @version 1.1
 * @date 2022/6/1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("wayline_job")
@Entity
@Table(name = "wayline_job")
public class WaylineJobEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Column(name = "job_id" , nullable = false, length = 45, unique = true)
    @Comment("uuid(flight id)")
    @TableField("job_id")
    private String jobId;

    @Column(name = "name" , nullable = false, length = 64)
    @Comment("The name of the job.")
    @TableField("name")
    private String name;

    @Column(name = "file_id" , nullable = false, length = 45)
    @Comment("The wayline file used for this job")
    @TableField("file_id")
    private String fileId;

    @Column(name = "dock_sn" , nullable = false, length = 45)
    @Comment("Which dock executes the job")
    @TableField("dock_sn")
    private String dockSn;

    @Column(name = "workspace_id" , nullable = false, length = 45)
    @Comment("Which workspace the current job belongs to")
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "task_type" , nullable = false)
    @Comment("0 IMMEDIATE;1 TIMED;2 CONDITIONAL")
    @TableField("task_type")
    private Integer taskType;

    @Column(name = "wayline_type" , nullable = false)
    @Comment("-1 manual;0 waypoint;1 mapping2d;2 mapping3d;3 mappingStrip")
    @TableField("wayline_type")
    private Integer waylineType;

    @Column(name = "username" , nullable = false, length = 64)
    @Comment("The name of the creator")
    @TableField("username")
    private String username;

    @Column(name = "execute_time" , nullable = false)
    @Comment("actual begin time")
    @TableField("execute_time")
    private Long executeTime;

    @Column(name = "end_time" , nullable = false)
    @Comment("planned end time")
    @TableField("end_time")
    private Long endTime;

    @Column(name = "error_code")
    @TableField("error_code")
    private Integer errorCode;

    @Column(name = "status" , nullable = false)
    @Comment("1: pending; 2: in progress; 3: success; 4: cancel; 5: failed")
    @TableField("status")
    private Integer status;

    @Column(name = "rth_altitude" , nullable = false)
    @Comment("return to home altitude. min: 20m; max: 500m")
    @TableField("rth_altitude")
    private Integer rthAltitude;

    @Column(name = "out_of_control" , nullable = false)
    @Comment("out of control action. 0: go home; 1: hover; 2: landing;")
    @TableField("out_of_control")
    private Integer outOfControl;

    @Column(name = "media_count" , nullable = false)
    @ColumnDefault("0")
    @TableField("media_count")
    private Integer mediaCount;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

    @Column(name = "begin_time" , nullable = false)
    @Comment("planned begin time")
    @TableField("begin_time")
    private Long beginTime;

    @Column(name = "completed_time" , nullable = false)
    @Comment("actual end time")
    @TableField("completed_time")
    private Long completedTime;

    @Column(name = "parent_id" , length = 45)
    @TableField("parent_id")
    private String parentId;

    @Column(name = "simulate_mission" , length = 255)
    @TableField("simulate_mission")
    private String simulateMission;

    @Column(name = "flight_distance")
    @TableField("flight_distance")
    private Double flightDistance;

    @Column(name = "flight_time")
    @TableField("flight_time")
    private Float flightTime;
}
