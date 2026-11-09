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
@TableName("manage_role")
@Entity
@Table(name = "manage_role")
public class RoleEntity implements Serializable {
    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "role_code" , nullable = false, length = 20, unique = true)
    @Comment("ROLE_XXXX")
    @TableField(value = "role_code")
    private String roleCode;

    @Column(name = "role_name" , nullable = false, length = 32)
    @TableField(value = "role_name")
    private String roleName;

    @Column(name = "child_role" , length = 20)
    @TableField(value = "child_role")
    private String childRole;

    @Column(name = "level" , nullable = false)
    @TableField(value = "level")
    private Integer level;
}
