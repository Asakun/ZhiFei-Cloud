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
 * @date 2022/8/16
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("manage_device_firmware")
@Entity
@Table(name = "manage_device_firmware")
public class DeviceFirmwareEntity implements Serializable {

    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "firmware_id" , nullable = false, length = 45, unique = true)
    @Comment("uuid")
    @TableField("firmware_id")
    private String firmwareId;

    @Column(name = "file_name" , nullable = false, length = 64)
    @ColumnDefault("undefined")
    @Comment("The file name of the firmware package, including the file suffix")
    @TableField("file_name")
    private String fileName;

    @Column(name = "firmware_version" , nullable = false, length = 45)
    @Comment("It needs to be formatted according to the official firmware version. 00.00.0000")
    @TableField("firmware_version")
    private String firmwareVersion;

    @Column(name = "object_key" , nullable = false, length = 200)
    @Comment("The object key of the firmware package in the bucket")
    @TableField("object_key")
    private String objectKey;

    @Column(name = "file_size" , nullable = false)
    @Comment("The size of the firmware package")
    @TableField("file_size")
    private Long fileSize;

    @Column(name = "file_md5" , nullable = false, length = 45)
    @Comment("The md5 of the firmware package")
    @TableField("file_md5")
    private String fileMd5;

    @Transient
    @TableField(exist = false)
    private String deviceName;

    @Column(name = "release_note" , nullable = false, length = 1000)
    @Comment("The release note of the firmware package")
    @TableField("release_note")
    private String releaseNote;

    @Column(name = "release_date" , nullable = false)
    @Comment("The release date of the firmware package")
    @TableField("release_date")
    private Long releaseDate;

    @Column(name = "status" , nullable = false)
    @ColumnDefault("0")
    @Comment("Availability of the firmware package. 1: available; 0: unavailable")
    @TableField("status")
    private Boolean status;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "user_name" , nullable = false, length = 64)
    @Comment("The name of the creator")
    @TableField("user_name")
    private String username;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

}
