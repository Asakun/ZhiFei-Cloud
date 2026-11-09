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
 * The entity class of the device
 *
 * @author sean.zhou
 * @version 0.1
 * @date 2021/11/10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "manage_device")
@Entity
@Table(name = "manage_device")
public class DeviceEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "device_sn" , nullable = false, length = 32, unique = true)
    @Comment("dock, drone, remote control")
    @TableField(value = "device_sn")
    private String deviceSn;

    @Column(name = "device_name" , nullable = false, length = 64)
    @ColumnDefault("undefined")
    @Comment("model of the device. This parameter corresponds to the device name in the device dictionary table")
    @TableField(value = "device_name")
    private String deviceName;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @Comment("The workspace to which the current device belongs")
    @TableField(value = "workspace_id")
    private String workspaceId;

    @Column(name = "device_type" , nullable = false)
    @ColumnDefault("-1")
    @Comment("This parameter corresponds to the device type in the device dictionary table")
    @TableField(value = "device_type")
    private Integer deviceType;

    @Column(name = "sub_type" , nullable = false)
    @ColumnDefault("-1")
    @Comment("This parameter corresponds to the sub type in the device dictionary table")
    @TableField(value = "sub_type")
    private Integer subType;

    @Column(name = "domain" , nullable = false)
    @ColumnDefault("-1")
    @Comment("This parameter corresponds to the domain in the device dictionary table")
    @TableField(value = "domain")
    private Integer domain;

    @Column(name = "version" , nullable = false, length = 32)
    @Comment("version of the protocol. This field is currently not useful")
    @TableField(value = "version")
    private String version;

    @Column(name = "device_index" , nullable = false, length = 32)
    @Comment("Control of the drone, A control or B control")
    @TableField(value = "device_index")
    private String deviceIndex;

    @Column(name = "child_sn" , nullable = false, length = 32)
    @Comment("The device controlled by the gateway")
    @TableField(value = "child_sn")
    private String childSn;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

    @Column(name = "device_desc" , nullable = false, length = 100)
    @TableField(value = "device_desc")
    private String deviceDesc;

    @Column(name = "url_normal" , nullable = false, length = 200)
    @Comment("The icon displayed on the remote control")
    @TableField(value = "url_normal")
    private String urlNormal;

    @Column(name = "url_select" , nullable = false, length = 200)
    @Comment("The icon displayed on the remote control when it is selected")
    @TableField(value = "url_select")
    private String urlSelect;

    @Column(name = "user_id" , nullable = false, length = 64)
    @Comment("The account used when the device was bound")
    @TableField(value = "user_id")
    private String userId;

    @Column(name = "nickname" , nullable = false, length = 64)
    @Comment("custom name of the device")
    @TableField(value = "nickname")
    private String nickname;

    @Column(name = "firmware_version" , nullable = false, length = 32)
    @Comment("firmware version of the device")
    @TableField(value = "firmware_version")
    private String firmwareVersion;

    @Column(name = "compatible_status" , nullable = false)
    @ColumnDefault("1")
    @Comment("1: consistent; 0: inconsistent; Whether the firmware versions are consistent")
    @TableField(value = "compatible_status")
    private Boolean compatibleStatus;

    @Column(name = "bound_status" , nullable = false)
    @ColumnDefault("0")
    @Comment("The status when the device is bound to the workspace. 1: bound; 0: not bound;")
    @TableField(value = "bound_status")
    private Boolean boundStatus;

    @Column(name = "bound_time")
    @Comment("The time when the device is bound to the workspace")
    @TableField(value = "bound_time")
    private Long boundTime;

    @Column(name = "login_time")
    @Comment("The time of the last device login")
    @TableField(value = "login_time")
    private Long loginTime;

    @Column(name = "insurance_expired")
    @Comment("The time of the insurance expired")
    @TableField(value = "insurance_expired")
    private Long insuranceExpired;

}