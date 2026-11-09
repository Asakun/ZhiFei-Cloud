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
@TableName(value = "api_key")
@Entity
@Table(name = "api_key")
public class ApiKeyEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "app" , nullable = false, length = 20)
    @TableField(value = "app")
    private String app;

    @Column(name = "api_key" , nullable = false, length = 64, unique = true)
    @TableField(value = "api_key")
    private String apiKey;

    @Column(name = "uid" , nullable = false, length = 64)
    @TableField(value = "uid")
    private String uid;

    @Column(name = "expire_time")
    @Comment("过期时间（null=永不过期）")
    @TableField(value = "expire_time")
    private Long expireTime;

    @Column(name = "enable" , nullable = false)
    @ColumnDefault("1")
    @Comment("1: enable; 0: disable")
    @TableField(value = "enable")
    private Boolean enable;

    @Column(name = "is_del" , nullable = false)
    @ColumnDefault("0")
    @Comment("1: del; 0: not del")
    @TableField(value = "is_del")
    private Boolean isDel;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}
