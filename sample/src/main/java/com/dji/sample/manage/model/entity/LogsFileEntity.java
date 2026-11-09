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
@TableName("logs_file")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "logs_file")
public class LogsFileEntity implements Serializable {

    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "file_id" , nullable = false, length = 45, unique = true)
    @Comment("uuid")
    @TableField("file_id")
    private String fileId;

    @Column(name = "name" , nullable = false, length = 100)
    @Comment("The name of the file in the bucket.")
    @TableField("name")
    private String name;

    @Column(name = "size", nullable = false)
    @ColumnDefault("0")
    @Comment("file size")
    @TableField("size")
    private Long size;

    @Column(name = "logs_id" , nullable = false, length = 45)
    @Comment("The logs_id in the manage_device_logs table.")
    @TableField("logs_id")
    private String logsId;

    @Column(name = "device_sn" , nullable = false, length = 45)
    @Comment("The sn of the device.")
    @TableField("device_sn")
    private String deviceSn;

    @Column(name = "fingerprint" , nullable = false, length = 64)
    @Comment("file fingerprint")
    @TableField("fingerprint")
    private String fingerprint;

    @Column(name = "object_key" , nullable = false, length = 1000)
    @Comment("The key of the file in the bucket.")
    @TableField("object_key")
    private String objectKey;

    @Column(name = "status", nullable = false)
    @Comment("Whether the upload was successful. 1: success; 0: failed;")
    @TableField("status")
    private Boolean status;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

}
