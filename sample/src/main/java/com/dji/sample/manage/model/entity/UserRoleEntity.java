package com.dji.sample.manage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manage_user_role")
@Entity
@Table(name = "manage_user_role")
public class UserRoleEntity implements Serializable {
    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "user_id" , nullable = false, length = 64)
    @Comment("uuid")
    @TableField(value = "user_id")
    private String userId;

    @Column(name = "role_code" , nullable = false, length = 20)
    @TableField(value = "role_code")
    private String roleCode;
}
