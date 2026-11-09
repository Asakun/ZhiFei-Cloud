package com.dji.sample.manage.model.entity;

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
 * @version 1.2
 * @date 2022/9/7
 */
@TableName("manage_device_logs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "manage_device_logs")
public class DeviceLogsEntity implements Serializable {

    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "logs_id" , nullable = false, length = 45, unique = true)
    @Comment("uuid")
    @TableField("logs_id")
    private String logsId;

    @Column(name = "username" , nullable = false, length = 100)
    @Comment("The name of the creator")
    @TableField("username")
    private String username;

    @Column(name = "logs_info" , nullable = false, length = 1000)
    @ColumnDefault("")
    @Comment("A description of the log issue")
    @TableField("logs_info")
    private String logsInfo;

    @Column(name = "device_sn" , nullable = false, length = 45)
    @Comment("The sn of the device")
    @TableField("device_sn")
    private String deviceSn;

    @Column(name = "happen_time")
    @Comment("The time when the logging problem occurred")
    @TableField("happen_time")
    private Long happenTime;

    @Column(name = "status" , nullable = false)
    @Comment("1: uploading; 2: done 3: canceled; 4: failed;")
    @TableField("status")
    private Integer status;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

}
