package com.dji.sample.map.model.entity;

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
 * @version 0.2
 * @date 2021/11/29
 */
@TableName("map_group_element")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "map_group_element")
public class GroupElementEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "group_id" , nullable = false, length = 64)
    @Comment("The group_id in the map_group table.")
    @TableField("group_id")
    private String groupId;

    @Column(name = "element_id" , nullable = false, length = 64, unique = true)
    @Comment("uuid")
    @TableField("element_id")
    private String elementId;

    @Column(name = "element_name" , nullable = false, length = 64)
    @Comment("The name of the element.")
    @TableField("element_name")
    private String elementName;

    @Column(name = "display" , nullable = false)
    @ColumnDefault("1")
    @Comment("The name of the element.")
    @TableField("display")
    private Integer display;

    @Column(name = "element_type" , nullable = false)
    @Comment("element type. 0: point; 1: line; 2: polygon.")
    @TableField("element_type")
    private Integer elementType;

    @Column(name = "username" , nullable = false, length = 64)
    @Comment("The name of the creator.")
    @TableField("username")
    private String username;

    @Column(name = "color" , nullable = false, length = 32)
    @Comment("The color of the element. Hexadecimal.")
    @TableField("color")
    private String color;

    @Column(name = "clamp_to_ground" , nullable = false, length = 1)
    @Comment("Whether it is on the ground. 1: true; 0: false.")
    @TableField("clamp_to_ground")
    private Boolean clampToGround;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

    @Column(name = "related_system" , length = 255)
    @Comment("related system name or api url")
    @TableField("related_system")
    private String relatedSystem;

    @Column(name = "related_id" , length = 64)
    @Comment("related system object id")
    @TableField("related_id")
    private String relatedId;
}
