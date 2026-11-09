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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manage_user")
@Entity
@Table(name = "manage_user")
public class UserEntity implements Serializable {
    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "user_id" , nullable = false, length = 64, unique = true)
    @Comment("uuid")
    @TableField(value = "user_id")
    private String userId;

    @Column(name = "username" , nullable = false, length = 32, unique = true)
    @TableField(value = "username")
    private String username;

    @Column(name = "password" , nullable = false, length = 64)
    @TableField(value = "password")
    private String password;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @Comment("uuid")
    @TableField(value = "workspace_id")
    private String workspaceId;

    @Column(name = "user_type" , nullable = false)
    @Comment("The type of account. Different sides need to be logged in with the corresponding type of account. 1: web; 2: pilot.")
    @TableField(value = "user_type")
    private Integer userType;

    @Column(name = "remark" , length = 64)
    @TableField(value = "remark")
    private String remark;

    @Column(name = "status" , nullable = false)
    @ColumnDefault("0")
    @Comment("状态（1-正常, 0-未启用, -1-禁用）")
    @TableField(value = "status")
    private Integer status;

    @Column(name = "mqtt_username", length = 32)
    @Comment("The account name used by the current account when logging into the emqx server.")
    @TableField(value = "mqtt_username")
    private String mqttUsername;

    @Column(name = "mqtt_password", length = 32)
    @Comment("The account password used by the current account when logging into the emqx server.")
    @TableField(value = "mqtt_password")
    private String mqttPassword;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

    @Column(name = "updator" , length = 32)
    @TableField(value = "updator")
    private String updator;

    public UserEntity(String userId, String username, String password, String workspaceId, Integer userType, Integer status) {
        this.status = status;
        this.userType = userType;
        this.workspaceId = workspaceId;
        this.password = password;
        this.username = username;
        this.userId = userId;
    }
}
