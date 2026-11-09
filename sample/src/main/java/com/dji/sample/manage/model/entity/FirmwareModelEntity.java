package com.dji.sample.manage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author sean
 * @version 1.3
 * @date 2022/12/21
 */
@Data
@TableName("manage_firmware_model")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "manage_firmware_model")
public class FirmwareModelEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "firmware_id" , nullable = false, length = 64)
    @TableField("firmware_id")
    private String firmwareId;

    @Column(name = "device_name" , nullable = false, length = 64)
    @Comment("model of the device. This parameter corresponds to the device name in the device dictionary table")
    @TableField("device_name")
    private String deviceName;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}
