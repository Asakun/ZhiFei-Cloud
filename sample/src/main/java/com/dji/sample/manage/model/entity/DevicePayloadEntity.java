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
 * @author sean.zhou
 * @date 2021/11/19
 * @version 0.1
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "manage_device_payload")
@Entity
@Table(name = "manage_device_payload")
public class DevicePayloadEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "payload_sn" , nullable = false, length = 32, unique = true)
    @Comment("The sn of the device payload")
    @TableField(value = "payload_sn")
    private String payloadSn;

    @Column(name = "payload_name" , nullable = false, length = 64)
    @ColumnDefault("undefined")
    @Comment("model of the payload. This parameter corresponds to the device name in the device dictionary table")
    @TableField(value = "payload_name")
    private String payloadName;

    @Column(name = "payload_type" , nullable = false)
    @Comment("This parameter corresponds to the device type in the device dictionary table")
    @TableField(value = "payload_type")
    private Integer payloadType;

    @Column(name = "sub_type" , nullable = false)
    @Comment("This parameter corresponds to the sub type in the device dictionary table")
    @TableField(value = "sub_type")
    private Integer subType;

    @Column(name = "firmware_version" , length = 32)
    @Comment("firmware version of the device payload")
    @TableField(value = "firmware_version")
    private String firmwareVersion;

    @Column(name = "payload_index" , nullable = false)
    @Comment("The location of the payload on the device")
    @TableField(value = "payload_index")
    private Integer payloadIndex;

    @Column(name = "device_sn" , nullable = false, length = 32)
    @Comment("Which device the current payload belongs to")
    @TableField(value = "device_sn")
    private String deviceSn;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

    @Column(name = "payload_desc" , length = 100)
    @TableField(value = "payload_desc")
    private String payloadDesc;

    @Column(name = "control_source" , length = 1)
    @TableField(value = "control_source")
    private String controlSource;

}