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
 * @version 0.3
 * @date 2021/12/22
 */
@Data
@TableName("wayline_file")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "wayline_file")
public class WaylineFileEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "name" , nullable = false, length = 64)
    @Comment("wayline name")
    @TableField("name")
    private String name;

    @Column(name = "wayline_id" , nullable = false, length = 64, unique = true)
    @Comment("uuid")
    @TableField("wayline_id")
    private String waylineId;

    @Column(name = "drone_model_key" , nullable = false, length = 32)
    @Comment("device product enum. format: domain-device_type-sub_type")
    @TableField("drone_model_key")
    private String droneModelKey;

    @Column(name = "payload_model_keys" , nullable = false, length = 200)
    @Comment("payload product enum. format: domain-device_type-sub_type")
    @TableField("payload_model_keys")
    private String payloadModelKeys;

    @Column(name = "sign" , nullable = false, length = 64)
    @Comment("The md5 of the wayline file")
    @TableField("sign")
    private String sign;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @Comment("Which workspace the current wayline belongs to")
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "favorited" , nullable = false)
    @ColumnDefault("0")
    @Comment("Whether the file is favorited or not")
    @TableField("favorited")
    private Boolean favorited;

    @Column(name = "template_types" , nullable = false, length = 32)
    @Comment("wayline file template type. 0: waypoint;")
    @TableField("template_types")
    private String templateTypes;

    @Column(name = "object_key" , nullable = false, length = 200)
    @Comment("The key of the file in the bucket")
    @TableField("object_key")
    private String objectKey;

    @Column(name = "user_name" , nullable = false, length = 64)
    @Comment("The name of the creator")
    @TableField("user_name")
    private String username;

    @Column(name = "dock_sn", length = 32)
    @TableField("dock_sn")
    private String dockSn;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

}
