package com.dji.sample.map.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author sean
 * @version 1.9
 * @date 2023/11/23
 */
@TableName("device_flight_area")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "device_flight_area")
public class DeviceFlightAreaEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "device_sn" , nullable = false, length = 64)
    @TableField("device_sn")
    private String deviceSn;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "file_id" , nullable = false, length = 64)
    @TableField("file_id")
    private String fileId;

    @Column(name = "sync_status" , nullable = false, length = 32)
    @TableField("sync_status")
    private String syncStatus;

    @Column(name = "sync_code" , nullable = false)
    @ColumnDefault("0")
    @TableField("sync_code")
    private Integer syncCode;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}
